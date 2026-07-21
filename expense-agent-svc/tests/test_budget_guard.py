from __future__ import annotations

import pytest

from expense_agent_svc.budgets import BudgetExceeded, BudgetGuard


class _FakeUsage:
    def __init__(self, input_tokens: int, output_tokens: int) -> None:
        self.input_tokens = input_tokens
        self.output_tokens = output_tokens


class _FakeResp:
    def __init__(self, input_tokens: int, output_tokens: int) -> None:
        self.usage = _FakeUsage(input_tokens, output_tokens)


def test_raises_at_ceiling() -> None:
    guard = BudgetGuard(ceiling_usd_e5=100)
    guard._spent_usd_e5 = 100
    with pytest.raises(BudgetExceeded):
        guard.check_or_raise()


def test_does_not_raise_below_ceiling() -> None:
    guard = BudgetGuard(ceiling_usd_e5=100)
    guard._spent_usd_e5 = 99
    guard.check_or_raise()  # should not raise


def test_record_call_arithmetic() -> None:
    guard = BudgetGuard(ceiling_usd_e5=1_000_000)
    guard.record_call(_FakeResp(input_tokens=1000, output_tokens=500))
    # (1000*300 + 500*1500) // 1000 = (300_000 + 750_000) // 1000 = 1050
    assert guard.spent_usd_e5 == 1050


def test_record_call_accumulates() -> None:
    guard = BudgetGuard(ceiling_usd_e5=1_000_000)
    guard.record_call(_FakeResp(input_tokens=1000, output_tokens=0))
    guard.record_call(_FakeResp(input_tokens=1000, output_tokens=0))
    # Each call: (1000*300)//1000 = 300
    assert guard.spent_usd_e5 == 600


def test_money_is_int() -> None:
    guard = BudgetGuard()
    assert isinstance(guard.spent_usd_e5, int)
    assert isinstance(guard.spent_delta, int)
    guard.record_call(_FakeResp(input_tokens=100, output_tokens=50))
    assert isinstance(guard.spent_usd_e5, int)


def test_spent_delta_matches_spent() -> None:
    guard = BudgetGuard(ceiling_usd_e5=1_000_000)
    guard.record_call(_FakeResp(input_tokens=2000, output_tokens=1000))
    assert guard.spent_delta == guard.spent_usd_e5
