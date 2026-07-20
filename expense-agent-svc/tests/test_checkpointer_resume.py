"""Crash-resume test: a NEW graph instance can read checkpoints written by the first.

Scenario:
  1. Invoke the expense agent graph and write a checkpoint (thread capstone-hitl-1).
  2. "Crash" — discard graph1 and saver1 entirely.
  3. Open a fresh AsyncPostgresSaver against the SAME Postgres.
  4. Assert aget_state() recovers the prior answer, proving restart-safety.
"""

from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from expense_agent_svc.budgets import BudgetGuard
from expense_agent_svc.graph import build_expense_agent_graph
from expense_agent_svc.nodes.synthesis import FinalAnswer
from expense_agent_svc.settings import Settings

_THREAD_ID = "capstone-hitl-1"


async def _fake_retriever(
    question: str, tenant_id: str = "tenant-a", k: int = 8
) -> list[dict[str, Any]]:
    return [{"doc_id": "doc-resume", "chunk_idx": 0, "score": 0.88}]


def _make_fake_instructor() -> Any:
    client = MagicMock()

    class _FakeCompletion:
        class usage:
            input_tokens = 100
            output_tokens = 40

    answer = FinalAnswer(
        text="Policy: all expenses under $500 are reimbursable.",
        citations=[],
        confidence=0.80,
    )
    client.messages = MagicMock()
    client.messages.create_with_completion = AsyncMock(return_value=(answer, _FakeCompletion()))
    return client


@pytest.mark.asyncio
async def test_crash_resume(checkpointer: Any, pg_dsn: str) -> None:
    settings = Settings(postgres_url=pg_dsn)

    # ── 1. First "process": invoke graph, write checkpoint ────────────────────
    graph1 = build_expense_agent_graph(settings, checkpointer=checkpointer)
    initial_state: dict[str, Any] = {
        "question": "what is the expense policy?",
        "tenant_id": "tenant-a",
        "thread_id": _THREAD_ID,
        "messages": [],
        "docs": [],
        "tool_results": {},
        "answer": None,
        "cost_usd_e5": 0,
        "visited_nodes": [],
    }
    config1: dict[str, Any] = {
        "configurable": {
            "thread_id": _THREAD_ID,
            "__retriever": _fake_retriever,
            "__instructor": _make_fake_instructor(),
            "__budget_guard": BudgetGuard(ceiling_usd_e5=100_000),
        }
    }
    final1 = await graph1.ainvoke(initial_state, config=config1)
    assert final1["answer"] is not None

    # ── 2. "Crash": open a brand-new saver against the same DB ───────────────
    from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

    async with AsyncPostgresSaver.from_conn_string(pg_dsn) as saver2:
        await saver2.setup()
        graph2 = build_expense_agent_graph(settings, checkpointer=saver2)

        # ── 3. Resume: aget_state must recover the prior checkpoint ──────────
        resume_config: dict[str, Any] = {"configurable": {"thread_id": _THREAD_ID}}
        snapshot = await graph2.aget_state(resume_config)

        assert snapshot is not None, "no checkpoint found after restart"
        assert snapshot.values.get("answer") is not None, "answer not persisted"
        assert "retrieval_agent" in snapshot.values.get("visited_nodes", [])
