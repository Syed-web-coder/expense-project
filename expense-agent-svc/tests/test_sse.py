"""Tests for the event_stream SSE adapter.

Drives event_stream over a faked graph and asserts SSE frame types:
  0: — at least one streaming delta
  2: — exactly one finalAnswer frame
  3: — error frame on BudgetExceeded / GraphRecursionError
"""

from __future__ import annotations

from typing import Any
from unittest.mock import MagicMock

import pytest

from expense_agent_svc.budgets import BudgetExceeded
from expense_agent_svc.sse import event_stream


def _make_synthesis_end_event(answer_json: str) -> dict[str, Any]:
    return {
        "event": "on_chain_end",
        "name": "synthesis_agent",
        "data": {"output": {"answer": answer_json, "cost_usd_e5": 100}},
    }


def _make_stream_chunk(text: str) -> dict[str, Any]:
    chunk = MagicMock()
    chunk.content = text
    return {
        "event": "on_chat_model_stream",
        "name": "ChatAnthropic",
        "data": {"chunk": chunk},
    }


def _make_graph(events: list[dict[str, Any]]) -> Any:
    graph = MagicMock()

    async def _astream(*args: Any, **kwargs: Any) -> Any:
        for e in events:
            yield e

    graph.astream_events = _astream
    return graph


# ── Happy path ────────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_sse_happy_path() -> None:
    answer_json = '{"text":"Travel up to $500 is reimbursable.","citations":[],"confidence":0.9}'
    graph = _make_graph(
        [
            _make_stream_chunk("Travel"),
            _make_stream_chunk(" up to $500"),
            _make_synthesis_end_event(answer_json),
        ]
    )

    frames: list[bytes] = []
    async for chunk in event_stream(graph, "question", "tenant-a", "thread-1"):
        frames.append(chunk)

    lines = [f.decode().rstrip("\n") for f in frames]
    zero_lines = [ln for ln in lines if ln.startswith("0:")]
    two_lines = [ln for ln in lines if ln.startswith("2:")]

    assert len(zero_lines) >= 1, "expected at least one 0: delta frame"
    assert len(two_lines) == 1, f"expected exactly one 2: finalAnswer frame, got {len(two_lines)}"

    import json

    payload = json.loads(two_lines[0][2:])
    assert "finalAnswer" in payload
    assert payload["finalAnswer"]["text"] == "Travel up to $500 is reimbursable."


# ── BudgetExceeded path ───────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_sse_budget_exceeded() -> None:
    graph = MagicMock()

    async def _raise_budget(*args: Any, **kwargs: Any) -> Any:
        raise BudgetExceeded("ceiling reached")
        yield  # make static analysers treat this as an async generator

    graph.astream_events = _raise_budget

    frames: list[bytes] = []
    async for chunk in event_stream(graph, "q", "t", "th"):
        frames.append(chunk)

    lines = [f.decode().rstrip("\n") for f in frames]
    error_lines = [ln for ln in lines if ln.startswith("3:")]
    assert len(error_lines) == 1

    import json

    err = json.loads(error_lines[0][2:])
    assert err["error"] == "budget_exceeded"


# ── GraphRecursionError path ──────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_sse_recursion_limit() -> None:
    from langgraph.errors import GraphRecursionError

    graph = MagicMock()

    async def _raise_recursion(*args: Any, **kwargs: Any) -> Any:
        raise GraphRecursionError("recursion limit")
        yield

    graph.astream_events = _raise_recursion

    frames: list[bytes] = []
    async for chunk in event_stream(graph, "q", "t", "th", recursion_limit=3):
        frames.append(chunk)

    lines = [f.decode().rstrip("\n") for f in frames]
    error_lines = [ln for ln in lines if ln.startswith("3:")]
    assert len(error_lines) == 1

    import json

    err = json.loads(error_lines[0][2:])
    assert err["error"] == "recursion_limit"
