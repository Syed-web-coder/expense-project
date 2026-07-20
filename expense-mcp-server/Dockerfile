# expense-mcp-server/Dockerfile
# Slim image serving the HTTP+SSE transport behind a ClusterIP service
# for the W7 D5 agent. stdio mode is launched directly by Claude Desktop
# and does not run in this image.
#
# BUILD FROM THE REPO ROOT (expense-project/), not this directory:
# expense-ai is a sibling directory pulled in as a path dependency
# ({root:uri}/../expense-ai in pyproject.toml), so it must be inside the
# Docker build context.
#
#   docker build -f expense-mcp-server/Dockerfile -t expense-mcp-server .
#
FROM python:3.12-slim

WORKDIR /app

RUN pip install --no-cache-dir uv

# Order matters for layer caching: expense-ai changes less often than
# expense-mcp-server during normal iteration.
COPY expense-ai /expense-ai
COPY expense-mcp-server /app

RUN uv pip install --system --no-cache .

ENV EXPENSE_MCP_HOST=0.0.0.0
ENV EXPENSE_MCP_PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python healthcheck.py

ENTRYPOINT ["expense-mcp-server-sse"]
