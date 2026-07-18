from __future__ import annotations

from typing import Any

import numpy as np
import psycopg
import pytest
from numpy.typing import NDArray

import expense_ai.rag as rag_module
from expense_ai.corpus import EMBEDDING_DIM, MODEL_NAME, CorpusRow
from expense_ai.pgvector_loader import load_rows

_TENANT = "rag-test-tenant"
_N_SEED = 10


class _FakeModel:
    def encode(
        self,
        texts: list[str],
        *,
        normalize_embeddings: bool = True,
        convert_to_numpy: bool = True,
        batch_size: int = 64,
    ) -> NDArray[np.float32]:
        rng = np.random.default_rng(99)
        return rng.random((len(texts), EMBEDDING_DIM)).astype(np.float32)


def _fake_model() -> Any:
    return _FakeModel()


def _seed(dsn: str) -> None:
    rng = np.random.default_rng(99)
    rows = [
        CorpusRow(
            doc_id=f"rag-test-doc-{i}",
            chunk_idx=0,
            chunk_text=f"Schedule C deduction chunk {i}: business meals and mileage deductions.",
            embedding=rng.random(EMBEDDING_DIM).astype(np.float32),
            model_version=MODEL_NAME,
            tenant_id=_TENANT,
        )
        for i in range(_N_SEED)
    ]
    load_rows(dsn, rows)


def test_retrieve_chunks_raises_without_langsmith_key(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("LANGSMITH_API_KEY", raising=False)
    with pytest.raises(RuntimeError, match="LANGSMITH_API_KEY"):
        rag_module.retrieve_chunks.__wrapped__("postgresql://fake/db", "test question")  # type: ignore[attr-defined]


def test_retrieve_chunks_is_traceable() -> None:
    assert hasattr(rag_module.retrieve_chunks, "__wrapped__"), (
        "retrieve_chunks must be wrapped by @traceable"
    )


@pytest.mark.integration
def test_retrieve_chunks_returns_sorted_results(
    pg_dsn: str,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LANGSMITH_API_KEY", "test-dummy-key-not-real")
    monkeypatch.setenv("LANGSMITH_TRACING", "false")
    monkeypatch.setattr(rag_module, "_get_model", _fake_model)
    # also reset cached model so _fake_model is used
    monkeypatch.setattr(rag_module, "_model", None)

    # ensure rows exist (idempotent due to ON CONFLICT UPDATE)
    with psycopg.connect(pg_dsn) as conn:
        count = conn.execute(
            "SELECT COUNT(*) FROM doc_chunks WHERE tenant_id = %s AND model_version = %s",
            (_TENANT, MODEL_NAME),
        ).fetchone()
    if count is None or count[0] < _N_SEED:
        _seed(pg_dsn)

    k = 3
    results = rag_module.retrieve_chunks(
        pg_dsn,
        "What are the Schedule C meal deduction rules?",
        k=k,
        tenant_id=_TENANT,
        model_version=MODEL_NAME,
    )

    assert len(results) == k

    expected_keys = {"doc_id", "chunk_idx", "chunk_text", "distance"}
    for row in results:
        assert set(row.keys()) == expected_keys

    distances = [float(str(row["distance"])) for row in results]
    assert distances == sorted(distances), "Results must be sorted by ascending distance"
