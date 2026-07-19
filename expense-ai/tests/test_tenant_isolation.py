from __future__ import annotations

import os

import numpy as np
import psycopg
import pytest
from numpy.typing import NDArray

from expense_ai.corpus import EMBEDDING_DIM, CorpusRow
from expense_ai.hybrid import dense_topk_filtered
from expense_ai.pgvector_loader import load_rows

os.environ.setdefault("LANGSMITH_API_KEY", "test-dummy-langsmith-key")
os.environ["LANGSMITH_TRACING"] = "false"

_ISO_MODEL = "tenant-isolation-test-model"
_TENANTS = ("tenant-a", "tenant-b", "tenant-c")
_DOCS_PER_TENANT = 5


@pytest.mark.integration
def test_dense_retrieval_never_leaks_cross_tenant(pg_dsn: str) -> None:
    """dense_topk_filtered with tenant_id='tenant-a' must return only tenant-a docs."""
    rng = np.random.default_rng(200)

    rows: list[CorpusRow] = []
    for tenant in _TENANTS:
        for i in range(_DOCS_PER_TENANT):
            vec: NDArray[np.float32] = rng.random(EMBEDDING_DIM).astype(np.float32)
            rows.append(
                CorpusRow(
                    doc_id=f"iso-{tenant}-doc-{i}",
                    chunk_idx=0,
                    chunk_text=f"Expense document {i} for {tenant}",
                    embedding=vec,
                    model_version=_ISO_MODEL,
                    tenant_id=tenant,
                )
            )
    load_rows(pg_dsn, rows)

    query_vec: list[float] = rng.random(EMBEDDING_DIM).astype(np.float32).tolist()

    with psycopg.connect(pg_dsn) as conn:
        results = dense_topk_filtered(
            conn,
            query_vec,
            "tenant-a",
            k=20,
            model_version=_ISO_MODEL,
        )

    assert len(results) > 0, "Expected at least one result for tenant-a"

    # Verify DB-side: each returned doc_id must belong to tenant-a
    with psycopg.connect(pg_dsn) as conn:
        for doc_id, _chunk_text, _dist in results:
            row = conn.execute(
                "SELECT tenant_id FROM doc_chunks WHERE doc_id = %s AND model_version = %s",
                (doc_id, _ISO_MODEL),
            ).fetchone()
            assert row is not None, f"doc_id {doc_id!r} not found in DB"
            assert row[0] == "tenant-a", (
                f"doc {doc_id!r} has tenant_id={row[0]!r}, expected 'tenant-a'"
            )
