# expense-mcp-server/tests/test_auth.py
"""Unit tests for the SSE JWT auth middleware. No live JWKS server is
available (W3 D1's issuer isn't standing anywhere real yet -- same
situation as orders.get_order's HTTP call), so we generate a throwaway
RSA key pair, sign our own test tokens locally, and monkeypatch
PyJWKClient.get_signing_key_from_jwt to hand back that key's public half
directly. NOTE: PyJWKClient uses urllib under the hood, not httpx, so
respx (an httpx mocker) can't intercept it -- monkeypatching the method
is the correct approach here, not a network-layer mock. This verifies
the actual validation logic (good token accepted + tenant_id extracted,
bad audience rejected, missing claim rejected) rather than skipping auth
verification entirely.
"""
from __future__ import annotations

import time

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from jwt import PyJWK
from jwt.algorithms import RSAAlgorithm
from starlette.applications import Starlette
from starlette.responses import PlainTextResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from expense_mcp_server.auth import current_tenant_id, wrap_with_auth

JWKS_URL = "https://fake-issuer.internal/.well-known/jwks.json"
AUDIENCE = "expense-mcp-server"


@pytest.fixture()
def rsa_key() -> rsa.RSAPrivateKey:
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


@pytest.fixture(autouse=True)
def _mock_jwks_lookup(monkeypatch: pytest.MonkeyPatch, rsa_key: rsa.RSAPrivateKey) -> None:
    public_jwk_dict = RSAAlgorithm.to_jwk(rsa_key.public_key(), as_dict=True)
    public_jwk_dict["kid"] = "test-key-1"
    public_jwk_dict["use"] = "sig"
    public_jwk_dict["alg"] = "RS256"
    signing_key = PyJWK.from_dict(public_jwk_dict)

    monkeypatch.setattr(
        jwt.PyJWKClient,
        "get_signing_key_from_jwt",
        lambda self, token: signing_key,
    )


def _sign(rsa_key: rsa.RSAPrivateKey, claims: dict[str, object]) -> str:
    return jwt.encode(claims, rsa_key, algorithm="RS256", headers={"kid": "test-key-1"})


async def _echo_tenant(request: object) -> PlainTextResponse:
    return PlainTextResponse(current_tenant_id.get() or "")


def _make_app() -> Starlette:
    return Starlette(routes=[Route("/sse", _echo_tenant)])


def test_valid_token_sets_tenant_id_and_reaches_app(rsa_key: rsa.RSAPrivateKey) -> None:
    token = _sign(rsa_key, {
        "aud": AUDIENCE,
        "tenant_id": "tenant-a",
        "exp": int(time.time()) + 300,
    })

    app = wrap_with_auth(_make_app(), jwks_url=JWKS_URL, audience=AUDIENCE)
    client = TestClient(app)
    r = client.get("/sse", headers={"Authorization": f"Bearer {token}"})

    assert r.status_code == 200
    assert r.text == "tenant-a"


def test_missing_authorization_header_returns_4030() -> None:
    app = wrap_with_auth(_make_app(), jwks_url=JWKS_URL, audience=AUDIENCE)
    client = TestClient(app)
    r = client.get("/sse")

    assert r.status_code == 401
    assert r.json()["error"]["code"] == 4030


def test_wrong_audience_rejected(rsa_key: rsa.RSAPrivateKey) -> None:
    token = _sign(rsa_key, {
        "aud": "some-other-service",
        "tenant_id": "tenant-a",
        "exp": int(time.time()) + 300,
    })

    app = wrap_with_auth(_make_app(), jwks_url=JWKS_URL, audience=AUDIENCE)
    client = TestClient(app)
    r = client.get("/sse", headers={"Authorization": f"Bearer {token}"})

    assert r.status_code == 401
    assert r.json()["error"]["code"] == 4030


def test_missing_tenant_id_claim_rejected(rsa_key: rsa.RSAPrivateKey) -> None:
    token = _sign(rsa_key, {"aud": AUDIENCE, "exp": int(time.time()) + 300})

    app = wrap_with_auth(_make_app(), jwks_url=JWKS_URL, audience=AUDIENCE)
    client = TestClient(app)
    r = client.get("/sse", headers={"Authorization": f"Bearer {token}"})

    assert r.status_code == 401
    assert r.json()["error"]["code"] == 4030
