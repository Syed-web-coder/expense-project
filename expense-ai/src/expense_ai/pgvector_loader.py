from __future__ import annotations

import hashlib
import json
import os
from collections.abc import Iterable
from typing import Any

import psycopg
from pgvector.psycopg import register_vector

from expense_ai.corpus import CorpusRow

_INSERT_SQL = """
INSERT INTO doc_chunks
    (doc_id, chunk_idx, chunk_text, embedding, model_version,
     tenant_id, chunk_metadata, content_hash)
VALUES
    (%(doc_id)s, %(chunk_idx)s, %(chunk_text)s, %(embedding)s, %(model_version)s,
     %(tenant_id)s, %(chunk_metadata)s::jsonb, %(content_hash)s)
ON CONFLICT (doc_id, chunk_idx, model_version)
DO UPDATE SET
    chunk_text     = EXCLUDED.chunk_text,
    embedding      = EXCLUDED.embedding,
    chunk_metadata = EXCLUDED.chunk_metadata,
    content_hash   = EXCLUDED.content_hash
"""

_CHECK_HASH_SQL = """
SELECT 1
FROM doc_chunks
WHERE doc_id = %(doc_id)s
  AND chunk_idx = %(chunk_idx)s
  AND model_version = %(model_version)s
  AND content_hash = %(content_hash)s
"""


def content_sha(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def filter_unchanged(
    conn: psycopg.Connection[Any],
    rows_meta: list[tuple[str, int, str, str]],
) -> set[tuple[str, int, str]]:
    result: set[tuple[str, int, str]] = set()
    with conn.cursor() as cur:
        for doc_id, chunk_idx, model_version, content_hash in rows_meta:
            cur.execute(
                _CHECK_HASH_SQL,
                {
                    "doc_id": doc_id,
                    "chunk_idx": chunk_idx,
                    "model_version": model_version,
                    "content_hash": content_hash,
                },
            )
            if cur.fetchone() is not None:
                result.add((doc_id, chunk_idx, model_version))
    return result


def load_rows(dsn: str, rows: Iterable[CorpusRow]) -> int:
    payload = list(rows)
    if not payload:
        return 0

    params: list[dict[str, Any]] = [
        {
            "doc_id": r.doc_id,
            "chunk_idx": r.chunk_idx,
            "chunk_text": r.chunk_text,
            "embedding": r.embedding,
            "model_version": r.model_version,
            "tenant_id": r.tenant_id,
            "chunk_metadata": json.dumps(r.chunk_metadata),
            "content_hash": r.content_hash,
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
