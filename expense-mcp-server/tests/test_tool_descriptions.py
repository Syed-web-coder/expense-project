# expense-mcp-server/tests/test_tool_descriptions.py
"""Routing-quality gate (Section 9): vague tool descriptions fail the
build before they ever reach Claude Desktop. Every tool description
must be long enough to actually explain WHEN/WHEN NOT to call the tool,
must contain the literal routing phrases "Use this" and "Do NOT", and
must end in a concrete worked example.
"""
from __future__ import annotations

import re

import pytest

from expense_mcp_server.tools.llm import _DESC_CHAT
from expense_mcp_server.tools.orders import _DESC_CREATE_REFUND, _DESC_GET_ORDER
from expense_mcp_server.tools.rag import _DESC_RAG

MIN_LENGTH = 200
# A concrete example: the description's tail should read like
# "Example: some_field='value', other_field=123 returns/does <outcome>."
_EXAMPLE_TAIL_RE = re.compile(r"Example:.+\.\s*$", re.DOTALL)

ALL_DESCRIPTIONS = {
    "orders.get_order": _DESC_GET_ORDER,
    "orders.create_refund": _DESC_CREATE_REFUND,
    "llm.chat": _DESC_CHAT,
    "rag.retrieve_and_generate": _DESC_RAG,
}


@pytest.mark.parametrize("tool_name", ALL_DESCRIPTIONS)
def test_description_meets_minimum_length(tool_name: str) -> None:
    desc = ALL_DESCRIPTIONS[tool_name]
    assert len(desc) >= MIN_LENGTH, (
        f"{tool_name} description is {len(desc)} chars, needs >= {MIN_LENGTH}"
    )


@pytest.mark.parametrize("tool_name", ALL_DESCRIPTIONS)
def test_description_contains_use_this(tool_name: str) -> None:
    desc = ALL_DESCRIPTIONS[tool_name]
    assert "Use this" in desc, f"{tool_name} description is missing the literal 'Use this'"


@pytest.mark.parametrize("tool_name", ALL_DESCRIPTIONS)
def test_description_contains_do_not(tool_name: str) -> None:
    desc = ALL_DESCRIPTIONS[tool_name]
    assert "Do NOT" in desc, f"{tool_name} description is missing the literal 'Do NOT'"


@pytest.mark.parametrize("tool_name", ALL_DESCRIPTIONS)
def test_description_ends_with_concrete_example(tool_name: str) -> None:
    desc = ALL_DESCRIPTIONS[tool_name]
    assert _EXAMPLE_TAIL_RE.search(desc), (
        f"{tool_name} description does not end with a concrete 'Example: ...' clause"
    )
