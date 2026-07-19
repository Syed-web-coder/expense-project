# expense-mcp-server/src/expense_mcp_server/tools/orders.py
"""orders.* tools: typed Pydantic v2 args, Decimal for money,
idempotency key on the write, structured McpError on every failure.

NOTE: no `from __future__ import annotations` here on purpose. FastMCP
dynamically builds a Pydantic wrapper model from the handler signature
in a different module; postponed (string) annotations can't be resolved
back to this module's classes, which breaks tool registration.
"""

from decimal import Decimal
from uuid import UUID

from langsmith import traceable
from mcp import McpError
from mcp.types import ErrorData
from pydantic import BaseModel, ConfigDict, Field

from expense_mcp_server.app import mcp

# ---- Input schemas ---------------------------------------------------------

class GetOrderArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")
    order_id: str = Field(min_length=1, description="Order id, e.g. ord-synth-9001.")
    tenant_id: str = Field(pattern=r"^tenant-[abc]$")

class CreateRefundArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")
    order_id: str = Field(min_length=1)
    amount: Decimal = Field(gt=Decimal("0"), decimal_places=2,
        description="Refund amount; serialised as string in JSON. Max 2 decimal places (cents).")
    reason: str = Field(min_length=4, max_length=200)
    tenant_id: str = Field(pattern=r"^tenant-[abc]$")
    idempotency_key: UUID = Field(description="UUID v4; required so retries are safe.")

# ---- Output schemas (pre-shape; see Likely Sticking Points) ----------------

class OrderView(BaseModel):
    model_config = ConfigDict(extra="forbid")
    order_id: str
    tenant_id: str
    total: Decimal
    status: str

class RefundView(BaseModel):
    model_config = ConfigDict(extra="forbid")
    order_id: str
    refund_id: str
    amount: Decimal
    reason: str
    status: str

# ---- HTTP-to-McpError mapping (single source of truth) ---------------------

def _map_http(status: int, body: str) -> McpError:
    code = {400: 4001, 401: 4030, 403: 4030, 404: 4040, 409: 4090, 429: 4290}.get(status, 5030)
    return McpError(ErrorData(code=code, message=body[:200]))

# ---- Tool handlers ---------------------------------------------------------

_DESC_GET_ORDER = (
    "Fetch a single order by id for the caller tenant. Returns the order "
    "id, tenant id, total (Decimal-as-string), and status. Use this when "
    "the user asks to look up, check, view, or read the state of an "
    "existing order. Do NOT use this to modify the order; for refunds "
    "call orders.create_refund. Example: order_id='ord-synth-9001', "
    "tenant_id='tenant-a' returns the order with status='paid'."
)

@mcp.tool(name="orders.get_order", description=_DESC_GET_ORDER)
@traceable(name="orders.get_order", project_name="expense-mcp-server")
async def orders_get_order(args: GetOrderArgs) -> dict[str, object]:
    ctx = mcp.get_context().request_context.lifespan_context
    r = await ctx.http.get(
        f"/orders/{args.order_id}",
        headers={
            "Authorization": f"Bearer {ctx.settings.bearer_jwt}",
            "X-Tenant": args.tenant_id,
        },
    )
    if r.status_code != 200:
        raise _map_http(r.status_code, r.text)
    return OrderView.model_validate(r.json()).model_dump(mode="json")

_DESC_CREATE_REFUND = (
    "Apply a refund to an existing order. Idempotent: pass the same "
    "idempotency_key (UUID v4) on retries and the server returns the "
    "original outcome without double-debiting. Use this when the user "
    "explicitly asks to refund, credit back, or reverse a charge on an "
    "order; do NOT use it for partial cancellations or order edits. "
    "Returns the refund id and the original amount and reason. Requires "
    "the caller JWT to carry 'orders.write' scope (verified by expense-orders.) "
    "Example: order_id='ord-synth-9001', amount='10.00', "
    "reason='duplicate', tenant_id='tenant-a' returns the refund view."
)

@mcp.tool(name="orders.create_refund", description=_DESC_CREATE_REFUND)
@traceable(name="orders.create_refund", project_name="expense-mcp-server")
async def orders_create_refund(args: CreateRefundArgs) -> dict[str, object]:
    ctx = mcp.get_context().request_context.lifespan_context
    payload = {
        "orderId": args.order_id,
        "amount": str(args.amount),
        "reason": args.reason,
        "idempotencyKey": str(args.idempotency_key),
    }
    r = await ctx.http.post(
        f"/orders/{args.order_id}/refunds",
        json=payload,
        headers={
            "Authorization": f"Bearer {ctx.settings.bearer_jwt}",
            "X-Tenant": args.tenant_id,
            "Idempotency-Key": str(args.idempotency_key),
        },
    )
    if r.status_code != 200:
        raise _map_http(r.status_code, r.text)
    return RefundView.model_validate(r.json()).model_dump(mode="json")
