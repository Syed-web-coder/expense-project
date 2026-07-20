# expense-mcp-server/src/expense_mcp_server/tools/_resources.py
"""Read-only expense://catalogue resource: a fallback for clients (the
W7 D5 agent) that want the tool surface + light corpus stats without a
full tools/list round trip.
"""
from __future__ import annotations

from expense_mcp_server.app import mcp

_TOOL_CATALOGUE = [
    "orders.get_order",
    "orders.create_refund",
    "llm.chat",
    "rag.retrieve_and_generate",
]

# Short, static corpus stats; real numbers would come from the W7 D3
# ingestion DAG's latest run, but a fixed snapshot is enough for clients
# doing a fallback capability check.
_CORPUS_STATS = {
    "size": 0,
    "tenants": ["tenant-a", "tenant-b", "tenant-c"],
}


@mcp.resource(uri="expense://catalogue", name="catalogue")
def catalogue() -> dict[str, object]:
    return {
        "tools": _TOOL_CATALOGUE,
        "corpus": _CORPUS_STATS,
    }
