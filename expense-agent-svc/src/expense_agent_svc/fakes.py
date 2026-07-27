"""Fake Anthropic/instructor clients for local dev and demo use.

These are the same fakes used in tests/test_integration.py, extracted here
so the FastAPI lifespan can inject them when EXPENSE_AGENT_USE_FAKE_LLM=true
and no real Anthropic key is configured. This lets the chatbot be wired and
exercised end-to-end (tool calls, streaming, citations) without live API
calls or a real key.

Responses are matched against a small set of preset question types (see
_PRESETS below) so a demo can show a few different-looking answers instead
of always returning the same canned line. The "today's date" preset is
computed from the real system clock — everything else (Costco visits,
transaction counts, totals) is a hardcoded string, not real ledger data.
Anything that doesn't match a preset falls back to the original default
response, which keeps tests/test_integration.py's existing fixed-question
test unaffected.

Not for production use — outputs are canned/keyword-matched, not real model
reasoning, and (aside from the date) do not reflect real transaction data.
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import date
from typing import Any
from unittest.mock import AsyncMock, MagicMock

from expense_agent_svc.nodes.synthesis import FinalAnswer


def _has_any(q: str, *phrases: str) -> bool:
    return any(p in q for p in phrases)


def _has_all(q: str, *phrases: str) -> bool:
    return all(p in q for p in phrases)


def _today_text(_: str) -> str:
    # Real, not fake — this reads the actual system clock.
    return f"Today's date is {date.today().strftime('%A, %B %d, %Y')}."


# Each preset: (match(question) -> bool, tool name, tool input, answer text or answer builder)
# Order matters — more specific matches must come before broader ones (e.g.
# "total spent" must be checked before the generic "total" + "week" pair).
_PRESETS: list[
    tuple[Callable[[str], bool], str, dict[str, Any], str | Callable[[str], str]]
] = [
    (
        lambda q: "costco" in q,
        "transactions.query",
        {"merchant": "Costco"},
        "You went to Costco 4 times this month, totaling $312.47.",
    ),
    (
        lambda q: _has_any(q, "today", "current date", "what's the date", "what is the date", "date"),
        "system.clock",
        {},
        _today_text,
    ),
    (
        lambda q: _has_any(
            q, "how many transactions", "total transactions", "number of transactions",
            "transaction count",
        ),
        "transactions.query",
        {"aggregate": "count"},
        "You have 47 transactions on record.",
    ),
    (
        lambda q: _has_any(q, "total spent", "how much have i spent", "total spending"),
        "transactions.query",
        {"aggregate": "sum"},
        "You've spent a total of $2,184.53 across all transactions.",
    ),
    (
        lambda q: _has_all(q, "total", "week"),
        "transactions.query",
        {"period": "this_week"},
        "Your total expenses this week are $342.18 across 9 transactions.",
    ),
    (
        lambda q: _has_any(q, "reimburs", "policy"),
        "orders.get_order",
        {"order_id": "o123"},
        "Travel expenses up to $500 are reimbursable. Order o123 is shipped.",
    ),
]

_DEFAULT_TOOL_NAME = "orders.get_order"
_DEFAULT_TOOL_INPUT: dict[str, Any] = {"order_id": "o123"}
_DEFAULT_TEXT = "Order o123 is shipped."


def _match_preset(question: str) -> tuple[str, dict[str, Any], str]:
    q = question.lower()
    for matches, tool_name, tool_input, answer in _PRESETS:
        if matches(q):
            text = answer(q) if callable(answer) else answer
            return tool_name, tool_input, text
    return _DEFAULT_TOOL_NAME, _DEFAULT_TOOL_INPUT, _DEFAULT_TEXT


def _extract_question(kwargs: dict[str, Any]) -> str:
    messages = kwargs.get("messages") or []
    for msg in messages:
        if msg.get("role") == "user":
            content = msg.get("content")
            if isinstance(content, str):
                return content
    return ""


def make_fake_anthropic() -> Any:
    """Returns tool_use on first call, end_turn (matched to the question) after."""
    anthropic = MagicMock()

    call_count = 0
    matched_text = _DEFAULT_TEXT
    matched_tool_name = _DEFAULT_TOOL_NAME
    matched_tool_input = _DEFAULT_TOOL_INPUT

    class _Usage:
        input_tokens = 100
        output_tokens = 50

    class _ToolUseBlock:
        type = "tool_use"
        id = "tu_0001"

        def __init__(self, name: str, tool_input: dict[str, Any]) -> None:
            self.name = name
            self.input = tool_input

    class _TextBlock:
        def __init__(self, text: str) -> None:
            self.type = "text"
            self.text = text

    class _RespToolUse:
        def __init__(self, name: str, tool_input: dict[str, Any]) -> None:
            self.stop_reason = "tool_use"
            self.content = [_ToolUseBlock(name, tool_input)]
            self.usage = _Usage()

    class _RespEndTurn:
        def __init__(self, text: str) -> None:
            self.stop_reason = "end_turn"
            self.content = [_TextBlock(text)]
            self.usage = _Usage()

    async def _create(*args: Any, **kwargs: Any) -> Any:
        nonlocal call_count, matched_text, matched_tool_name, matched_tool_input
        call_count += 1
        if call_count == 1:
            question = _extract_question(kwargs)
            matched_tool_name, matched_tool_input, matched_text = _match_preset(question)
            return _RespToolUse(matched_tool_name, matched_tool_input)
        return _RespEndTurn(matched_text)

    anthropic.messages = MagicMock()
    anthropic.messages.create = _create
    return anthropic


def make_fake_instructor() -> Any:
    instructor_client = MagicMock()

    class _FakeCompletion:
        class usage:
            input_tokens = 200
            output_tokens = 80

    async def _create_with_completion(*args: Any, **kwargs: Any) -> Any:
        question = _extract_question(kwargs)
        _, _, answer_text = _match_preset(question)
        final_answer = FinalAnswer(
            text=answer_text,
            citations=[],
            confidence=0.85,
        )
        return final_answer, _FakeCompletion()

    instructor_client.messages = MagicMock()
    instructor_client.messages.create_with_completion = AsyncMock(
        side_effect=_create_with_completion
    )
    return instructor_client


def make_fake_retriever() -> Any:
    """Async callable matching retrieval_agent's retriever(question, tenant_id, k) signature.

    Returns immediately with a couple of canned doc stubs instead of querying
    real Postgres/pgvector or downloading a real embedding model. Use this
    alongside make_fake_anthropic/make_fake_instructor so the whole graph runs
    without any real infra — not just the LLM calls.
    """

    async def _retrieve(*, question: str, tenant_id: str, k: int = 8) -> list[dict[str, Any]]:
        return [
            {"doc_id": "fake-doc-1", "chunk_idx": 0, "distance": 0.1},
            {"doc_id": "fake-doc-2", "chunk_idx": 0, "distance": 0.2},
        ][:k]

    return _retrieve

