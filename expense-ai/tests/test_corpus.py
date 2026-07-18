from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from numpy.typing import NDArray

from expense_ai.corpus import EMBEDDING_DIM, CorpusRow, embed_dataframe, load_corpus

FIXTURE = Path(__file__).parent / "fixtures" / "corpus_seed.jsonl"


class _FakeModel:
    """Deterministic fake encoder that returns float64 so we can verify the cast."""

    def encode(
        self,
        texts: list[str],
        batch_size: int = 64,
        normalize_embeddings: bool = True,
        convert_to_numpy: bool = True,
    ) -> NDArray[np.float64]:
        rng = np.random.default_rng(42)
        return rng.random((len(texts), EMBEDDING_DIM), dtype=np.float64)


def _seed_df() -> pd.DataFrame:
    return pd.read_json(FIXTURE, lines=True)


def test_load_corpus_dedup() -> None:
    base = _seed_df().head(5)
    duplicate = pd.concat([base, base.head(2)], ignore_index=True)

    tmp = Path(__file__).parent / "fixtures" / "_dedup_test.jsonl"
    duplicate.to_json(tmp, orient="records", lines=True)

    try:
        df = load_corpus(tmp)
        assert len(df) == 5, f"Expected 5 unique rows, got {len(df)}"
        assert df.duplicated(subset=["doc_id", "chunk_idx"]).sum() == 0
    finally:
        tmp.unlink(missing_ok=True)


def test_load_corpus_length_bounds_rejected(tmp_path: Path) -> None:
    rows = [
        {"doc_id": "a", "chunk_idx": 0, "chunk_text": "", "tenant_id": "t"},
        {"doc_id": "b", "chunk_idx": 0, "chunk_text": "x" * 8001, "tenant_id": "t"},
        {"doc_id": "c", "chunk_idx": 0, "chunk_text": "valid text", "tenant_id": "t"},
    ]
    df = pd.DataFrame(rows)
    p = tmp_path / "bounds.jsonl"
    df.to_json(p, orient="records", lines=True)

    result = load_corpus(p)
    assert len(result) == 1
    assert result.iloc[0]["doc_id"] == "c"


def test_load_corpus_unsupported_extension(tmp_path: Path) -> None:
    p = tmp_path / "data.csv"
    p.write_text("col1,col2\na,b\n")
    with pytest.raises(ValueError, match="Unsupported file extension"):
        load_corpus(p)


def test_embed_dataframe_dtype_and_shape() -> None:
    df = _seed_df().head(10)
    rows = embed_dataframe(df, model=_FakeModel())

    assert len(rows) == 10
    for row in rows:
        assert isinstance(row, CorpusRow)
        assert row.embedding.dtype == np.float32, f"Expected float32, got {row.embedding.dtype}"
        assert row.embedding.shape == (EMBEDDING_DIM,), (
            f"Expected ({EMBEDDING_DIM},), got {row.embedding.shape}"
        )
