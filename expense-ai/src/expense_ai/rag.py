from __future__ import annotations

import os
from typing import Any

import psycopg
from langsmith import traceable
from pgvector.psycopg import register_vector

from expense_ai.corpus import MODEL_NAME

_model: Any = None  # lazy SentenceTransformer; not loaded at import time

_QUERY_SQL = """
SELECT doc_id, chunk_idx, chunk_text, embedding <=> %s::vector AS distance
FROM doc_chunks
WHERE tenant_id = %s AND model_version = %s
ORDER BY distance
LIMIT %s
"""


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
