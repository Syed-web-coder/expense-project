from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from numpy.typing import NDArray

MODEL_NAME = "all-MiniLM-L6-v2"
EMBEDDING_DIM = 384

_REQUIRED_COLS = {"doc_id", "chunk_idx", "chunk_text", "tenant_id"}


def _empty_metadata() -> dict[str, Any]:
    return {}


@dataclass(frozen=True, slots=True)
class CorpusRow:
    doc_id: str
    chunk_idx: int
    chunk_text: str
    embedding: NDArray[np.float32]
    model_version: str
    tenant_id: str
    chunk_metadata: dict[str, Any] = field(default_factory=_empty_metadata)
    content_hash: str | None = None


def load_corpus(path: Path) -> pd.DataFrame:
    suffix = path.suffix.lower()
    if suffix == ".parquet":
        df = pd.read_parquet(path)
    elif suffix in {".jsonl", ".json"}:
        df = pd.read_json(path, lines=True)
    else:
        raise ValueError(f"Unsupported file extension: {suffix!r}")

    missing = _REQUIRED_COLS - set(df.columns)
    if missing:
        raise ValueError(f"Missing required columns: {missing}")

    df = df.drop_duplicates(subset=["doc_id", "chunk_idx"], keep="first")
    df = df.query("chunk_text.str.len() >= 1 and chunk_text.str.len() <= 8000")
    return df.reset_index(drop=True)


def embed_dataframe(
    df: pd.DataFrame,
    model: object | None = None,
    batch_size: int = 64,
) -> list[CorpusRow]:
    if model is None:
        from sentence_transformers import SentenceTransformer

        model = SentenceTransformer(MODEL_NAME)

    texts: list[str] = df["chunk_text"].tolist()
    # single batched call — no row-wise apply
    embeddings: NDArray[np.float32] = model.encode(  # type: ignore[union-attr]
        texts,
        batch_size=batch_size,
        normalize_embeddings=True,
        convert_to_numpy=True,
    ).astype(np.float32)

    rows: list[CorpusRow] = []
    for i, row in enumerate(df.itertuples(index=False)):
        rows.append(
            CorpusRow(
                doc_id=str(row.doc_id),
                chunk_idx=int(row.chunk_idx),  # type: ignore[arg-type]
                chunk_text=str(row.chunk_text),
                embedding=embeddings[i],
                model_version=MODEL_NAME,
                tenant_id=str(row.tenant_id),
            )
        )
    return rows
