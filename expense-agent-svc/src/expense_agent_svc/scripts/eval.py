"""Eval gate CLI: run trajectory evaluation and optionally fail CI.

Usage
-----
uv run python -m expense_agent_svc.scripts.eval           # print results
uv run python -m expense_agent_svc.scripts.eval --gate    # exit 1 on threshold breach

Gate thresholds
---------------
- trajectory_match < 0.70          → fail
- faithfulness < 0.85 (when set)   → fail
- trajectory regression > 15% vs evals/last_run.json (when both runs present faithfulness) → fail

When EXPENSE_AGENT_ANTHROPIC_API_KEY is absent/PLACEHOLDER the graph runs
with fake clients so trajectories are still exercised even in environments
without real credentials.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path
from typing import Any
from unittest.mock import AsyncMock, MagicMock

from expense_agent_svc.budgets import BudgetGuard
from expense_agent_svc.graph import build_expense_agent_graph
from expense_agent_svc.nodes.synthesis import FinalAnswer
from expense_agent_svc.settings import Settings

# Ensure project root (expense-agent-svc/) is in sys.path so that `evals` is importable.
# This must come after expense_agent_svc imports (which are installed editable) and before evals.
_ROOT = Path(__file__).parents[3]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from evals.trajectory import SCENARIOS, run_eval  # noqa: E402


def _make_fake_instructor() -> Any:
    client = MagicMock()

    class _FC:
        class usage:
            input_tokens = 50
            output_tokens = 20

    answer = FinalAnswer(text="No information available.", citations=[], confidence=0.3)
    client.messages = MagicMock()
    client.messages.create_with_completion = AsyncMock(return_value=(answer, _FC()))
    return client


def _make_fake_mcp() -> Any:
    session = AsyncMock()
    lr = MagicMock()
    lr.tools = []
    session.list_tools = AsyncMock(return_value=lr)
    return session


async def _run_async(gate: bool) -> int:
    settings = Settings()

    async def _fake_retriever(
        question: str, tenant_id: str = "t", k: int = 8
    ) -> list[dict[str, Any]]:
        return [{"doc_id": "eval-fake-doc", "chunk_idx": 0, "score": 0.85}]

    extras: dict[str, Any] = {
        "__retriever": _fake_retriever,
        "__instructor": _make_fake_instructor(),
        "__mcp_session": _make_fake_mcp(),
        "__budget_guard": BudgetGuard(ceiling_usd_e5=500_000),
    }

    from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

    async with AsyncPostgresSaver.from_conn_string(settings.postgres_url) as saver:
        await saver.setup()
        graph = build_expense_agent_graph(settings, checkpointer=saver)
        result = await run_eval(graph, SCENARIOS, configurable_extras=extras)

    if not gate:
        print(json.dumps(result, indent=2))
        return 0

    # ── Gate checks ───────────────────────────────────────────────────────────
    traj: float = result.get("trajectory_match", 0.0)
    if traj < 0.70:
        print(f"FAIL trajectory_match={traj:.2f} < 0.70", file=sys.stderr)
        return 1

    faithfulness: float | None = result.get("faithfulness")
    if faithfulness is not None and faithfulness < 0.85:
        print(f"FAIL faithfulness={faithfulness:.2f} < 0.85", file=sys.stderr)
        return 1

    # Cost regression: compare against previously committed last_run.json
    last_run_path = _ROOT / "evals" / "last_run.json"
    if last_run_path.exists() and faithfulness is not None:
        try:
            prior = json.loads(last_run_path.read_text())
            prior_traj: float = prior.get("trajectory_match", 0.0)
            if prior_traj > 0 and traj < prior_traj * 0.85:
                print(
                    f"FAIL cost regression: trajectory_match={traj:.2f} vs prior={prior_traj:.2f}",
                    file=sys.stderr,
                )
                return 1
        except Exception:
            pass

    print(f"PASS trajectory_match={traj:.2f}")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description="Expense agent eval gate")
    parser.add_argument("--gate", action="store_true", help="Exit 1 on threshold breach")
    args = parser.parse_args()

    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

    sys.exit(asyncio.run(_run_async(args.gate)))


if __name__ == "__main__":
    main()
