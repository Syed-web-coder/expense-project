from __future__ import annotations

import asyncio
import time

import pytest

from expense_agent_svc.nodes._deadline import deadline


@pytest.mark.asyncio
async def test_deadline_returns_sentinel_quickly() -> None:
    sentinel: dict[str, list[object]] = {"docs": []}

    @deadline(seconds=0.2, sentinel=sentinel)
    async def slow_node(state: object) -> dict[str, object]:
        await asyncio.sleep(99)
        return {"docs": [{"chunk_id": "x"}]}

    start = time.monotonic()
    result = await slow_node({})
    elapsed = time.monotonic() - start

    assert result == sentinel
    assert elapsed < 2.0, f"Deadline took too long: {elapsed:.2f}s"


@pytest.mark.asyncio
async def test_deadline_passes_through_on_time() -> None:
    sentinel: dict[str, list[object]] = {"docs": []}

    @deadline(seconds=2.0, sentinel=sentinel)
    async def fast_node(state: object) -> dict[str, list[dict[str, str]]]:
        await asyncio.sleep(0.01)
        return {"docs": [{"chunk_id": "ok"}]}

    result = await fast_node({})
    assert result == {"docs": [{"chunk_id": "ok"}]}
