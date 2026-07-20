# expense-mcp-server/PROMPT_JOURNAL.md

## W7 D4 transcripts

### 1. Tool descriptions for orders.create_refund

**Prompt to Claude:**

> Write a description for an MCP tool `orders.create_refund` that takes
> (order_id, amount Decimal, reason, tenant_id, idempotency_key UUID).
> The description must tell the LLM client WHEN to call this tool, WHEN
> NOT to call it, what scope the caller JWT needs, and one concrete
> example. The tool is idempotent on idempotency_key.

**Raw Claude response (excerpt):**

> "Apply a refund to an existing order. Idempotent: pass the same
> idempotency_key (UUID v4) on retries and the server returns the
> original outcome without double-debiting. Use this when the user
> explicitly asks to refund, credit back, or reverse a charge on an
> order; do NOT use it for partial cancellations or order edits. ..."

**Used as is / Modified / Rejected:** Modified twice. First pass used a
lowercase "do NOT" which failed the routing-quality gate test in
`tests/test_tool_descriptions.py` (`assert "Do NOT" in desc`, literal
case match) -- capitalized it. Second pass: the same gate requires the
description to end in a concrete `Example: ...` clause matched by
regex; `rag.retrieve_and_generate`'s description had none, so one was
added (`question='What is the per-diem limit...'` etc.) after the test
caught it failing.

### 2. FastMCP lifespan + httpx client + structured logging

**Prompt to Claude:** Write the FastMCP `app.py` lifespan: open a shared
`httpx.AsyncClient` for calls to expense-orders, expose it via a
dataclass context object, and pin all logging to stderr so JSON-RPC
frames on stdout stay clean.

**Raw Claude response (excerpt):** Initial version also opened
`psycopg.connect(...)`, a Redis client, and an Anthropic client eagerly
in the same lifespan, alongside the httpx client, for
`rag.retrieve_and_generate`'s dependencies.

**Used as is / Modified / Rejected:** Rejected the eager-open design.
`psycopg.connect()` is synchronous and connects immediately, unlike
`httpx.AsyncClient` (lazy); with no live Postgres available, this would
crash the *entire* server at startup, taking down `orders.get_order` and
`llm.chat` too even though neither needs Postgres. Moved pg/redis/
anthropic client construction into `tools/rag.py`, built lazily and
cached at module scope on first RAG call instead.

### 3. Testcontainers E2E + idempotent refund assertion

**Prompt to Claude:** Write `test_e2e_mcp_to_spring.py`: Postgres +
`uptimecrew/expense-orders:w3d1` + the MCP server subprocess, asserting
`tools/list` returns all 4 tools, `orders.get_order` returns the seeded
order, and `orders.create_refund` called twice with the same
`idempotency_key` returns the same `refund_id`.

**Raw Claude response (excerpt):** Matched the assignment's reference
structure closely: session-scoped `postgres`/`orders_svc`/`mcp_server`
fixtures, a small `_rpc` JSON-RPC helper, three test functions.

**Used as is / Modified / Rejected:** Used as is for the test logic
itself. Could not be run to green in this environment: Colima's
containerd store is erroring (`blob ... expected at ... input/output
error`) because the host machine is at ~100% disk capacity -- confirmed
via `df -h` and reproduced identically during the Task 3 Docker rebuild.
The test file, fixtures, and JSON-RPC protocol handling were verified
by code review and by the fact that the *same* subprocess/JSON-RPC
machinery is proven working in `test_smoke_stdio.py`, which does run
green locally (100 tools/list+tools/call pairs, real `_map_http` code
round-trip verified). Documented as a known environment blocker rather
than silently skipped or marked passing.
