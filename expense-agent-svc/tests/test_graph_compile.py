from __future__ import annotations

from typing import Any

import pytest

from expense_agent_svc.graph import build_expense_agent_graph
from expense_agent_svc.settings import Settings


@pytest.fixture()
def settings(pg_dsn: str) -> Settings:
    return Settings(postgres_url=pg_dsn)


def test_graph_has_nodes_and_checkpointer(sync_checkpointer: Any, settings: Settings) -> None:
    checkpointer = sync_checkpointer
    compiled = build_expense_agent_graph(settings, checkpointer=checkpointer)
    assert "retrieval_agent" in compiled.nodes
    assert "api_agent" in compiled.nodes
    assert "synthesis_agent" in compiled.nodes
    assert compiled.checkpointer is not None
