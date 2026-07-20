"""AgentState for the expense LangGraph pipeline.

Reducer semantics
-----------------
- messages  : add_messages — appends new messages, deduplicates by id (safe for
              parallel fan-out because each branch only appends its own messages).
- docs      : operator.add — list concatenation; retrieval_agent and api_agent may
              both contribute docs independently, so their lists are merged.
- tool_results : _merge_tool_results — dict union, right-side wins per key; each
              parallel branch owns distinct tool-call ids so there is no real
              conflict, but the reducer makes it explicit.
- cost_usd_e5 : operator.add — integer sum across all nodes; using integer (never
              float) throughout to avoid floating-point drift in money arithmetic.
- answer    : plain overwrite; only synthesis_agent writes this field, so no
              reducer is needed.

Injectable slots (NotRequired, single-underscore prefix avoids name-mangling)
are populated by tests or the app lifespan to avoid real network calls.
"""

from __future__ import annotations

import operator
from typing import Annotated, Any, NotRequired, TypedDict

from langgraph.graph.message import add_messages


def _merge_tool_results(left: dict[str, Any], right: dict[str, Any]) -> dict[str, Any]:
    """Last-write-wins per key merge for concurrent tool results."""
    return {**left, **right}


class _AgentStateCore(TypedDict):
    question: str
    tenant_id: str
    thread_id: str
    messages: Annotated[list[Any], add_messages]
    docs: Annotated[list[dict[str, Any]], operator.add]
    tool_results: Annotated[dict[str, Any], _merge_tool_results]
    answer: str | None
    cost_usd_e5: Annotated[int, operator.add]
    visited_nodes: Annotated[list[str], operator.add]


# Injectable fields use functional TypedDict to allow double-underscore keys
# without Python's name-mangling that affects class-based definitions.
_AgentStateInject = TypedDict(
    "_AgentStateInject",
    {
        "__retriever": NotRequired[Any],
        "__pg_dsn": NotRequired[str],
        "__mcp_session": NotRequired[Any],
        "__budget_guard": NotRequired[Any],
        "__anthropic": NotRequired[Any],
        "__instructor": NotRequired[Any],
    },
    total=False,
)


class AgentState(_AgentStateCore, _AgentStateInject):
    """Full agent state combining core fields and injectable test helpers."""
