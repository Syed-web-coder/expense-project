"""deadline(seconds, sentinel) — wraps an async node with asyncio.wait_for.

Apply BEFORE @traceable so the timeout covers the full traced execution:

    @deadline(seconds=3.0, sentinel={"docs": []})
    @traceable(name="retrieval_agent")
    async def retrieval_agent(state: AgentState) -> dict[str, Any]: ...
"""

from __future__ import annotations

import asyncio
import functools
from collections.abc import Callable, Coroutine
from typing import Any, TypeVar

F = TypeVar("F", bound=Callable[..., Coroutine[Any, Any, Any]])


def deadline(seconds: float, sentinel: dict[str, Any]) -> Callable[[F], F]:
    def decorator(fn: F) -> F:
        @functools.wraps(fn)
        async def wrapper(*args: Any, **kwargs: Any) -> Any:
            try:
                return await asyncio.wait_for(fn(*args, **kwargs), timeout=seconds)
            except TimeoutError:
                try:
                    from langsmith.run_trees import (  # type: ignore[attr-defined]
                        get_current_run_tree,
                    )

                    run = get_current_run_tree()
                    if run is not None:
                        run.add_metadata({"deadline_exceeded": True, "limit_s": seconds})
                except Exception:
                    pass
                return sentinel

        return wrapper  # type: ignore[return-value]

    return decorator
