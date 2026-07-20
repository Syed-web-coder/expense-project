# expense-mcp-server/src/expense_mcp_server/observability.py
"""tool.invoke.start / tool.invoke.end structlog lines around every tool
handler. @traceable (already on each handler) lands the LangSmith span;
this is the local-side telemetry the Grafana dashboard aggregates.

cost_usd_minor is a STUB: real per-call cost isn't wired yet (it belongs
to the llm-proxy's usage metering, which this MCP shim doesn't own).
_COST_TABLE below is a fixed placeholder estimate per tool so the field
is present and typed correctly (integer minor units, per the W6 D4 money
discipline), not a fabricated real number. Replace once llm-proxy exposes
actual per-call cost.
"""
from __future__ import annotations

import time
from collections.abc import Awaitable, Callable
from functools import wraps
from typing import Any, TypeVar

import structlog
from mcp import McpError
from pydantic import BaseModel

from expense_mcp_server.auth import current_tenant_id

log = structlog.get_logger("expense-mcp-server")

# Fixed per-tool placeholder cost estimate, in integer USD minor units (cents).
_COST_TABLE: dict[str, int] = {
    "orders.get_order": 0,
    "orders.create_refund": 0,
    "llm.chat": 50,
    "rag.retrieve_and_generate": 120,
}

F = TypeVar("F", bound=Callable[..., Awaitable[Any]])


def _resolve_tenant_id(args: object) -> str | None:
    # Prefer the tool's own explicit tenant_id field (required on every
    # Args model); fall back to the SSE auth middleware's ContextVar.
    tenant_id = getattr(args, "tenant_id", None)
    if tenant_id is not None:
        return str(tenant_id)
    return current_tenant_id.get()


def observe(tool_name: str) -> Callable[[F], F]:
    """Wrap a tool handler with start/end structlog lines."""

    def decorator(fn: F) -> F:
        @wraps(fn)
        async def wrapper(args: BaseModel) -> dict[str, object]:
            tenant_id = _resolve_tenant_id(args)
            start = time.perf_counter()
            log.info("tool.invoke.start", tool=tool_name, tenant_id=tenant_id)
            try:
                result = await fn(args)
            except McpError as exc:
                duration_ms = round((time.perf_counter() - start) * 1000, 2)
                log.info(
                    "tool.invoke.end",
                    tool=tool_name,
                    tenant_id=tenant_id,
                    duration_ms=duration_ms,
                    cost_usd_minor=0,
                    mcp_error_code=exc.error.code,
                )
                raise
            duration_ms = round((time.perf_counter() - start) * 1000, 2)
            log.info(
                "tool.invoke.end",
                tool=tool_name,
                tenant_id=tenant_id,
                duration_ms=duration_ms,
                cost_usd_minor=_COST_TABLE.get(tool_name, 0),
            )
            return dict(result)

        return wrapper  # type: ignore[return-value]

    return decorator
