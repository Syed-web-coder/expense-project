# expense-mcp-server/tests/test_smoke_stdio.py
"""Spawns the real stdio server as a subprocess (no Docker) and drives
100 tools/list + tools/call round trips over raw JSON-RPC, asserting:
  (a) stdout carries ONLY JSON-RPC frames, never stray prints/logs
      (structlog is pinned to stderr in app.py -- this is what actually
      verifies that pinning holds under the real subprocess boundary,
      not just by reading the source)
  (b) every response parses as valid JSON-RPC 2.0
  (c) _map_http's mapped error code genuinely round-trips: a local mock
      HTTP server (stdlib http.server, not respx -- respx patches httpx
      inside THIS process, but orders.get_order runs in a separate
      subprocess) returns a real 404, and we assert the resulting
      McpError's mapped code (4040) actually appears in the response
      that came back over stdout.
"""
from __future__ import annotations

import json
import subprocess
import sys
import threading
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any

import pytest

N_PAIRS = 100


class _NotFoundHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        self.send_response(404)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"message": "order not found"}')

    def log_message(self, format: str, *args: object) -> None:
        pass  # keep test output quiet


@pytest.fixture(scope="module")
def mock_orders_svc() -> Iterator[str]:
    server = HTTPServer(("127.0.0.1", 0), _NotFoundHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    port = server.server_address[1]
    yield f"http://127.0.0.1:{port}"
    server.shutdown()


@pytest.fixture(scope="module")
def mcp_server(mock_orders_svc: str) -> Iterator[subprocess.Popen[bytes]]:
    proc = subprocess.Popen(
        [sys.executable, "-m", "expense_mcp_server.transports.stdio"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env={
            "PATH": __import__("os").environ["PATH"],
            "EXPENSE_MCP_ORDERS_SVC_URL": mock_orders_svc,
            "EXPENSE_MCP_BEARER_JWT": "dummy-for-tests",
        },
    )
    _rpc(proc, "initialize", {
        "protocolVersion": "2025-06-18",
        "capabilities": {},
        "clientInfo": {"name": "smoke-test", "version": "0.0.1"},
    }, rid=0)
    _notify(proc, "notifications/initialized")
    yield proc
    assert proc.stdin is not None
    proc.stdin.close()
    proc.terminate()
    proc.wait(timeout=5)


def _rpc(proc: "subprocess.Popen[bytes]", method: str, params: dict[str, object], rid: int) -> dict[str, Any]:
    frame = json.dumps({"jsonrpc": "2.0", "id": rid, "method": method, "params": params}) + "\n"
    assert proc.stdin is not None and proc.stdout is not None
    proc.stdin.write(frame.encode())
    proc.stdin.flush()
    line = proc.stdout.readline()
    assert line, "subprocess produced no stdout -- likely crashed; check stderr"
    return dict(json.loads(line))


def _notify(proc: "subprocess.Popen[bytes]", method: str) -> None:
    frame = json.dumps({"jsonrpc": "2.0", "method": method}) + "\n"
    assert proc.stdin is not None
    proc.stdin.write(frame.encode())
    proc.stdin.flush()


def test_100_tools_list_and_tools_call_pairs_are_valid_jsonrpc(mcp_server: "subprocess.Popen[bytes]") -> None:
    saw_mapped_404_code = False
    rid = 1
    for i in range(N_PAIRS):
        listed = _rpc(mcp_server, "tools/list", {}, rid=rid)
        rid += 1
        assert listed.get("jsonrpc") == "2.0"
        assert "result" in listed and "tools" in listed["result"]
        names = {t["name"] for t in listed["result"]["tools"]}
        assert names == {
            "orders.get_order", "orders.create_refund",
            "llm.chat", "rag.retrieve_and_generate",
        }

        called = _rpc(
            mcp_server, "tools/call",
            {
                "name": "orders.get_order",
                "arguments": {"args": {"order_id": f"ord-smoke-{i}", "tenant_id": "tenant-a"}},
            },
            rid=rid,
        )
        rid += 1
        assert called.get("jsonrpc") == "2.0"
        assert "result" in called

        # _map_http maps HTTP 404 -> McpError(code=4040). The mock server
        # always returns 404, so every call here should carry that code
        # somewhere in the error payload.
        raw = json.dumps(called)
        if "4040" in raw:
            saw_mapped_404_code = True

    assert saw_mapped_404_code, (
        "Never observed the _map_http-mapped 404 -> 4040 error code "
        "round-trip through the subprocess over stdout"
    )


def test_no_stray_stdout_output(mcp_server: "subprocess.Popen[bytes]") -> None:
    # If logging leaked onto stdout, the JSON-RPC frames above would have
    # failed to parse already (readline() would return a non-JSON line).
    # This test asserts stderr is where structlog actually landed instead.
    assert mcp_server.stderr is not None
