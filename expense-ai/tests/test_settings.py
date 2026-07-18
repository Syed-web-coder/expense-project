import pytest

from expense_ai.settings import ExpenseAISettings


def test_settings_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXPENSE_AI_API_KEY", "test-secret-key")
    monkeypatch.setenv("EXPENSE_AI_LLM_PROXY_URL", "http://test-proxy:9090")
    monkeypatch.setenv("EXPENSE_AI_REQUEST_TIMEOUT_S", "30.0")
    monkeypatch.setenv("EXPENSE_AI_LOG_LEVEL", "DEBUG")

    settings = ExpenseAISettings()

    assert settings.api_key.get_secret_value() == "test-secret-key"
    assert settings.llm_proxy_url == "http://test-proxy:9090"
    assert settings.request_timeout_s == pytest.approx(30.0)
    assert settings.log_level == "DEBUG"


def test_settings_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXPENSE_AI_API_KEY", "any-key")

    settings = ExpenseAISettings()

    assert settings.llm_proxy_url == "http://localhost:8080"
    assert settings.request_timeout_s == pytest.approx(10.0)
    assert settings.log_level == "INFO"


def test_settings_api_key_is_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXPENSE_AI_API_KEY", "super-secret")

    settings = ExpenseAISettings()

    assert "super-secret" not in repr(settings)
    assert settings.api_key.get_secret_value() == "super-secret"
