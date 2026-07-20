"""Verify that the real PostgresSaver + recursion_limit triggers GraphRecursionError.

Uses a purpose-built two-node cycling graph so the test is self-contained
and runs independently of the expense agent graph topology.
"""

from __future__ import annotations

from typing import Any, TypedDict

import pytest
from langgraph.errors import GraphRecursionError
from langgraph.graph import END, START, StateGraph


class _LoopState(TypedDict):
    count: int


async def _node_a(state: _LoopState) -> dict[str, int]:
    return {"count": state["count"] + 1}


async def _node_b(state: _LoopState) -> dict[str, int]:
    return {"count": state["count"]}


def _always_a(state: _LoopState) -> str:
    return "a"


def _build_loop_graph(checkpointer: Any) -> Any:
    g: StateGraph[_LoopState] = StateGraph(_LoopState)
    g.add_node("a", _node_a)
    g.add_node("b", _node_b)
    g.add_edge(START, "a")
    g.add_edge("a", "b")
    g.add_conditional_edges("b", _always_a, {"a": "a", END: END})
    return g.compile(checkpointer=checkpointer)


@pytest.mark.asyncio
async def test_recursion_limit_raises(checkpointer: Any) -> None:
    graph = _build_loop_graph(checkpointer)
    config: dict[str, Any] = {
        "configurable": {"thread_id": "loop-test-001"},
        "recursion_limit": 5,
    }
    with pytest.raises(GraphRecursionError):
        await graph.ainvoke({"count": 0}, config=config)
