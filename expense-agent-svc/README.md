# expense-agent-svc

LangGraph multi-agent capstone for the Expense Tracking Platform (Week 7 Day 5).

Three-node graph — `retrieval_agent → synthesis_agent` and/or `api_agent → synthesis_agent` —
with PostgresSaver checkpointing, per-request budget enforcement, SSE streaming, and a trajectory
eval gate.

## What it does

| Node | Purpose |
|------|---------|
| `retrieval_agent` | Fetches policy docs from pgvector via `expense-ai.rag.retrieve_chunks` |
| `api_agent` | Calls the `expense-mcp-server` tool-use loop (orders, refunds) |
| `synthesis_agent` | Produces a structured `FinalAnswer` with citations via instructor + Anthropic |

The `supervisor` routes questions to one or both agent branches based on keyword matching. Both
branches may run in parallel via `Send()` and their results are merged by reducers before synthesis.

## Setup

```bash
cd expense-agent-svc
uv sync
cp .env.example .env   # fill in real values
```

Docker is required for tests (testcontainers Postgres):

```bash
docker pull postgres:16
```

## Running

```bash
uv run expense-agent-svc        # production (uvicorn on :8080)
uv run uvicorn expense_agent_svc.app:app --reload --port 8080   # dev
```

## Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `EXPENSE_AGENT_POSTGRES_URL` | `postgresql://postgres:postgres@localhost:5432/postgres` | Checkpoint store |
| `EXPENSE_AGENT_ANTHROPIC_API_KEY` | — | Anthropic API (synthesis + api_agent) |
| `EXPENSE_AGENT_LANGSMITH_API_KEY` | — | LangSmith tracing |
| `EXPENSE_AGENT_LANGSMITH_PROJECT` | `expense-agent-svc-dev` | LangSmith project name |
| `EXPENSE_AGENT_MCP_SSE_URL` | `http://localhost:9000/sse` | MCP SSE server |
| `EXPENSE_AGENT_REQUEST_BUDGET_USD_E5` | `25000` | Per-request ceiling (1e-5 USD) |

## API

```
GET  /healthz                           → {"status": "ok"}
POST /v1/chat/stream                    → text/event-stream
     body: {question, tenant_id, thread_id}
```

**SSE protocol:**

```
0:{"delta": "..."}          — streaming token from synthesis LLM
2:{"finalAnswer": {...}}    — complete FinalAnswer (text, citations, confidence)
3:{"error": "..."}          — error frame (budget_exceeded | recursion_limit)
```

## Tests

```bash
uv run pytest -v                                          # all tests (requires Docker)
uv run pytest tests/test_budget_guard.py -v              # no Docker needed
uv run pytest tests/test_supervisor.py tests/test_deadline.py -v  # no Docker needed
```

## Eval gate

```bash
uv run python -m expense_agent_svc.scripts.eval           # run all 20 scenarios, print JSON
uv run python -m expense_agent_svc.scripts.eval --gate    # exit 1 if trajectory_match < 0.70
```

Results are written to `evals/last_run.json`.

## Architecture notes

- **Injectable clients** live in `config["configurable"]`, never in `AgentState`, because
  `PostgresSaver` serialises all state to JSON and `AsyncAnthropic` is not JSON-serialisable.
- **`Optional[RunnableConfig]`** is kept on all node signatures (not `RunnableConfig | None`)
  because LangGraph's signature inspector matches the string `"Optional[RunnableConfig]"`.
- **`visited_nodes`** uses `Annotated[list[str], operator.add]` so parallel branches accumulate
  their names without conflict.
- **BudgetGuard** uses integer 1e-5 USD arithmetic — no floats anywhere in the money path.
