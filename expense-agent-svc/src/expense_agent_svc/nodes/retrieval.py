from __future__ import annotations

import asyncio
from typing import Any, Optional

from langchain_core.runnables import RunnableConfig
from langsmith import traceable

from expense_agent_svc.nodes._deadline import deadline
from expense_agent_svc.state import AgentState


def _get_dep(
    config: Optional[RunnableConfig], state: AgentState, key: str, default: Any = None
) -> Any:
    """Check config['configurable'] first (not serialized), fall back to state slot."""
    cfg: dict[str, Any] = (config or {}).get("configurable", {})
    if key in cfg:
        return cfg[key]
    return state.get(key, default)


@deadline(seconds=3.0, sentinel={"docs": []})
@traceable(name="retrieval_agent")
async def retrieval_agent(
    state: AgentState, config: Optional[RunnableConfig] = None
) -> dict[str, Any]:
    retriever: Any = _get_dep(config, state, "__retriever")
    if retriever is not None:
        docs_raw: list[dict[str, Any]] = await retriever(
            question=state["question"],
            tenant_id=state["tenant_id"],
            k=8,
        )
    else:
        from expense_ai.rag import retrieve_chunks

        pg_dsn: str = _get_dep(
            config, state, "__pg_dsn", "postgresql://postgres:postgres@localhost:5432/postgres"
        )
        docs_raw = await asyncio.to_thread(
            retrieve_chunks,
            pg_dsn,
            state["question"],
            8,
            state["tenant_id"],
        )

    docs = [
        {
            "doc_id": str(d.get("doc_id", "")),
            "chunk_idx": int(d.get("chunk_idx", 0)),
            "score": float(1.0 - d.get("distance", 0.0))
            if "distance" in d
            else float(d.get("score", 0.0)),
        }
        for d in docs_raw[:8]
    ]
    return {"docs": docs, "cost_usd_e5": 0, "visited_nodes": ["retrieval_agent"]}
