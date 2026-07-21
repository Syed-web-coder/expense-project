from __future__ import annotations

import asyncio
import sys
from collections.abc import AsyncGenerator, Generator
from typing import Any

import psycopg
import pytest
from psycopg.rows import dict_row
from testcontainers.postgres import PostgresContainer

# psycopg AsyncConnection requires SelectorEventLoop on Windows
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


@pytest.fixture(scope="session")
def pg_container() -> Generator[PostgresContainer, None, None]:
    with PostgresContainer("postgres:16") as container:
        yield container


@pytest.fixture(scope="session")
def pg_dsn(pg_container: PostgresContainer) -> str:
    # driver=None → plain "postgresql://" URL, compatible with psycopg3
    url: str = pg_container.get_connection_url(driver=None)
    return url


@pytest.fixture()
async def checkpointer(pg_dsn: str) -> AsyncGenerator[Any, None]:
    """AsyncPostgresSaver backed by testcontainers Postgres.

    Using AsyncPostgresSaver (not PostgresSaver) so ainvoke works correctly —
    PostgresSaver is sync-only and raises NotImplementedError on aget_tuple.
    """
    from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

    async with AsyncPostgresSaver.from_conn_string(pg_dsn) as saver:
        await saver.setup()
        yield saver


@pytest.fixture(scope="session")
def sync_checkpointer(pg_dsn: str) -> Generator[Any, None, None]:
    """Sync PostgresSaver for graph-compile-only tests (no ainvoke)."""
    from langgraph.checkpoint.postgres import PostgresSaver

    conn = psycopg.connect(pg_dsn, autocommit=True, row_factory=dict_row)
    saver = PostgresSaver(conn)
    saver.setup()
    yield saver
    conn.close()
