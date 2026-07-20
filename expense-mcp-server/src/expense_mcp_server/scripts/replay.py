# expense-mcp-server/src/expense_mcp_server/scripts/replay.py
"""Deterministic, offline replay of committed fixtures through the real
MCP dispatch path -- not by calling the Python handler functions
directly. Each fixture provides a tool name, its args, and a canned
upstream HTTP response (mocked via respx); the script drives everything
through mcp.shared.memory.create_connected_server_and_client_session so
the same request-context/lifespan machinery a real client would trigger
is genuinely exercised (this is why bare mcp.call_tool() doesn't work
here -- see the comment on `_mcp_server` below).

Only orders.get_order / orders.create_refund / llm.chat are covered.
rag.retrieve_and_generate is excluded -- see tests/fixtures/README.md
for why (no live Postgres/Redis/Anthropic anywhere yet).

Writes .replay/latest.json with p50/p95/p99 latency per tool. Exits
non-zero if any fixture's tool call comes back as an MCP-level error.
"""
from __future__ import annotations

import asyncio
import json
import sys
import time
from pathlib import Path
from typing import Any

import httpx
import respx
from mcp.shared.memory import create_connected_server_and_client_session

FIXTURES_DIR = Path(__file__).resolve().parents[3] / "tests" / "fixtures"
OUTPUT_DIR = Path(__file__).resolve().parents[3] / ".replay"


def _load_fixtures() -> list[dict[str, Any]]:
    fixtures = []
    for path in sorted(FIXTURES_DIR.glob("*.json")):
        with open(path) as f:
            fixtures.append(json.load(f))
    return fixtures


def _percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = min(int(len(ordered) * pct), len(ordered) - 1)
    return round(ordered[idx], 2)


async def _run_fixture(client: Any, fixture: dict[str, Any]) -> tuple[str, float, bool]:
    mock = fixture["mock"]
    with respx.mock:
        respx.request(mock["method"], mock["url"]).mock(
            return_value=httpx.Response(mock["status_code"], json=mock["json"])
        )
        start = time.perf_counter()
        result = await client.call_tool(fixture["tool"], {"args": fixture["args"]})
        duration_ms = (time.perf_counter() - start) * 1000
    return fixture["tool"], duration_ms, bool(result.isError)


async def main() -> int:
    # Importing registers every tool's @mcp.tool decorator on the shared
    # module-level `mcp` instance before we build a session against it.
    from expense_mcp_server.app import mcp
    from expense_mcp_server.tools import _resources, llm, orders  # noqa: F401

    fixtures = _load_fixtures()
    if not fixtures:
        print(f"No fixtures found under {FIXTURES_DIR}", file=sys.stderr)
        return 1

    durations_by_tool: dict[str, list[float]] = {}
    had_error = False

    # NOTE: mcp._mcp_server reaches into a private attribute; FastMCP in
    # this SDK version doesn't expose a public accessor for the
    # underlying low-level Server that create_connected_server_and_client_session
    # expects.
    async with create_connected_server_and_client_session(mcp._mcp_server) as client:
        for fixture in fixtures:
            tool, duration_ms, is_error = await _run_fixture(client, fixture)
            durations_by_tool.setdefault(tool, []).append(duration_ms)
            status = "ERROR" if is_error else "ok"
            print(f"[{status}] {tool} ({duration_ms:.2f}ms)")
            if is_error:
                had_error = True

    report = {
        tool: {
            "n": len(durations),
            "p50_ms": _percentile(durations, 0.50),
            "p95_ms": _percentile(durations, 0.95),
            "p99_ms": _percentile(durations, 0.99),
        }
        for tool, durations in durations_by_tool.items()
    }

    OUTPUT_DIR.mkdir(exist_ok=True)
    output_path = OUTPUT_DIR / "latest.json"
    with open(output_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"\nWrote {output_path}")

    if had_error:
        print("\nOne or more fixtures returned an MCP-level error.", file=sys.stderr)
        return 1
    return 0


def cli() -> None:
    sys.exit(asyncio.run(main()))


if __name__ == "__main__":
    cli()
