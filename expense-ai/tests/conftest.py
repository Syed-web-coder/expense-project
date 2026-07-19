from __future__ import annotations

from collections.abc import Generator
from pathlib import Path

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer  # type: ignore[import-untyped]

PG_IMAGE = "pgvector/pgvector:pg16"
_SQL_DIR = Path(__file__).parent.parent / "sql"
_DDL_V001 = _SQL_DIR / "V001__doc_chunks.sql"
_DDL_V002 = _SQL_DIR / "V002__rag2_metadata_and_partial_indexes.sql"


def _split_sql(sql: str) -> list[str]:
    return [s.strip() for s in sql.split(";") if s.strip()]


@pytest.fixture(scope="session")
def pg_dsn() -> Generator[str, None, None]:
    with PostgresContainer(PG_IMAGE, driver=None) as pg:
        dsn = pg.get_connection_url().replace("postgresql+psycopg2://", "postgresql://")
        with psycopg.connect(dsn) as conn:
            conn.execute(_DDL_V001.read_text())
            conn.commit()
        # CREATE INDEX CONCURRENTLY cannot run inside a transaction; use autocommit
        with psycopg.connect(dsn, autocommit=True) as conn:
            for stmt in _split_sql(_DDL_V002.read_text()):
                conn.execute(stmt)
        yield dsn
