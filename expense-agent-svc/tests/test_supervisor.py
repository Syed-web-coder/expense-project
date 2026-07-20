from __future__ import annotations

from expense_agent_svc.graph import supervisor
from expense_agent_svc.state import AgentState


def _state(question: str) -> AgentState:
    return AgentState(
        question=question,
        tenant_id="tenant-a",
        thread_id="th-1",
        messages=[],
        docs=[],
        tool_results={},
        answer=None,
        cost_usd_e5=0,
        visited_nodes=[],
    )


def test_docs_only_routes_to_retrieval() -> None:
    sends = supervisor(_state("What is the expense policy?"))
    assert len(sends) == 1
    assert sends[0].node == "retrieval_agent"


def test_docs_keyword_how_do_i() -> None:
    sends = supervisor(_state("how do i submit a travel claim?"))
    assert len(sends) == 1
    assert sends[0].node == "retrieval_agent"


def test_api_only_routes_to_api() -> None:
    sends = supervisor(_state("get order 12345 status"))
    assert len(sends) == 1
    assert sends[0].node == "api_agent"


def test_both_keywords_routes_to_both() -> None:
    sends = supervisor(_state("what is the refund rule for order 99?"))
    nodes = {s.node for s in sends}
    assert nodes == {"retrieval_agent", "api_agent"}


def test_neither_keyword_defaults_to_retrieval() -> None:
    sends = supervisor(_state("hello, good morning"))
    assert len(sends) == 1
    assert sends[0].node == "retrieval_agent"
