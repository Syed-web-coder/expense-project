"""LangGraph expense agent: supervisor routes to retrieval_agent / api_agent / both.

The supervisor is the single policy point for routing — future additions (tenant
gates, rate-limit checks, A/B experiments) belong here, not in the nodes.

Fan-out via Send objects: both agents may run in parallel when the question
touches both policy docs and live API data.  Their results are merged by the
AgentState reducers before synthesis_agent runs.
"""

from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph
from langgraph.types import Send

from expense_agent_svc.nodes.api import api_agent
from expense_agent_svc.nodes.retrieval import retrieval_agent
from expense_agent_svc.nodes.synthesis import synthesis_agent
from expense_agent_svc.settings import Settings
from expense_agent_svc.state import AgentState

_DOCS_KEYWORDS = frozenset({"policy", "docs", "how do i", "rule"})
_API_KEYWORDS = frozenset({"order", "refund", "status"})


def supervisor(state: AgentState) -> list[Send]:
    """Route the question to one or both agent branches.

    Keyword matching is deliberately simple — this is the place to add
    semantic routing, tenant-specific rules, or rate-limit guards later.
    """
    q = state["question"].lower()
    wants_docs = any(kw in q for kw in _DOCS_KEYWORDS)
    wants_api = any(kw in q for kw in _API_KEYWORDS)

    if wants_docs and wants_api:
        return [Send("retrieval_agent", state), Send("api_agent", state)]
    if wants_api:
        return [Send("api_agent", state)]
    # docs-only or neither → retrieval_agent (never blind)
    return [Send("retrieval_agent", state)]


def build_expense_agent_graph(
    settings: Settings,
    *,
    checkpointer: Any = None,
) -> Any:
    """Build and compile the expense agent StateGraph.

    If *checkpointer* is None, a PostgresSaver is constructed from
    settings.postgres_url and .setup() is called to initialise the schema.
    Pass a checkpointer explicitly in tests to use a testcontainers instance.
    """
    graph: StateGraph[AgentState] = StateGraph(AgentState)
    graph.add_node("retrieval_agent", retrieval_agent)
    graph.add_node("api_agent", api_agent)
    graph.add_node("synthesis_agent", synthesis_agent)

    graph.add_conditional_edges(START, supervisor, ["retrieval_agent", "api_agent"])
    graph.add_edge("retrieval_agent", "synthesis_agent")
    graph.add_edge("api_agent", "synthesis_agent")
    graph.add_edge("synthesis_agent", END)

    if checkpointer is None:
        import psycopg
        from langgraph.checkpoint.postgres import PostgresSaver
        from psycopg.rows import dict_row

        conn = psycopg.connect(settings.postgres_url, autocommit=True, row_factory=dict_row)
        checkpointer = PostgresSaver(conn)
        checkpointer.setup()

    return graph.compile(checkpointer=checkpointer)
