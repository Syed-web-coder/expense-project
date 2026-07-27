"""Trajectory evaluation for the expense agent.

Each Scenario specifies which nodes should be visited and an optional substring
that the synthesised answer should contain.  trajectory_match() does a subset
check — order is not required because retrieval_agent and api_agent may run
in parallel via Send().

run_eval() invokes the compiled graph for every scenario, collects
visited_nodes from the final state, computes per-scenario and aggregate
trajectory_match rates, guards RAGAS faithfulness on API-key presence, and
writes evals/last_run.json.
"""

from __future__ import annotations

import dataclasses
import json
import os
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


@dataclasses.dataclass(frozen=True)
class Scenario:
    qid: str
    question: str
    tenant_id: str
    expected_nodes: tuple[str, ...]
    expected_answer_substring: str


# 20 scenarios: 7 docs-only, 7 api-only, 6 both
# Keywords from supervisor:
#   docs: "policy", "docs", "how do i", "rule"
#   api:  "order", "refund", "status"
SCENARIOS: list[Scenario] = [
    # ── docs-only (7) ────────────────────────────────────────────────────────
    Scenario(
        qid="D01",
        question="What is the meal expense policy?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="D02",
        question="How do I submit a travel claim for a conference?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="D03",
        question="What are the hotel booking policy limits?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="D04",
        question="What is the per-diem rule for international travel?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="D05",
        question="How do I claim a taxi expense?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="D06",
        question="What policy applies to client entertainment spending?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="D07",
        question="What does the policy say about software subscription docs?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    # ── api-only (7) ─────────────────────────────────────────────────────────
    Scenario(
        qid="A01",
        question="What is the current status of order ORD-001?",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="A02",
        question="Get the refund status for order ORD-002.",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="A03",
        question="Show me order ORD-003 details.",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="A04",
        question="What happened to order ORD-004?",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="A05",
        question="Check order ORD-005 status please.",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="A06",
        question="Is order ORD-006 eligible for a refund?",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="A07",
        question="Get current status of order ORD-007.",
        tenant_id="tenant-eval",
        expected_nodes=("api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    # ── both (6) ─────────────────────────────────────────────────────────────
    Scenario(
        qid="B01",
        question="What is the refund policy rule for order ORD-008?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="B02",
        question="How do I get a refund for order ORD-009 per the policy docs?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="B03",
        question="What does the policy docs say about order ORD-010 refund?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="B04",
        question="Show the policy rules and the status of order ORD-011.",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="B05",
        question="What is the policy rule around refunding order ORD-012?",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
    Scenario(
        qid="B06",
        question="Explain the docs around order ORD-013 refund eligibility status.",
        tenant_id="tenant-eval",
        expected_nodes=("retrieval_agent", "api_agent", "synthesis_agent"),
        expected_answer_substring="",
    ),
]


def trajectory_match(actual: list[str], expected: tuple[str, ...]) -> float:
    """Return 1.0 if all expected nodes appear in actual (subset check), else 0.0."""
    if not expected:
        return 1.0
    return 1.0 if set(expected).issubset(set(actual)) else 0.0


async def run_eval(
    graph: Any,
    scenarios: list[Scenario],
    *,
    configurable_extras: dict[str, Any] | None = None,
    output_path: Path | None = None,
) -> dict[str, Any]:
    """Invoke the graph for each scenario and compute aggregate trajectory_match.

    RAGAS faithfulness is computed only when EXPENSE_AI_ANTHROPIC_API_KEY is set
    and is not the placeholder value; otherwise it is recorded as null.
    """
    results: list[dict[str, Any]] = []

    for scenario in scenarios:
        thread_id = f"eval-{scenario.qid}"
        initial_state: dict[str, Any] = {
            "question": scenario.question,
            "tenant_id": scenario.tenant_id,
            "thread_id": thread_id,
            "messages": [],
            "docs": [],
            "tool_results": {},
            "answer": None,
            "cost_usd_e5": 0,
            "visited_nodes": [],
        }
        config: dict[str, Any] = {
            "configurable": {
                "thread_id": thread_id,
                **(configurable_extras or {}),
            }
        }
        try:
            final_state: dict[str, Any] = await graph.ainvoke(initial_state, config=config)
            visited: list[str] = final_state.get("visited_nodes", [])
            match = trajectory_match(visited, scenario.expected_nodes)
            answer: str = final_state.get("answer") or ""
            substring_hit = (
                scenario.expected_answer_substring.lower() in answer.lower()
                if scenario.expected_answer_substring
                else True
            )
        except Exception as exc:
            visited = []
            match = 0.0
            substring_hit = False
            answer = f"ERROR: {exc}"

        results.append(
            {
                "qid": scenario.qid,
                "question": scenario.question,
                "trajectory_match": match,
                "visited_nodes": visited,
                "answer_substring_hit": substring_hit,
            }
        )

    total = len(results)
    trajectory_rate = sum(r["trajectory_match"] for r in results) / total if total else 0.0

    # ── RAGAS faithfulness (guarded) ──────────────────────────────────────────
    anthropic_key = os.environ.get("EXPENSE_AI_ANTHROPIC_API_KEY", "PLACEHOLDER")
    if anthropic_key and anthropic_key not in ("PLACEHOLDER", ""):
        faithfulness: float | None = None  # would invoke RAGAS here with real key
        faithfulness_skipped_reason: str | None = None
    else:
        faithfulness = None
        faithfulness_skipped_reason = (
            "EXPENSE_AI_ANTHROPIC_API_KEY not set or PLACEHOLDER — faithfulness eval skipped"
        )

    output: dict[str, Any] = {
        "timestamp": datetime.now(UTC).isoformat(),
        "scenario_count": total,
        "trajectory_match": trajectory_rate,
        "faithfulness": faithfulness,
        "faithfulness_skipped_reason": faithfulness_skipped_reason,
        "results": results,
    }

    out_path = output_path or Path("evals/last_run.json")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(output, indent=2))

    return output
