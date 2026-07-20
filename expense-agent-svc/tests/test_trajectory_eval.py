"""Verify run_eval exercises all 20 scenarios and meets the 0.70 trajectory gate.

Uses the real Postgres checkpointer (testcontainers) and fully-faked LLM
clients so no API keys are required.  Writes last_run.json to a tmp directory.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from expense_agent_svc.budgets import BudgetGuard
from expense_agent_svc.graph import build_expense_agent_graph
from expense_agent_svc.nodes.synthesis import FinalAnswer
from expense_agent_svc.settings import Settings


async def _fake_retriever(
    question: str, tenant_id: str = "tenant-eval", k: int = 8
) -> list[dict[str, Any]]:
    return [{"doc_id": "eval-doc-1", "chunk_idx": 0, "score": 0.90}]


def _make_fake_instructor() -> Any:
    client = MagicMock()

    class _FC:
        class usage:
            input_tokens = 60
            output_tokens = 25

    answer = FinalAnswer(
        text="The expense policy allows reimbursement up to $500.",
        citations=[],
        confidence=0.75,
    )
    client.messages = MagicMock()
    client.messages.create_with_completion = AsyncMock(return_value=(answer, _FC()))
    return client


def _make_fake_mcp() -> Any:
    session = AsyncMock()
    lr = MagicMock()
    lr.tools = []
    session.list_tools = AsyncMock(return_value=lr)
    return session


@pytest.fixture()
def settings(pg_dsn: str) -> Settings:
    return Settings(postgres_url=pg_dsn)


@pytest.mark.asyncio
async def test_run_eval_trajectory_gate(
    checkpointer: Any, settings: Settings, tmp_path: Path
) -> None:
    from evals.trajectory import SCENARIOS, run_eval

    graph = build_expense_agent_graph(settings, checkpointer=checkpointer)

    extras: dict[str, Any] = {
        "__retriever": _fake_retriever,
        "__instructor": _make_fake_instructor(),
        "__mcp_session": _make_fake_mcp(),
        "__budget_guard": BudgetGuard(ceiling_usd_e5=2_000_000),
    }

    output_path = tmp_path / "last_run.json"
    result = await run_eval(
        graph,
        SCENARIOS,
        configurable_extras=extras,
        output_path=output_path,
    )

    assert result["trajectory_match"] >= 0.70, (
        f"trajectory_match {result['trajectory_match']:.2f} below 0.70 gate"
    )
    assert output_path.exists(), "last_run.json was not written"

    import json

    written = json.loads(output_path.read_text())
    assert written["scenario_count"] == len(SCENARIOS)
    assert written["trajectory_match"] == result["trajectory_match"]
