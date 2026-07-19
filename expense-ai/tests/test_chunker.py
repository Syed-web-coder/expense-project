from __future__ import annotations

import numpy as np
import psycopg
import pytest
from langchain_core.documents import Document

from expense_ai.chunker import DEFAULT_CHUNK_SIZE, chunk_docs, make_splitter
from expense_ai.corpus import EMBEDDING_DIM, CorpusRow
from expense_ai.pgvector_loader import content_sha, filter_unchanged, load_rows


def test_make_splitter_invalid_overlap_raises() -> None:
    with pytest.raises(ValueError):
        make_splitter(chunk_size=100, overlap=200)


def test_chunk_ids_stable_and_monotonic() -> None:
    text = "This is a test sentence about expense tracking and deduction categories. " * 75
    doc = Document(page_content=text, metadata={"doc_id": "stable-test-doc"})
    chunks = chunk_docs([doc])
    assert len(chunks) > 1
    for i, chunk in enumerate(chunks):
        assert chunk.metadata["chunk_ordinal"] == i
        assert chunk.metadata["chunk_id"] == f"chunk-stable-test-doc-p{i}"


def test_average_chunk_length_within_range() -> None:
    base = (
        "Expense report for Q3 2024 includes vendor invoices, receipts, and tax deductions. "
        "Each line item is categorized by merchant, amount, and deductibility status. "
    )
    text = base * 55
    doc = Document(page_content=text, metadata={"doc_id": "avg-test-doc"})
    chunks = chunk_docs([doc])
    assert len(chunks) >= 2
    avg_len = sum(len(c.page_content) for c in chunks) / len(chunks)
    assert 400 <= avg_len <= 950, f"Average chunk length {avg_len:.1f} out of [400, 950]"


@pytest.mark.integration
def test_filter_unchanged_skips_known_rows(pg_dsn: str) -> None:
    text = "Deterministic content for re-embed gate test fixture A"
    hash_val = content_sha(text)
    row = CorpusRow(
        doc_id="gate-test-doc-001",
        chunk_idx=0,
        chunk_text=text,
        embedding=np.zeros(EMBEDDING_DIM, dtype=np.float32),
        model_version="gate-model-v1",
        tenant_id="tenant-a",
        content_hash=hash_val,
    )
    load_rows(pg_dsn, [row])

    with psycopg.connect(pg_dsn) as conn:
        unchanged = filter_unchanged(conn, [("gate-test-doc-001", 0, "gate-model-v1", hash_val)])
    assert ("gate-test-doc-001", 0, "gate-model-v1") in unchanged

    with psycopg.connect(pg_dsn) as conn:
        still_changed = filter_unchanged(
            conn, [("gate-test-doc-001", 0, "gate-model-v1", "wrong-hash-value")]
        )
    assert ("gate-test-doc-001", 0, "gate-model-v1") not in still_changed


# confirm defaults still hold
def test_default_chunk_size_value() -> None:
    assert DEFAULT_CHUNK_SIZE == 900
