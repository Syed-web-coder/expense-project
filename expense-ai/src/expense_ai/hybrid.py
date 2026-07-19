from __future__ import annotations

import json
from typing import Any

import psycopg
from langsmith import traceable
from pgvector.psycopg import register_vector

K_CONST = 60

_DENSE_BASE = """
SELECT doc_id, chunk_text, embedding <=> %s::vector AS distance
FROM doc_chunks
WHERE tenant_id = %s AND model_version = %s"""

_SPARSE_SQL = """
SELECT d.doc_id, d.chunk_text, ts_rank_cd(d.chunk_tsv, q.tsq) AS score
FROM doc_chunks d
CROSS JOIN LATERAL (SELECT websearch_to_tsquery('english', %s) AS tsq) q
WHERE d.tenant_id = %s
  AND d.chunk_tsv @@ q.tsq
ORDER BY score DESC
LIMIT %s
"""


@traceable(run_type="retriever", name="expense_ai.dense_topk_filtered")
def dense_topk_filtered(
    conn: psycopg.Connection[Any],
    query_vec: list[float],
    tenant_id: str,
    metadata_filter: dict[str, object] | None = None,
    k: int = 50,
    model_version: str = "all-MiniLM-L6-v2",
) -> list[tuple[str, str, float]]:
    register_vector(conn)
    if metadata_filter is not None:
        sql = _DENSE_BASE + " AND chunk_metadata @> %s::jsonb ORDER BY distance LIMIT %s"
        rows = conn.execute(
            sql, (query_vec, tenant_id, model_version, json.dumps(metadata_filter), k)
        ).fetchall()
    else:
        sql = _DENSE_BASE + " ORDER BY distance LIMIT %s"
        rows = conn.execute(sql, (query_vec, tenant_id, model_version, k)).fetchall()
    return [(str(row[0]), str(row[1]), float(row[2])) for row in rows]


@traceable(run_type="retriever", name="expense_ai.sparse_topk_fts")
def sparse_topk_fts(
    conn: psycopg.Connection[Any],
    query_text: str,
    tenant_id: str,
    k: int = 50,
) -> list[tuple[str, str, float]]:
    rows = conn.execute(_SPARSE_SQL, (query_text, tenant_id, k)).fetchall()
    return [(str(row[0]), str(row[1]), float(row[2])) for row in rows]


@traceable(run_type="chain", name="expense_ai.rrf_fuse")
def rrf_fuse(
    dense: list[tuple[str, str, float]],
    sparse: list[tuple[str, str, float]],
    k_const: int = K_CONST,
    w_dense: float = 1.0,
    w_sparse: float = 1.0,
    top_k: int = 60,
) -> list[tuple[str, str, float]]:
    scores: dict[str, float] = {}
    texts: dict[str, str] = {}
    for rank, (doc_id, chunk_text, _) in enumerate(dense, 1):
        scores[doc_id] = scores.get(doc_id, 0.0) + w_dense / (k_const + rank)
        texts[doc_id] = chunk_text
    for rank, (doc_id, chunk_text, _) in enumerate(sparse, 1):
        scores[doc_id] = scores.get(doc_id, 0.0) + w_sparse / (k_const + rank)
        texts[doc_id] = chunk_text
    sorted_items = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:top_k]
    return [(doc_id, texts[doc_id], score) for doc_id, score in sorted_items]


def coverage(
    dense: list[tuple[str, str, float]],
    sparse: list[tuple[str, str, float]],
) -> dict[str, float]:
    dense_ids = {row[0] for row in dense}
    sparse_ids = {row[0] for row in sparse}
    both = dense_ids & sparse_ids
    union = dense_ids | sparse_ids
    return {
        "dense_only": float(len(dense_ids - sparse_ids)),
        "sparse_only": float(len(sparse_ids - dense_ids)),
        "both": float(len(both)),
        "jaccard": float(len(both)) / float(len(union)) if union else 0.0,
    }
