from __future__ import annotations

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="EXPENSE_AGENT_",
        extra="forbid",
    )

    postgres_url: str = "postgresql://postgres:postgres@localhost:5432/postgres"
    anthropic_api_key: SecretStr | None = None
    use_fake_llm: bool = False
    langsmith_api_key: SecretStr | None = None
    langsmith_project: str = "expense-agent-svc-dev"
    mcp_sse_url: str = "http://localhost:9000/sse"
    request_budget_usd_e5: int = 25000
