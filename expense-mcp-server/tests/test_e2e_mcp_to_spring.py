# expense-mcp-server/tests/test_e2e_mcp_to_spring.py
"""E2E: Postgres + Spring Boot orders-svc + MCP server subprocess.

Asserts (a) tools/list returns all 4 tools, (b) tools/call for
orders.get_order returns the seeded synthetic order, (c)
orders.create_refund called twice with the same idempotency_key
returns the same refund_id.

Requires Docker (Testcontainers) and the uptimecrew/expense-orders:w3d1
image. This is the merge-to-main CI tier, not the PR tier -- it's
expected to need real infrastructure that isn't always available in
every dev environment (see the note at the bottom of this file for the
current state of that in this environment).
"""
from __future__ import annotations

import json
import subprocess
import sys
import time
from collections.abc import Iterator
from typing import Any
from uuid import uuid4

import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.postgres import PostgresContainer


@pytest.fixture(scope="session")
def postgres() -> Iterator[dict[str, str]]:
    with PostgresContainer("postgres:16-alpine") as pg:
        yield {"url": pg.get_connection_url()}


@pytest.fixture(scope="session")
def orders_svc(postgres: dict[str, str]) -> Iterator[dict[str, str]]:
    container = (
        DockerContainer("uptimecrew/expense-orders:w3d1")
        .with_env("SPRING_DATASOURCE_URL", postgres["url"])
        .with_exposed_ports(8080)
    )
    container.start()
    host = container.get_container_host_ip()
    port = container.get_exposed_port(8080)
    deadline = time.time() + 60
    import httpx

    while time.time() < deadline:
        try:
            r = httpx.get(f"http://{host}:{port}/actuator/health", timeout=2)
            if r.status_code == 200:
                break
        except httpx.HTTPError:
            pass
        time.sleep(1)
    else:
        pytest.fail("orders-svc did not become healthy within 60s")
    yield {"url": f"http://{host}:{port}"}
    container.stop()


@pytest.fixture(scope="session")
def mcp_server(orders_svc: dict[str, str]) -> Iterator["subprocess.Popen[bytes]"]:
    proc = subprocess.Popen(
        [sys.executable, "-m", "expense_mcp_server.transports.stdio"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env={
            "PATH": __import__("os").environ["PATH"],
            "EXPENSE_MCP_ORDERS_SVC_URL": orders_svc["url"],
            "EXPENSE_MCP_BEARER_JWT": "dummy-for-tests",
        },
    )
    _rpc(proc, "initialize", {
        "protocolVersion": "2025-06-18",
        "capabilities": {},
        "clientInfo": {"name": "e2e-test", "version": "0.0.1"},
    }, rid=0)
    assert proc.stdin is not None
    proc.stdin.write((json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized"}) + "\n").encode())
    proc.stdin.flush()
    yield proc
    proc.stdin.close()
    proc.terminate()
    proc.wait(timeout=5)


def _rpc(proc: "subprocess.Popen[bytes]", method: str, params: dict[str, Any], rid: int) -> dict[str, Any]:
    frame = json.dumps({"jsonrpc": "2.0", "id": rid, "method": method, "params": params}) + "\n"
    assert proc.stdin is not None and proc.stdout is not None
    proc.stdin.write(frame.encode())
    proc.stdin.flush()
    return dict(json.loads(proc.stdout.readline()))


def test_tools_list_returns_4_tools(mcp_server: "subprocess.Popen[bytes]") -> None:
    listed = _rpc(mcp_server, "tools/list", {}, rid=1)
    names = {t["name"] for t in listed["result"]["tools"]}
    assert names == {
        "orders.get_order", "orders.create_refund",
        "llm.chat", "rag.retrieve_and_generate",
    }


def test_get_order_returns_seeded_synthetic_order(mcp_server: "subprocess.Popen[bytes]") -> None:
    called = _rpc(
        mcp_server, "tools/call",
        {"name": "orders.get_order",
         "arguments": {"args": {"order_id": "ord-synth-9001", "tenant_id": "tenant-a"}}},
        rid=2,
    )
    payload = json.loads(called["result"]["content"][0]["text"])
    assert payload["order_id"] == "ord-synth-9001"
    assert payload["tenant_id"] == "tenant-a"


def test_create_refund_is_idempotent(mcp_server: "subprocess.Popen[bytes]") -> None:
    key = str(uuid4())
    args = {"order_id": "ord-synth-9001", "amount": "10.00",
            "reason": "duplicate", "tenant_id": "tenant-a",
            "idempotency_key": key}
    first = _rpc(mcp_server, "tools/call", {"name": "orders.create_refund", "arguments": {"args": args}}, rid=3)
    second = _rpc(mcp_server, "tools/call", {"name": "orders.create_refund", "arguments": {"args": args}}, rid=4)
    p1 = json.loads(first["result"]["content"][0]["text"])
    p2 = json.loads(second["result"]["content"][0]["text"])
    assert p1["refund_id"] == p2["refund_id"]
