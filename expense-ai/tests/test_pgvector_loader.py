from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
import psycopg
import pytest
from numpy.typing import NDArray

from expense_ai.corpus import EMBEDDING_DIM, CorpusRow
from expense_ai.pgvector_loader import load_rows

FIXTURE = Path(__file__).parent / "fixtures" / "corpus_seed.jsonl"


class _FakeModel:
    """Deterministic float32 encoder; avoids downloading the real model."""

    def encode(
        self,
        texts: list[str],
        batch_size: int = 64,
        normalize_embeddings: bool = True,
        convert_to_numpy: bool = True,
    ) -> NDArray[np.float32]:
        rng = np.random.default_rng(0)
        arr = rng.random((len(texts), EMBEDDING_DIM)).astype(np.float32)
        return arr


def _make_rows(n: int | None = None) -> list[CorpusRow]:
    df = pd.read_json(FIXTURE, lines=True)
    if n is not None:
        df = df.head(n)
    return _embed(df)


def _embed(df: pd.DataFrame) -> list[CorpusRow]:
    from expense_ai.corpus import embed_dataframe

    return embed_dataframe(df, model=_FakeModel())


@pytest.mark.integration
def test_load_100_rows(pg_dsn: str) -> None:
    rows = _make_rows(100)
    count = load_rows(pg_dsn, rows)
    assert count == 100

    loaded_ids = list({r.doc_id for r in rows})
    with psycopg.connect(pg_dsn) as conn:
        result = conn.execute(
            "SELECT COUNT(*) FROM doc_chunks WHERE doc_id = ANY(%s)", (loaded_ids,)
        ).fetchone()
    assert result is not None
    assert result[0] == 100


@pytest.mark.integration
def test_idempotency(pg_dsn: str) -> None:
    rows = _make_rows(100)
    load_rows(pg_dsn, rows)
    load_rows(pg_dsn, rows)

    loaded_ids = list({r.doc_id for r in rows})
    with psycopg.connect(pg_dsn) as conn:
        result = conn.execute(
            "SELECT COUNT(*) FROM doc_chunks WHERE doc_id = ANY(%s)", (loaded_ids,)
        ).fetchone()
    assert result is not None
    assert result[0] == 100


@pytest.mark.integration
def test_hnsw_index_exists(pg_dsn: str) -> None:
    with psycopg.connect(pg_dsn) as conn:
        result = conn.execute(
            "SELECT indexname FROM pg_indexes WHERE indexname = 'doc_chunks_embedding_hnsw'"
        ).fetchone()
    assert result is not None, "HNSW index doc_chunks_embedding_hnsw not found in pg_indexes"


@pytest.mark.integration
def test_cosine_query_uses_hnsw(pg_dsn: str) -> None:
    rng = np.random.default_rng(7)
    query_vec = rng.random(EMBEDDING_DIM).astype(np.float32).tolist()

    with psycopg.connect(pg_dsn) as conn:
        from pgvector.psycopg import register_vector

        register_vector(conn)
        with conn.transaction():
            # Disable seqscan; omit b-tree-friendly WHERE so HNSW is the only
            # remaining index option for ORDER BY distance LIMIT 5.
            conn.execute("SET LOCAL enable_seqscan = off")
            plan = conn.execute(
                """
                EXPLAIN
                SELECT chunk_id, chunk_text, embedding <=> %s::vector AS distance
                FROM doc_chunks
                ORDER BY distance
                LIMIT 5
                """,
                (query_vec,),
            ).fetchall()

    plan_text = "\n".join(row[0] for row in plan)
    assert "doc_chunks_embedding_hnsw" in plan_text, (
        f"Expected HNSW index in plan, got:\n{plan_text}"
    )
