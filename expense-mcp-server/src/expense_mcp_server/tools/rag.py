# expense-mcp-server/src/expense_mcp_server/tools/rag.py
"""rag.retrieve_and_generate: thin adapter over the W7 D3 pipeline.

The function lives in expense_ai.rag and is called in-process via a
worker thread. Its pg/redis/anthropic clients are built lazily and
cached at module scope on first call (NOT in the app lifespan) so a
missing Postgres/Redis at startup doesn't take down orders.get_order
and llm.chat, which don't need them.

DEVIATION FROM THE CURRICULUM REFERENCE (flagged, not hidden):
The real expense_ai.rag.retrieve_and_generate signature and return
shape differ from the reference used to write this assignment:
  - No `top_k` parameter exists upstream; retrieval depth is hardcoded
    internally (dense k=50, mmr k=20, rerank top_k=6). We can never
    return more than 6 citations regardless of the caller's top_k; we
    truncate client-side and say so in the tool description.
  - The return dict has "text" (not "answer") and citations shaped as
    {doc_id, chunk_text, tenant_id} -- no chunk_id or score. Our
    Citation/RagAnswer DTOs below reflect what's actually available
    rather than inventing fields the pipeline doesn't produce.
  - "coverage" isn't returned upstream either; we compute a proxy as
    len(citations) / requested top_k rather than fabricate a number.
"""
import asyncio

import anthropic
import psycopg
import redis as redis_module
from langsmith import traceable
from mcp import McpError
from mcp.types import ErrorData
from pydantic import BaseModel, ConfigDict, Field

from expense_mcp_server.app import mcp
from expense_mcp_server.observability import observe
from expense_mcp_server.settings import Settings

# ---- Lazily-built, cached RAG dependencies ---------------------------------

_pg_conn: psycopg.Connection | None = None
_redis_client: redis_module.Redis | None = None
_anthropic_client: anthropic.Anthropic | None = None


def _get_rag_deps(
    settings: Settings,
) -> tuple[psycopg.Connection, redis_module.Redis, anthropic.Anthropic]:
    global _pg_conn, _redis_client, _anthropic_client
    if _pg_conn is None:
        _pg_conn = psycopg.connect(settings.postgres_dsn)
    if _redis_client is None:
        _redis_client = redis_module.Redis.from_url(settings.redis_url)
    if _anthropic_client is None:
        _anthropic_client = anthropic.Anthropic(api_key=settings.anthropic_api_key)
    return _pg_conn, _redis_client, _anthropic_client


# ---- Input schema -----------------------------------------------------------

class RagArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")
    question: str = Field(min_length=2, max_length=2000)
    tenant_id: str = Field(pattern=r"^tenant-[abc]$")
    top_k: int = Field(default=6, ge=1, le=20)

# ---- Output schema (matches what expense_ai.rag actually returns) ----------

class Citation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    doc_id: str
    chunk_text: str

class RagAnswer(BaseModel):
    model_config = ConfigDict(extra="forbid")
    answer: str
    citations: list[Citation]
    coverage: float
    rerank_timed_out: bool

# ---- Tool handler -------------------------------------------------------

_DESC_RAG = (
    "Answer a question grounded in the tenant's document corpus using "
    "the W7 D3 retrieval pipeline (hybrid dense + sparse, MMR, "
    "cross-encoder rerank). Returns an answer string plus up to top_k "
    "citations with doc_id and a chunk_text excerpt, plus a coverage "
    "diagnostic and a rerank_timed_out flag. NOTE: the underlying "
    "pipeline reranks to a fixed top 6 chunks internally, so top_k "
    "values above 6 will not return more than 6 citations. Use this "
    "when the user asks for information that lives in the tenant's "
    "documents (policies, prior filings, indexed knowledge base). Do "
    "NOT use this for transactional reads (use orders.get_order) or "
    "generative chat without grounding (use llm.chat). Example: "
    "question='What is the per-diem limit for international travel?', "
    "tenant_id='tenant-a', top_k=6 returns an answer grounded in that "
    "tenant's indexed travel policy documents."
)

@mcp.tool(name="rag.retrieve_and_generate", description=_DESC_RAG)
@traceable(name="rag.retrieve_and_generate", project_name="expense-mcp-server")
@observe("rag.retrieve_and_generate")
async def rag_retrieve_and_generate(args: RagArgs) -> dict[str, object]:
    ctx = mcp.get_context().request_context.lifespan_context
    conn, r, ac = _get_rag_deps(ctx.settings)
    try:
        # Offload the sync RAG call to a worker thread so the event loop
        # is not blocked by the cross-encoder forward pass.
        result = await asyncio.wait_for(
            asyncio.to_thread(
                ctx.rag_fn,
                args.question,
                args.tenant_id,
                anthropic=ac,
                conn=conn,
                r=r,
            ),
            timeout=ctx.settings.tool_timeout_rag_s,
        )
    except asyncio.TimeoutError as exc:
        raise McpError(ErrorData(code=5040, message="[5040] rag timed out")) from exc

    citations = [
        Citation(doc_id=c["doc_id"], chunk_text=c["chunk_text"])
        for c in result["citations"][: args.top_k]
    ]
    answer = RagAnswer(
        answer=result["text"],
        citations=citations,
        coverage=len(citations) / args.top_k,
        rerank_timed_out=bool(result.get("rerank_timed_out", False)),
    )
    return answer.model_dump(mode="json")
