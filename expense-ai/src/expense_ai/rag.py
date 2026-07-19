from __future__ import annotations

import os
from collections.abc import Callable
from typing import Any, Protocol

import numpy as np
import psycopg
import redis as redis_module
from langsmith import traceable
from numpy.typing import NDArray
from pgvector.psycopg import register_vector

from expense_ai.cache import cache_lookup, cache_store
from expense_ai.corpus import MODEL_NAME
from expense_ai.hybrid import dense_topk_filtered, rrf_fuse, sparse_topk_fts
from expense_ai.rerank import bge_rerank, mmr_pick

_model: Any = None  # lazy SentenceTransformer; not loaded at import time

_QUERY_SQL = """
SELECT doc_id, chunk_idx, chunk_text, embedding <=> %s::vector AS distance
FROM doc_chunks
WHERE tenant_id = %s AND model_version = %s
ORDER BY distance
LIMIT %s
"""


class _Embedder(Protocol):
    def encode(
        self,
        sentences: list[str],
        *,
        normalize_embeddings: bool = ...,
        convert_to_numpy: bool = ...,
    ) -> NDArray[np.float32]: ...


def _get_model() -> Any:
    global _model
    if _model is None:
        from sentence_transformers import SentenceTransformer

        _model = SentenceTransformer(MODEL_NAME)
    return _model


@traceable(run_type="retriever", name="expense_ai.retrieve_chunks")
def retrieve_chunks(
    dsn: str,
    question: str,
    k: int = 5,
    tenant_id: str = "tenant-a",
    model_version: str = MODEL_NAME,
) -> list[dict[str, object]]:
    if "LANGSMITH_API_KEY" not in os.environ:
        raise RuntimeError(
            "LANGSMITH_API_KEY is not set; configure it before calling retrieve_chunks"
        )
    model = _get_model()
    vec: list[float] = model.encode(
        [question],
        normalize_embeddings=True,
        convert_to_numpy=True,
    )[0].tolist()
    with psycopg.connect(dsn) as conn:
        register_vector(conn)
        rows = conn.execute(_QUERY_SQL, (vec, tenant_id, model_version, k)).fetchall()
    return [
        {
            "doc_id": str(row[0]),
            "chunk_idx": int(row[1]),
            "chunk_text": str(row[2]),
            "distance": float(row[3]),
        }
        for row in rows
    ]


def _build_prompt(query_text: str, ctx: list[tuple[str, str, float]]) -> str:
    numbered = "\n".join(f"[{i + 1}] {text}" for i, (_, text, _) in enumerate(ctx))
    return (
        f"Context:\n{numbered}\n\n"
        f"Question: {query_text}\n\n"
        f"Answer concisely using only the context above:"
    )


def retrieve_and_generate(
    query_text: str,
    tenant_id: str,
    *,
    anthropic: Any,
    conn: psycopg.Connection[Any],
    r: redis_module.Redis,
    metadata_filter: dict[str, object] | None = None,
    model_name: str = "claude-sonnet-4-5",
    use_hybrid: bool = True,
    use_mmr: bool = True,
    use_rerank: bool = True,
    use_filter: bool = True,
    embedder: _Embedder | None = None,
    scorer: Callable[[list[list[str]]], NDArray[np.float32]] | None = None,
) -> dict[str, object]:
    if "LANGSMITH_API_KEY" not in os.environ:
        raise RuntimeError(
            "LANGSMITH_API_KEY is not set; configure it before calling retrieve_and_generate"
        )

    # Embed query using injected embedder or lazy-loaded model
    active_embedder: Any = embedder if embedder is not None else _get_model()
    raw: Any = active_embedder.encode(
        [query_text],
        normalize_embeddings=True,
        convert_to_numpy=True,
    )
    query_vec: NDArray[np.float32] = np.asarray(raw[0], dtype=np.float32)

    # Semantic cache lookup
    cached = cache_lookup(r, query_vec, tenant_id)
    if cached is not None:
        return cached

    # Retrieval
    register_vector(conn)
    eff_filter = metadata_filter if use_filter else None
    dense = dense_topk_filtered(
        conn,
        query_vec.tolist(),
        tenant_id,
        metadata_filter=eff_filter,
        k=50,
        model_version=MODEL_NAME,
    )

    if use_hybrid:
        sparse = sparse_topk_fts(conn, query_text, tenant_id, k=50)
        fused: list[tuple[str, str, float]] = rrf_fuse(dense, sparse)[:60]
    else:
        fused = dense[:60]

    # MMR diversification
    if use_mmr:
        mmr_embedder: Any = embedder if embedder is not None else _get_model()
        diversified = mmr_pick(query_vec, fused, mmr_embedder, k=20)
    else:
        diversified = fused[:20]

    # Rerank
    if use_rerank:
        final_ctx, timed_out = bge_rerank(query_text, diversified, top_k=6, scorer=scorer)
    else:
        final_ctx, timed_out = diversified[:6], False

    # Generate via Anthropic
    prompt = _build_prompt(query_text, final_ctx)
    response: Any = anthropic.messages.create(
        model=model_name,
        max_tokens=512,
        messages=[{"role": "user", "content": prompt}],
    )
    text = str(response.content[0].text)

    answer: dict[str, object] = {
        "text": text,
        "citations": [
            {"doc_id": doc_id, "chunk_text": chunk_text, "tenant_id": tenant_id}
            for doc_id, chunk_text, _ in final_ctx
        ],
        "rerank_timed_out": timed_out,
    }

    cache_store(r, query_vec, tenant_id, answer)
    return answer
