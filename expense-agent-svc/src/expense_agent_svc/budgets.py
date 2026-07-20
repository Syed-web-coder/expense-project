from __future__ import annotations

from typing import Any


class BudgetExceeded(Exception):
    """Raised when cumulative LLM spend reaches the request ceiling."""


class BudgetGuard:
    """Tracks integer micro-dollar spend and enforces a per-request ceiling.

    All monetary values are stored as int (units of 1e-5 USD = 0.00001 USD)
    to avoid floating-point drift.  Pricing baked in:
      input  tokens: $3.00 / MTok  → 300 e5-units per 1000 tokens
      output tokens: $15.00 / MTok → 1500 e5-units per 1000 tokens
    """

    def __init__(self, ceiling_usd_e5: int = 25_000) -> None:
        self._ceiling_usd_e5: int = ceiling_usd_e5
        self._spent_usd_e5: int = 0

    @property
    def spent_usd_e5(self) -> int:
        return self._spent_usd_e5

    @property
    def spent_delta(self) -> int:
        """Total spent by this guard instance (delta since creation)."""
        return self._spent_usd_e5

    def check_or_raise(self) -> None:
        if self._spent_usd_e5 >= self._ceiling_usd_e5:
            raise BudgetExceeded(
                f"Request budget exceeded: {self._spent_usd_e5} >= {self._ceiling_usd_e5} (e5-USD)"
            )

    def record_call(self, resp: Any) -> None:
        usage = resp.usage
        cost_e5: int = (int(usage.input_tokens) * 300 + int(usage.output_tokens) * 1500) // 1000
        self._spent_usd_e5 += cost_e5
