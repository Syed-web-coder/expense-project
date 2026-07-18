from __future__ import annotations

from pathlib import Path

import great_expectations as gx
import numpy as np
import pgvector.sqlalchemy  # noqa: F401  — registers vector type with SQLAlchemy
import pytest
from great_expectations.expectations.core.expect_column_value_lengths_to_be_between import (
    ExpectColumnValueLengthsToBeBetween,
)
from great_expectations.expectations.core.expect_column_values_to_not_be_null import (
    ExpectColumnValuesToNotBeNull,
)
from great_expectations.expectations.core.expect_table_row_count_to_be_between import (
    ExpectTableRowCountToBeBetween,
)
from numpy.typing import NDArray

from expense_ai.corpus import EMBEDDING_DIM, CorpusRow, embed_dataframe, load_corpus
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
        return rng.random((len(texts), EMBEDDING_DIM)).astype(np.float32)


@pytest.mark.integration
def test_gx_doc_chunks_suite(pg_dsn: str) -> None:
    # Seed 100+ chunks via load_corpus + fake encoder + load_rows
    df = load_corpus(FIXTURE)
    assert len(df) >= 100, f"Fixture must supply 100+ rows, got {len(df)}"

    rows: list[CorpusRow] = embed_dataframe(df, model=_FakeModel())
    load_rows(pg_dsn, rows)

    # GX 1.x SQLAlchemy needs the psycopg (v3) dialect prefix; psycopg2 is absent
    sa_dsn = pg_dsn.replace("postgresql://", "postgresql+psycopg://", 1)

    ctx = gx.get_context(mode="ephemeral")

    ds = ctx.data_sources.add_postgres(
        name="pg_testcontainer",
        connection_string=sa_dsn,
    )
    asset = ds.add_table_asset(name="doc_chunks_asset", table_name="doc_chunks")
    batch_def = asset.add_batch_definition_whole_table(name="full_table")

    suite = ctx.suites.add(gx.ExpectationSuite(name="doc_chunks_v1"))
    suite.add_expectation(ExpectColumnValuesToNotBeNull(column="doc_id"))
    suite.add_expectation(ExpectColumnValuesToNotBeNull(column="embedding"))
    suite.add_expectation(ExpectColumnValuesToNotBeNull(column="model_version"))
    suite.add_expectation(ExpectTableRowCountToBeBetween(min_value=100, max_value=10_000_000))
    suite.add_expectation(
        ExpectColumnValueLengthsToBeBetween(column="chunk_text", min_value=1, max_value=8000)
    )

    val_def = ctx.validation_definitions.add(
        gx.ValidationDefinition(name="doc_chunks_validation", data=batch_def, suite=suite)
    )

    checkpoint = ctx.checkpoints.add(
        gx.Checkpoint(name="doc_chunks_checkpoint", validation_definitions=[val_def])
    )

    result = checkpoint.run()
    assert result.success is True, f"GX checkpoint failed:\n{result}"
