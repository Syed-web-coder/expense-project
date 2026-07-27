"""api_agent — calls MCP tools via the Anthropic tool-use loop.

Idempotency key is injected into the tool input dict under "_idempotency_key"
because mcp.ClientSession.call_tool() does not accept a headers parameter.

Dependencies are resolved from config['configurable'] first (not serialized by
the checkpointer), falling back to state slots for backward compatibility.
"""

from __future__ import annotations

import json
import uuid
from typing import Any, Optional

from langchain_core.runnables import RunnableConfig
from langsmith import traceable

from expense_agent_svc.budgets import BudgetGuard
from expense_agent_svc.nodes._deadline import deadline
from expense_agent_svc.nodes.retrieval import _get_dep
from expense_agent_svc.state import AgentState

_MAX_ITERATIONS = 5
_MODEL = "claude-sonnet-4-5"


@deadline(seconds=5.0, sentinel={"tool_results": {}})
@traceable(name="api_agent")
async def api_agent(state: AgentState, config: Optional[RunnableConfig] = None) -> dict[str, Any]:
    session: Any = _get_dep(config, state, "__mcp_session")
    guard: BudgetGuard = _get_dep(config, state, "__budget_guard", BudgetGuard())
    anthropic: Any = _get_dep(config, state, "__anthropic")
    thread_id: str = state["thread_id"]

    if session is None or anthropic is None:
        return {"tool_results": {}, "cost_usd_e5": 0, "visited_nodes": ["api_agent"]}

    tools_result = await session.list_tools()
    tools = [
        {
            "name": t.name,
            "description": t.description or "",
            "input_schema": t.inputSchema,
        }
        for t in tools_result.tools
    ]

    messages: list[dict[str, Any]] = [{"role": "user", "content": state["question"]}]
    tool_results: dict[str, Any] = {}

    for _ in range(_MAX_ITERATIONS):
        guard.check_or_raise()
        resp = await anthropic.messages.create(
            model=_MODEL,
            max_tokens=4096,
            tools=tools,
            messages=messages,
        )
        guard.record_call(resp)

        assistant_content: list[Any] = list(resp.content)
        messages.append({"role": "assistant", "content": assistant_content})

        if resp.stop_reason != "tool_use":
            break

        tool_result_blocks: list[dict[str, Any]] = []
        for block in resp.content:
            if block.type != "tool_use":
                continue
            idem_key = str(
                uuid.uuid5(
                    uuid.NAMESPACE_OID,
                    f"{thread_id}:{block.name}:{json.dumps(block.input, sort_keys=True)}",
                )
            )
            args: dict[str, Any] = dict(block.input)
            # mcp.ClientSession.call_tool() has no headers param — inject key into args
            args["_idempotency_key"] = idem_key
            call_result = await session.call_tool(block.name, args)
            tool_results[block.id] = {
                "tool_name": block.name,
                "input": block.input,
                "output": call_result.content,
            }
            tool_result_blocks.append(
                {
                    "type": "tool_result",
                    "tool_use_id": block.id,
                    "content": call_result.content,
                }
            )
        messages.append({"role": "user", "content": tool_result_blocks})

    return {
        "tool_results": tool_results,
        "cost_usd_e5": guard.spent_delta or 0,
        "visited_nodes": ["api_agent"],
    }
