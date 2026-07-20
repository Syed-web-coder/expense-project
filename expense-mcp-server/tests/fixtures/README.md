# Replay fixtures

Each `*.json` file here is one deterministic, offline replay case for
`scripts/replay.py`: a tool name, its input `args`, and a canned upstream
HTTP response the fixture's underlying dependency should return (mocked
via respx, since there's no live orders-svc / llm-proxy to call yet).

**rag.retrieve_and_generate is intentionally NOT covered here.** Its real
dependencies (psycopg, Redis, a live Anthropic key) can't be mocked the
same way respx mocks httpx, and none of those backends exist yet in any
environment (same underlying gap as the missing EKS cluster from W6/W7:
the infra this pipeline assumes was never provisioned). Once a real or
containerized Postgres/Redis is available, add rag fixtures + extend
replay.py's dependency-injection to match.
