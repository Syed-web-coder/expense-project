"""synthesis_agent — produces a structured FinalAnswer with citations.

Uses an instructor-patched AsyncAnthropic client resolved from
config['configurable']['__instructor'] (not serialized) or state['__instructor'].
Production wiring creates instructor.from_anthropic(AsyncAnthropic()) in app lifespan.
"""

from __future__ import annotations

import json
from typing import Any, Optional

from langchain_core.runnables import RunnableConfig
from langsmith import traceable
from pydantic import BaseModel, Field

from expense_agent_svc.budgets import BudgetGuard
from expense_agent_svc.nodes._deadline import deadline
from expense_agent_svc.nodes.retrieval import _get_dep
from expense_agent_svc.state import AgentState

_MODEL = "claude-sonnet-4-5"


class Citation(BaseModel, extra="forbid"):
    doc_id: str
    quote: str = Field(min_length=10, max_length=240)


class FinalAnswer(BaseModel, extra="forbid"):
    text: str = Field(min_length=1, max_length=2000)
    citations: list[Citation]
    confidence: float = Field(ge=0.0, le=1.0)


def _build_system_prompt(
    tenant_id: str,
    docs: list[dict[str, Any]],
    tool_results: dict[str, Any],
) -> str:
    docs_section = json.dumps(docs, indent=2) if docs else "(none)"
    tools_section = json.dumps(tool_results, indent=2) if tool_results else "(none)"
    return (
        f"You are the Expense Policy Assistant for tenant '{tenant_id}'.\n\n"
        f"Retrieved policy documents:\n{docs_section}\n\n"
        f"API tool results:\n{tools_section}\n\n"
        "Rules:\n"
        "- Answer only from the context above. Do not fabricate citations.\n"
        "- If both docs and tool_results are empty, set confidence below 0.4 and\n"
        "  acknowledge you have no information to provide a reliable answer.\n"
        "- Each citation quote must be verbatim from the docs (10–240 chars).\n"
        "- Keep your answer between 1 and 2000 characters."
    )


@deadline(seconds=8.0, sentinel={"answer": "[deadline exceeded]"})
@traceable(name="synthesis_agent")
async def synthesis_agent(
    state: AgentState, config: Optional[RunnableConfig] = None
) -> dict[str, Any]:
    instructor_client: Any = _get_dep(config, state, "__instructor")
    guard: BudgetGuard = _get_dep(config, state, "__budget_guard", BudgetGuard())

    if instructor_client is None:
        return {
            "answer": "[no instructor client configured]",
            "cost_usd_e5": 0,
            "visited_nodes": ["synthesis_agent"],
        }

    guard.check_or_raise()

    docs: list[dict[str, Any]] = state.get("docs", [])
    tool_results: dict[str, Any] = state.get("tool_results", {})
    system_prompt = _build_system_prompt(state["tenant_id"], docs, tool_results)

    answer, completion = await instructor_client.messages.create_with_completion(
        model=_MODEL,
        max_tokens=2000,
        system=system_prompt,
        messages=[{"role": "user", "content": state["question"]}],
        response_model=FinalAnswer,
        max_retries=2,
    )

    cost_e5: int = (
        int(completion.usage.input_tokens) * 300 + int(completion.usage.output_tokens) * 1500
    ) // 1000

    return {
        "answer": answer.model_dump_json(),
        "cost_usd_e5": cost_e5,
        "visited_nodes": ["synthesis_agent"],
    }
