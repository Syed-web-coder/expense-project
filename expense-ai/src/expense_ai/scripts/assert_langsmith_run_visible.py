"""Verify that a retrieve_chunks run appears in LangSmith.

Usage:
    uv run python -m expense_ai.scripts.assert_langsmith_run_visible
"""

from __future__ import annotations

import os
import sys
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path

import numpy as np
import psycopg
from testcontainers.postgres import PostgresContainer  # type: ignore[import-untyped]

from expense_ai.corpus import EMBEDDING_DIM, MODEL_NAME, CorpusRow
from expense_ai.pgvector_loader import load_rows
from expense_ai.rag import retrieve_chunks

_PG_IMAGE = "pgvector/pgvector:pg16"
_DDL_FILE = Path(__file__).resolve().parents[3] / "sql" / "V001__doc_chunks.sql"
_N_SEED = 10
_TENANT = "tenant-a"


def _make_seed_rows() -> list[CorpusRow]:
    rng = np.random.default_rng(42)
    return [
        CorpusRow(
            doc_id=f"assert-seed-{i}",
            chunk_idx=0,
            chunk_text=f"Schedule C deduction reference chunk {i}: meals, mileage, home office.",
            embedding=rng.random(EMBEDDING_DIM).astype(np.float32),
            model_version=MODEL_NAME,
            tenant_id=_TENANT,
        )
        for i in range(_N_SEED)
    ]


def main() -> None:
    api_key = os.environ.get("LANGSMITH_API_KEY") or os.environ.get("EXPENSE_AI_LANGSMITH_API_KEY")
    project = (
        os.environ.get("LANGSMITH_PROJECT")
        or os.environ.get("EXPENSE_AI_LANGSMITH_PROJECT")
        or "expense-ai-dev"
    )

    if not api_key:
        print(
            "ERROR: set LANGSMITH_API_KEY or EXPENSE_AI_LANGSMITH_API_KEY",
            file=sys.stderr,
        )
        sys.exit(1)

    os.environ["LANGSMITH_API_KEY"] = api_key
    os.environ["LANGSMITH_PROJECT"] = project

    if not _DDL_FILE.exists():
        print(f"ERROR: DDL file not found: {_DDL_FILE}", file=sys.stderr)
        sys.exit(1)

    with PostgresContainer(_PG_IMAGE, driver=None) as pg:
        dsn = pg.get_connection_url().replace("postgresql+psycopg2://", "postgresql://")

        with psycopg.connect(dsn) as conn:
            conn.execute(_DDL_FILE.read_text())
            conn.commit()

        load_rows(dsn, _make_seed_rows())

        before = datetime.now(tz=UTC)
        retrieve_chunks(dsn, "What are the Schedule C meal deduction limits?", k=3)

        from langsmith import Client

        client = Client()
        try:
            client.flush()
        except Exception:
            time.sleep(3)

        cutoff = before - timedelta(seconds=30)
        runs = list(
            client.list_runs(
                project_name=project,
                filter='eq(name, "expense_ai.retrieve_chunks")',
                start_time=cutoff,
                limit=10,
            )
        )

    if runs:
        print(
            f"SUCCESS: found {len(runs)} run(s) named 'expense_ai.retrieve_chunks' "
            f"in LangSmith project '{project}'"
        )
        sys.exit(0)
    else:
        print(
            f"FAILURE: no runs named 'expense_ai.retrieve_chunks' found in project "
            f"'{project}' within the last 10 minutes",
            file=sys.stderr,
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
