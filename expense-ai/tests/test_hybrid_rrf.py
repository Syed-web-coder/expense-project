from __future__ import annotations

import os

import numpy as np
import psycopg
import pytest
from numpy.typing import NDArray

from expense_ai.corpus import EMBEDDING_DIM, CorpusRow
from expense_ai.hybrid import coverage, dense_topk_filtered, rrf_fuse, sparse_topk_fts
from expense_ai.pgvector_loader import load_rows

# Disable LangSmith tracing so @traceable decorators are no-ops in tests
os.environ.setdefault("LANGSMITH_API_KEY", "test-dummy-langsmith-key")
os.environ["LANGSMITH_TRACING"] = "false"

_MODEL = "hybrid-test-model"
_TENANT = "tenant-a"


def _row(
    doc_id: str,
    chunk_text: str,
    embedding: NDArray[np.float32],
    chunk_metadata: dict[str, str] | None = None,
) -> CorpusRow:
    return CorpusRow(
        doc_id=doc_id,
        chunk_idx=0,
        chunk_text=chunk_text,
        embedding=embedding,
        model_version=_MODEL,
        tenant_id=_TENANT,
        chunk_metadata=dict(chunk_metadata) if chunk_metadata is not None else {},
    )


@pytest.mark.integration
def test_metadata_filter_applies(pg_dsn: str) -> None:
    rng = np.random.default_rng(10)
    load_rows(
        pg_dsn,
        [
            _row(
                "hyb-food-doc-01",
                "restaurant vendor invoice for business meal deduction",
                rng.random(EMBEDDING_DIM).astype(np.float32),
                {"category": "food"},
            ),
            _row(
                "hyb-travel-doc-01",
                "airline ticket reimbursement for business travel expense",
                rng.random(EMBEDDING_DIM).astype(np.float32),
                {"category": "travel"},
            ),
        ],
    )
    query_vec = rng.random(EMBEDDING_DIM).astype(np.float32).tolist()
    with psycopg.connect(pg_dsn) as conn:
        results = dense_topk_filtered(
            conn,
            query_vec,
            _TENANT,
            metadata_filter={"category": "food"},
            k=20,
            model_version=_MODEL,
        )
    doc_ids = [r[0] for r in results]
    assert "hyb-food-doc-01" in doc_ids
    assert "hyb-travel-doc-01" not in doc_ids


@pytest.mark.integration
def test_sparse_fts_finds_distinctive_text(pg_dsn: str) -> None:
    rng = np.random.default_rng(20)
    load_rows(
        pg_dsn,
        [
            _row(
                "hyb-sparse-distinctive",
                "zephrex procurement authorization overseas subsidiary vendor payment",
                rng.random(EMBEDDING_DIM).astype(np.float32),
            ),
            _row(
                "hyb-sparse-generic",
                "standard expense report for quarterly review and filing",
                rng.random(EMBEDDING_DIM).astype(np.float32),
            ),
        ],
    )
    with psycopg.connect(pg_dsn) as conn:
        results = sparse_topk_fts(conn, "zephrex", _TENANT, k=20)
    doc_ids = [r[0] for r in results]
    assert "hyb-sparse-distinctive" in doc_ids
    assert "hyb-sparse-generic" not in doc_ids


def test_rrf_fuse_covers_both_disjoint_lists() -> None:
    dense: list[tuple[str, str, float]] = [
        ("doc-a", "text a", 0.1),
        ("doc-b", "text b", 0.2),
    ]
    sparse: list[tuple[str, str, float]] = [
        ("doc-c", "text c", 0.9),
        ("doc-d", "text d", 0.8),
    ]
    result = rrf_fuse(dense, sparse)
    ids = {r[0] for r in result}
    assert {"doc-a", "doc-b", "doc-c", "doc-d"} <= ids


def test_coverage_jaccard_finite() -> None:
    dense: list[tuple[str, str, float]] = [
        ("doc-a", "text a", 0.1),
        ("doc-b", "text b", 0.2),
    ]
    sparse: list[tuple[str, str, float]] = [
        ("doc-b", "text b", 0.9),
        ("doc-c", "text c", 0.8),
    ]
    cov = coverage(dense, sparse)
    assert 0.0 <= cov["jaccard"] <= 1.0
    assert cov["both"] == 1.0
    assert cov["dense_only"] == 1.0
    assert cov["sparse_only"] == 1.0
