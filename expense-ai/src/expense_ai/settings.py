from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class ExpenseAISettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="EXPENSE_AI_", extra="forbid")

    api_key: SecretStr
    llm_proxy_url: str = "http://localhost:8080"
    request_timeout_s: float = 10.0
    log_level: str = "INFO"
    langsmith_api_key: SecretStr | None = None
    langsmith_project: str = "expense-ai-dev"
    pg_dsn: str | None = None
    anthropic_api_key: SecretStr | None = None
