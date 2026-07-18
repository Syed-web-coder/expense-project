from __future__ import annotations

import os
from collections.abc import Iterable

import psycopg
from pgvector.psycopg import register_vector

from expense_ai.corpus import CorpusRow

_INSERT_SQL = """
INSERT INTO doc_chunks
    (doc_id, chunk_idx, chunk_text, embedding, model_version, tenant_id)
VALUES
    (%(doc_id)s, %(chunk_idx)s, %(chunk_text)s, %(embedding)s, %(model_version)s, %(tenant_id)s)
ON CONFLICT (doc_id, chunk_idx, model_version)
DO UPDATE SET
    chunk_text = EXCLUDED.chunk_text,
    embedding  = EXCLUDED.embedding
"""


def load_rows(dsn: str, rows: Iterable[CorpusRow]) -> int:
    payload = list(rows)
    if not payload:
        return 0

    params = [
        {
            "doc_id": r.doc_id,
            "chunk_idx": r.chunk_idx,
            "chunk_text": r.chunk_text,
            "embedding": r.embedding,
            "model_version": r.model_version,
            "tenant_id": r.tenant_id,
        }
        for r in payload
    ]

    with psycopg.connect(dsn) as conn:
        register_vector(conn)
        with conn.cursor() as cur:
            cur.executemany(_INSERT_SQL, params)
        conn.commit()

    return len(payload)


def dsn_from_env() -> str:
    return os.environ["EXPENSE_AI_PG_DSN"]
