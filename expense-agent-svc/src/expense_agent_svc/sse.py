"""Server-Sent Events streaming adapter for the expense agent graph.

Protocol
--------
0:<json>\\n  — streaming delta from on_chat_model_stream
2:<json>\\n  — final synthesis answer on on_chain_end for synthesis_agent
3:<json>\\n  — error (recursion_limit | budget_exceeded)
"""

from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

from langsmith import traceable

from expense_agent_svc.budgets import BudgetExceeded


@traceable(name="chat_request")
async def event_stream(
    graph: Any,
    question: str,
    tenant_id: str,
    thread_id: str,
    *,
    recursion_limit: int = 25,
    configurable_extras: dict[str, Any] | None = None,
) -> AsyncIterator[bytes]:
    """Yield SSE-encoded bytes from a LangGraph astream_events call."""
    from langgraph.errors import GraphRecursionError

    inputs: dict[str, Any] = {
        "question": question,
        "tenant_id": tenant_id,
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
        },
        "recursion_limit": recursion_limit,
    }

    try:
        async for event in graph.astream_events(inputs, config, version="v2"):
            kind: str = event.get("event", "")

            if kind == "on_chat_model_stream":
                chunk = event["data"].get("chunk")
                if chunk is not None:
                    content = getattr(chunk, "content", "")
                    if isinstance(content, str) and content:
                        yield b"0:" + json.dumps({"delta": content}).encode() + b"\n"
                    elif isinstance(content, list) and content:
                        text = "".join(
                            p["text"] if isinstance(p, dict) and p.get("type") == "text" else ""
                            for p in content
                        )
                        if text:
                            yield b"0:" + json.dumps({"delta": text}).encode() + b"\n"

            elif kind == "on_chain_end" and event.get("name") == "synthesis_agent":
                output: dict[str, Any] = event["data"].get("output") or {}
                answer_json: str | None = output.get("answer")
                if answer_json:
                    try:
                        parsed: Any = json.loads(answer_json)
                    except (json.JSONDecodeError, TypeError):
                        parsed = answer_json
                    yield b"2:" + json.dumps({"finalAnswer": parsed}).encode() + b"\n"

    except GraphRecursionError:
        yield (
            b"3:"
            + json.dumps({"error": "recursion_limit", "detail": "max recursion reached"}).encode()
            + b"\n"
        )
    except BudgetExceeded as exc:
        yield (
            b"3:" + json.dumps({"error": "budget_exceeded", "detail": str(exc)}).encode() + b"\n"
        )
