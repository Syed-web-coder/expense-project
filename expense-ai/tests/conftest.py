from __future__ import annotations

from collections.abc import Generator
from pathlib import Path

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer  # type: ignore[import-untyped]

PG_IMAGE = "pgvector/pgvector:pg16"
_DDL_FILE = Path(__file__).parent.parent / "sql" / "V001__doc_chunks.sql"


@pytest.fixture(scope="session")
def pg_dsn() -> Generator[str, None, None]:
    with PostgresContainer(PG_IMAGE, driver=None) as pg:
        dsn = pg.get_connection_url().replace("postgresql+psycopg2://", "postgresql://")
        with psycopg.connect(dsn) as conn:
            conn.execute(_DDL_FILE.read_text())
            conn.commit()
        yield dsn
