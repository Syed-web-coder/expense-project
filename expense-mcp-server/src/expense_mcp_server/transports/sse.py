# expense-mcp-server/src/expense_mcp_server/transports/sse.py
"""HTTP+SSE entry point for the W7 D5 agent and any remote MCP client.

NOTE ON DEVIATION FROM THE CURRICULUM REFERENCE: the installed mcp==1.28.1
FastMCP.run() does not accept host/port kwargs for the sse transport (they
live on FastMCP.settings, set at construction time in this SDK version).
Since we also need to wrap the app with our own JWT auth middleware (see
auth.py -- the built-in TokenVerifier/AuthSettings machinery is for
delegating to a full external OAuth 2.1 authorization server, which is
more than this assignment needs), we grab the raw Starlette app via
mcp.sse_app(), wrap it, and serve it ourselves with uvicorn instead of
calling mcp.run(transport="sse").
"""
from __future__ import annotations

import uvicorn

from expense_mcp_server.app import mcp
from expense_mcp_server.auth import wrap_with_auth
from expense_mcp_server.settings import Settings
from expense_mcp_server.tools import _resources, llm, orders, rag  # noqa: F401  (registers decorators)


def main() -> None:
    settings = Settings()
    app = mcp.sse_app()
    authed_app = wrap_with_auth(app, jwks_url=settings.jwks_url, audience=settings.jwt_audience)
    uvicorn.run(authed_app, host=settings.host, port=settings.port)


if __name__ == "__main__":
    main()
