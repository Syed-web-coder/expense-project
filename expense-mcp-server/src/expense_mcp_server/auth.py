# expense-mcp-server/src/expense_mcp_server/auth.py
"""SSE-only bearer JWT validation: a defensive duplicate of the W3 D1
Java-side check, done locally against the same JWKS. Extracts the
tenant_id claim into a ContextVar so logging (and, in future, tool
handlers) can read it without an explicit parameter.

NOT used on the stdio transport: Claude Desktop launches that as a
trusted local subprocess and passes the JWT via EXPENSE_MCP_BEARER_JWT
directly, so there's no handshake to authenticate.
"""
from __future__ import annotations

import contextvars

import jwt
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

current_tenant_id: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "current_tenant_id", default=None
)


def _unauthorized(message: str) -> JSONResponse:
    # 4030 isn't a real HTTP status; it's this project's McpError code
    # space. We surface it in the JSON body and use HTTP 401 for the
    # actual transport-level status.
    return JSONResponse(
        {"error": {"code": 4030, "message": message}},
        status_code=401,
    )


class JWTAuthMiddleware:
    """Validates the bearer JWT on every request to the SSE app. On
    success, sets current_tenant_id for the duration of the request.
    """

    def __init__(self, app: ASGIApp, jwks_url: str, audience: str) -> None:
        self.app = app
        self._jwk_client = jwt.PyJWKClient(jwks_url)
        self._audience = audience

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        request = Request(scope, receive=receive)
        auth_header = request.headers.get("authorization", "")
        if not auth_header.lower().startswith("bearer "):
            response = _unauthorized("missing or malformed Authorization header")
            await response(scope, receive, send)
            return

        token = auth_header.split(" ", 1)[1]
        try:
            signing_key = self._jwk_client.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                audience=self._audience,
            )
        except jwt.InvalidTokenError as exc:
            response = _unauthorized(f"invalid token: {exc}")
            await response(scope, receive, send)
            return

        tenant_id = claims.get("tenant_id")
        if not tenant_id:
            response = _unauthorized("token missing tenant_id claim")
            await response(scope, receive, send)
            return

        token_ctx = current_tenant_id.set(tenant_id)
        try:
            await self.app(scope, receive, send)
        finally:
            current_tenant_id.reset(token_ctx)


def wrap_with_auth(app: Starlette, jwks_url: str, audience: str) -> ASGIApp:
    return JWTAuthMiddleware(app, jwks_url=jwks_url, audience=audience)
