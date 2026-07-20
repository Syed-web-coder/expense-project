# expense-mcp-server/src/expense_mcp_server/tools/llm.py
"""llm.chat: thin adapter over the W3 D1 llm-proxy /v1/chat/completions
endpoint. JWT pass-through; upstream 429 maps to McpError(4290) so the
W7 D5 agent can apply its exponential backoff.

NOTE: no `from __future__ import annotations` here, same reason as
tools/orders.py -- FastMCP needs to resolve real (not string) annotations
when it builds the tool's Pydantic wrapper model.
"""
from langsmith import traceable
from mcp import McpError
from mcp.types import ErrorData
from pydantic import BaseModel, ConfigDict, Field

from expense_mcp_server.app import mcp
from expense_mcp_server.observability import observe
from expense_mcp_server.tools.orders import _map_http

# ---- Input schemas ---------------------------------------------------------

class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")
    role: str = Field(pattern=r"^(system|user|assistant)$")
    content: str = Field(min_length=1)

class ChatArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")
    messages: list[ChatMessage] = Field(min_length=1)
    max_tokens: int = Field(gt=0, le=4096)
    tenant_id: str = Field(pattern=r"^tenant-[abc]$")

# ---- Tool handler -----------------------------------------------------------

_DESC_CHAT = (
    "Send a chat completion request to the cost-tracked LLM gateway. Use "
    "this for general, ungrounded conversational or generative responses "
    "when the user's question does not need to be grounded in the "
    "tenant's document corpus (for that, use rag.retrieve_and_generate). "
    "Do NOT use this to look up or modify an order (use orders.get_order "
    "or orders.create_refund instead). Returns the "
    "upstream chat completion response. Example: messages=[{role='user', "
    "content='Summarize this expense policy in one sentence.'}], "
    "max_tokens=200, tenant_id='tenant-a'."
)

@mcp.tool(name="llm.chat", description=_DESC_CHAT)
@traceable(name="llm.chat", project_name="expense-mcp-server")
@observe("llm.chat")
async def llm_chat(args: ChatArgs) -> dict[str, object]:
    ctx = mcp.get_context().request_context.lifespan_context
    payload = {
        "messages": [m.model_dump() for m in args.messages],
        "max_tokens": args.max_tokens,
    }
    r = await ctx.http.post(
        f"{ctx.settings.llm_proxy_url}/v1/chat/completions",
        json=payload,
        headers={
            "Authorization": f"Bearer {ctx.settings.bearer_jwt}",
            "X-Tenant": args.tenant_id,
        },
    )
    if r.status_code == 429:
        raise McpError(ErrorData(code=4290, message="[4290] llm-proxy rate limited"))
    if r.status_code != 200:
        raise _map_http(r.status_code, r.text)
    return dict(r.json())
