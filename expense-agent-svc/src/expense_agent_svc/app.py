"""FastAPI application for the expense agent service.

Lifespan wires production clients (Postgres, Anthropic, MCP SSE).
Missing keys/services log a warning and leave the slot as None so that
local dev works without real infrastructure — nodes degrade gracefully
when clients are absent.
"""

from __future__ import annotations

import asyncio
import logging
import sys

# AsyncPostgresSaver needs SelectorEventLoop; Windows defaults to ProactorEventLoop.
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

from collections.abc import AsyncIterator
from contextlib import AsyncExitStack, asynccontextmanager
from typing import Any

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel

from expense_agent_svc.budgets import BudgetExceeded, BudgetGuard
from expense_agent_svc.graph import build_expense_agent_graph
from expense_agent_svc.settings import Settings
from expense_agent_svc.sse import event_stream

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = Settings()

    async with AsyncExitStack() as stack:
        # ── 1. Postgres checkpointer ──────────────────────────────────────────
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

        saver: Any = await stack.enter_async_context(
            AsyncPostgresSaver.from_conn_string(settings.postgres_url)
        )
        await saver.setup()
        graph = build_expense_agent_graph(settings, checkpointer=saver)

        # ── 2. Anthropic + instructor (guarded by key presence) ───────────────
        anthropic_client: Any = None
        instructor_client: Any = None
        if settings.anthropic_api_key is not None:
            try:
                import instructor as _instructor
                from anthropic import AsyncAnthropic

                anthropic_client = AsyncAnthropic(
                    api_key=settings.anthropic_api_key.get_secret_value()
                )
                instructor_client = _instructor.from_anthropic(anthropic_client)
            except Exception as exc:
                logger.warning("anthropic_init_failed: %s", exc)
        else:
            logger.warning("EXPENSE_AGENT_ANTHROPIC_API_KEY not set — local dev mode")

        # ── 3. MCP SSE session (guarded by URL availability) ─────────────────
        mcp_session: Any = None
        if settings.mcp_sse_url:
            try:
                from mcp import ClientSession
                from mcp.client.sse import sse_client

                read, write = await stack.enter_async_context(sse_client(settings.mcp_sse_url))
                session: ClientSession = await stack.enter_async_context(ClientSession(read, write))
                await session.initialize()
                mcp_session = session
            except Exception as exc:
                logger.warning("mcp_init_failed url=%s: %s", settings.mcp_sse_url, exc)

        app.state.graph = graph
        app.state.settings = settings
        app.state.anthropic_client = anthropic_client
        app.state.instructor_client = instructor_client
        app.state.mcp_session = mcp_session

        yield


app = FastAPI(title="Expense Agent Service", lifespan=lifespan)


@app.exception_handler(BudgetExceeded)
async def _budget_exceeded_handler(request: Request, exc: BudgetExceeded) -> JSONResponse:
    return JSONResponse(
        status_code=503,
        content={"error": "budget_exceeded", "detail": str(exc)},
        headers={"Retry-After": "30"},
    )


@app.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


class ChatRequest(BaseModel):
    question: str
    tenant_id: str
    thread_id: str


@app.post("/v1/chat/stream")
async def chat_stream(body: ChatRequest, request: Request) -> StreamingResponse:
    graph: Any = request.app.state.graph
    settings: Settings = request.app.state.settings

    extras: dict[str, Any] = {
        "__budget_guard": BudgetGuard(ceiling_usd_e5=settings.request_budget_usd_e5),
    }
    if request.app.state.anthropic_client is not None:
        extras["__anthropic"] = request.app.state.anthropic_client
    if request.app.state.instructor_client is not None:
        extras["__instructor"] = request.app.state.instructor_client
    if request.app.state.mcp_session is not None:
        extras["__mcp_session"] = request.app.state.mcp_session

    stream = event_stream(
        graph,
        body.question,
        body.tenant_id,
        body.thread_id,
        recursion_limit=25,
        configurable_extras=extras,
    )

    headers: dict[str, str] = {}
    try:
        from langsmith.run_trees import get_current_run_tree  # type: ignore[attr-defined]

        run = get_current_run_tree()
        if run is not None:
            headers["X-LangSmith-Trace-Id"] = str(run.id)
    except Exception:
        pass

    return StreamingResponse(
        stream,
        media_type="text/event-stream",
        headers=headers,
    )


def run() -> None:
    uvicorn.run("expense_agent_svc.app:app", host="0.0.0.0", port=8080, loop="asyncio")
