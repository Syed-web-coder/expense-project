# Build context: expense-project/ (parent of both expense-agent-svc/ and expense-ai/)
# docker build -f expense-agent-svc/Dockerfile -t expense-agent-svc .
FROM python:3.12-slim AS base

RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir uv

WORKDIR /app

# Copy path dependency first (expense-ai is a local uv path dep)
COPY expense-ai/ ./expense-ai/

# Copy the service
COPY expense-agent-svc/pyproject.toml expense-agent-svc/uv.lock* ./expense-agent-svc/
COPY expense-agent-svc/src/ ./expense-agent-svc/src/

WORKDIR /app/expense-agent-svc

RUN uv sync --frozen --no-dev

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8080/healthz || exit 1

EXPOSE 8080

CMD ["uv", "run", "uvicorn", "expense_agent_svc.app:app", "--host", "0.0.0.0", "--port", "8080"]
