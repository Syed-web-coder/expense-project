# expense-mcp-server/src/expense_mcp_server/app.py
"""FastMCP entry: shared lifespan opens the httpx client and the W7 D3
RAG handle; every tool reads from the lifespan context.
"""
from __future__ import annotations

import logging
import sys
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass

import httpx
import structlog
from mcp.server.fastmcp import FastMCP

from expense_ai.rag import retrieve_and_generate as _rag_fn
from expense_mcp_server.settings import Settings

# Logging MUST go to stderr; stdout carries JSON-RPC frames on stdio.
logging.basicConfig(level=logging.INFO, stream=sys.stderr, format="%(message)s")
structlog.configure(
    processors=[
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.JSONRenderer(),
    ],
    logger_factory=structlog.PrintLoggerFactory(file=sys.stderr),
)
log = structlog.get_logger("expense-mcp-server")


@dataclass
class AppCtx:
    http: httpx.AsyncClient
    rag_fn: object
    settings: Settings


@asynccontextmanager
async def lifespan(_: FastMCP) -> AsyncIterator[AppCtx]:
    s = Settings()
    # Shared HTTP client to the W3 D1 Spring Boot services. The JWT
    # is added per call from the caller context; we keep one pool.
    client = httpx.AsyncClient(
        base_url=s.orders_svc_url,
        timeout=httpx.Timeout(s.tool_timeout_default_s, connect=2.0),
    )
    # NOTE: pg/redis/anthropic clients for rag.retrieve_and_generate are
    # deliberately NOT opened here. Unlike httpx.AsyncClient (lazy), a
    # psycopg.connect() call connects immediately and would crash the
    # whole server at startup if Postgres isn't reachable -- taking down
    # orders.get_order and llm.chat too, which don't need Postgres at all.
    # tools/rag.py lazily builds and caches these on first RAG call instead.
    log.info("lifespan.start", orders_svc=s.orders_svc_url)
    try:
        yield AppCtx(http=client, rag_fn=_rag_fn, settings=s)
    finally:
        await client.aclose()
        log.info("lifespan.stop")


# NOTE: installed mcp==1.28.1 dropped the `version` kwarg from FastMCP.__init__
# (curriculum reference targeted an older SDK). Name + lifespan still apply.
mcp = FastMCP(name="expense-mcp-server", lifespan=lifespan)
