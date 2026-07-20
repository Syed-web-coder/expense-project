# expense-mcp-server/src/expense_mcp_server/settings.py
"""All env-driven config in one place; prefix EXPENSE_MCP_.
"""
from __future__ import annotations

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="EXPENSE_MCP_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    orders_svc_url: str = Field(default="https://expense-orders.internal")
    llm_proxy_url: str = Field(default="https://llm-proxy.internal")
    langsmith_project: str = Field(default="expense-mcp-server")
    tool_timeout_default_s: float = Field(default=5)
    tool_timeout_rag_s: float = Field(default=30)
    # The bearer JWT used to call the W3 D1 services. In stdio mode it
    # comes from the Claude Desktop launcher's env; in SSE mode it comes
    # off the incoming Authorization header (see transports/sse.py).
    bearer_jwt: str = Field(default="")
    # RAG dependencies for rag.retrieve_and_generate (calls expense_ai in-process).
    postgres_dsn: str = Field(default="", description="psycopg DSN for the pgvector corpus.")
    redis_url: str = Field(default="redis://localhost:6379")
    anthropic_api_key: str = Field(default="")
    # SSE transport host/port (EXPENSE_MCP_HOST / EXPENSE_MCP_PORT).
    host: str = Field(default="0.0.0.0")
    port: int = Field(default=8080)
    # JWT validation on the SSE handshake: defensive duplicate of the
    # W3 D1 Java-side check, done locally against the same JWKS.
    jwks_url: str = Field(default="https://expense-orders.internal/.well-known/jwks.json")
    jwt_audience: str = Field(default="expense-mcp-server")
