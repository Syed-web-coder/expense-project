"""Integration test: full graph invocation with fakes — no real API keys needed.

Uses Testcontainers Postgres for the checkpointer.  Verifies:
- Both branches (retrieval + api) run for a mixed-keyword question.
- Final state has docs, tool_results, and a valid FinalAnswer JSON.
- cost_usd_e5 accumulates across branches.
- Two invocations with the same thread_id produce >=2 checkpoint rows.

Injectable dependencies (retriever, MCP session, Anthropic client, instructor)
are passed via config['configurable'] so they are never serialized into Postgres
checkpoints (LangGraph only serializes state, not config).
"""

from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock, MagicMock

import psycopg
import pytest

from expense_agent_svc.budgets import BudgetGuard
from expense_agent_svc.fakes import make_fake_anthropic, make_fake_instructor
from expense_agent_svc.graph import build_expense_agent_graph
from expense_agent_svc.nodes.synthesis import FinalAnswer
from expense_agent_svc.settings import Settings

# ---------------------------------------------------------------------------
# Fakes
# ---------------------------------------------------------------------------


async def _fake_retriever(
    question: str, tenant_id: str = "tenant-a", k: int = 8
) -> list[dict[str, Any]]:
    return [{"doc_id": "doc-1", "chunk_idx": 0, "score": 0.92}]


def _make_fake_mcp_session() -> Any:
    session = AsyncMock()

    tool = MagicMock()
    tool.name = "orders.get_order"
    tool.description = "Fetch an order by ID"
    tool.inputSchema = {
        "type": "object",
        "properties": {"order_id": {"type": "string"}},
    }
    list_tools_result = MagicMock()
    list_tools_result.tools = [tool]
    session.list_tools = AsyncMock(return_value=list_tools_result)

    call_tool_result = MagicMock()
    call_tool_result.content = [{"type": "text", "text": '{"status": "shipped"}'}]
    session.call_tool = AsyncMock(return_value=call_tool_result)
    return session


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.fixture()
def settings(pg_dsn: str) -> Settings:
    return Settings(postgres_url=pg_dsn)


@pytest.fixture()
def compiled_graph(settings: Settings, checkpointer: Any) -> Any:
    return build_expense_agent_graph(settings, checkpointer=checkpointer)


@pytest.mark.asyncio
async def test_full_graph_invocation(compiled_graph: Any, pg_dsn: str) -> None:
    guard = BudgetGuard(ceiling_usd_e5=100_000)

    # Injectable deps go in config['configurable'] — NOT serialized by LangGraph.
    # State contains only serializable primitive fields.
    initial_state: dict[str, Any] = {
        "question": "what is the refund policy rule for order o123?",
        "tenant_id": "tenant-a",
        "thread_id": "int-test-thread-1",
        "messages": [],
        "docs": [],
        "tool_results": {},
        "answer": None,
        "cost_usd_e5": 0,
        "visited_nodes": [],
    }
    config: dict[str, Any] = {
        "configurable": {
            "thread_id": "int-test-thread-1",
            "__retriever": _fake_retriever,
            "__mcp_session": _make_fake_mcp_session(),
            "__anthropic": make_fake_anthropic(),
            "__instructor": make_fake_instructor(),
            "__budget_guard": guard,
        }
    }

    final_state = await compiled_graph.ainvoke(initial_state, config=config)

    # docs populated by retrieval_agent
    assert len(final_state["docs"]) >= 1

    # tool_results populated by api_agent (had a tool_use round-trip)
    assert len(final_state["tool_results"]) >= 1

    # answer is valid FinalAnswer JSON
    assert final_state["answer"] is not None
    assert final_state["answer"] != "[deadline exceeded]"
    answer_obj = FinalAnswer.model_validate_json(final_state["answer"])
    assert answer_obj.confidence > 0.0

    # cost accumulated from both branches + synthesis
    assert final_state["cost_usd_e5"] > 0

    # Second invocation same thread — checkpoint rows should be >= 2
    await compiled_graph.ainvoke(initial_state, config=config)

    with psycopg.connect(pg_dsn) as conn:
        row = conn.execute(
            "SELECT COUNT(*) FROM checkpoints WHERE thread_id = %s",
            ("int-test-thread-1",),
        ).fetchone()
        assert row is not None
        assert int(row[0]) >= 2
