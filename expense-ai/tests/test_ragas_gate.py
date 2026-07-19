from __future__ import annotations

import hashlib
import json
import os
import warnings
from collections.abc import Generator
from pathlib import Path
from typing import Any

import numpy as np
import psycopg
import pytest
import redis as redis_module
from numpy.typing import NDArray
from testcontainers.redis import RedisContainer  # type: ignore[import-untyped]

from expense_ai.corpus import EMBEDDING_DIM, CorpusRow
from expense_ai.pgvector_loader import load_rows
from expense_ai.rag import retrieve_and_generate

os.environ.setdefault("LANGSMITH_API_KEY", "test-dummy-langsmith-key")
os.environ["LANGSMITH_TRACING"] = "false"

# ---------------------------------------------------------------------------
# RAGAS gate — guarded by Anthropic key presence (same pattern as W7D2)
# ---------------------------------------------------------------------------
_ANTHROPIC_KEY = os.environ.get("EXPENSE_AI_ANTHROPIC_API_KEY", "")
_SKIP_RAGAS = _ANTHROPIC_KEY in ("", "PLACEHOLDER")

_GOLDEN_FILE = Path(__file__).parent / "golden" / "expense_golden_50.jsonl"

FAITHFULNESS_HARD_FLOOR = 0.85
ANSWER_RELEVANCY_SOFT_FLOOR = 0.80
CONTEXT_PRECISION_SOFT_FLOOR = 0.75
CONTEXT_RECALL_SOFT_FLOOR = 0.80


@pytest.mark.slow
@pytest.mark.skipif(_SKIP_RAGAS, reason="RAGAS gate requires EXPENSE_AI_ANTHROPIC_API_KEY")
def test_ragas_gate() -> None:
    from datasets import Dataset  # type: ignore[import-untyped]
    from ragas import evaluate
    from ragas.metrics import (
        answer_relevancy,
        context_precision,
        context_recall,
        faithfulness,
    )

    os.environ["ANTHROPIC_API_KEY"] = _ANTHROPIC_KEY

    rows: list[dict[str, Any]] = []
    with _GOLDEN_FILE.open() as fh:
        for line in fh:
            stripped = line.strip()
            if stripped:
                rows.append(json.loads(stripped))

    dataset: Any = Dataset.from_list(rows)
    result: Any = evaluate(
        dataset=dataset,
        metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
    )
    scores: dict[str, float] = dict(result)

    # Hard gate — faithfulness below 0.85 is a pipeline failure
    if scores["faithfulness"] < FAITHFULNESS_HARD_FLOOR:
        raise SystemExit(
            f"RAGAS faithfulness {scores['faithfulness']:.3f} < {FAITHFULNESS_HARD_FLOOR} — "
            "pipeline quality gate failed"
        )

    # Soft warnings for the other three metrics
    for metric, floor in (
        ("answer_relevancy", ANSWER_RELEVANCY_SOFT_FLOOR),
        ("context_precision", CONTEXT_PRECISION_SOFT_FLOOR),
        ("context_recall", CONTEXT_RECALL_SOFT_FLOOR),
    ):
        if scores[metric] < floor:
            warnings.warn(
                f"RAGAS {metric} {scores[metric]:.3f} < {floor} (soft floor)",
                stacklevel=2,
            )


# ---------------------------------------------------------------------------
# Unit test — retrieve_and_generate with all fakes, no model downloads
# ---------------------------------------------------------------------------


class _FakeContent:
    def __init__(self, text: str) -> None:
        self.text = text


class _FakeResponse:
    def __init__(self, text: str) -> None:
        self.content = [_FakeContent(text)]


class _FakeMessages:
    def __init__(self, counter: list[int]) -> None:
        self._counter = counter

    def create(self, **kwargs: Any) -> _FakeResponse:
        self._counter[0] += 1
        return _FakeResponse("Expense deductions include meals and travel.")


class _FakeAnthropic:
    def __init__(self) -> None:
        self._counter: list[int] = [0]
        self.messages = _FakeMessages(self._counter)

    @property
    def call_count(self) -> int:
        return self._counter[0]


class _DeterministicEmbedder:
    """Returns a stable unit vector hashed from the input text."""

    def encode(
        self,
        sentences: list[str],
        *,
        normalize_embeddings: bool = True,
        convert_to_numpy: bool = True,
    ) -> NDArray[np.float32]:
        result: list[NDArray[np.float32]] = []
        for s in sentences:
            seed = int(hashlib.md5(s.encode()).hexdigest(), 16) % (2**32)
            rng = np.random.default_rng(seed)
            v = rng.random(EMBEDDING_DIM).astype(np.float32)
            v /= np.linalg.norm(v) + 1e-10
            result.append(v)
        return np.stack(result)


def _fake_scorer(pairs: list[list[str]]) -> NDArray[np.float32]:
    return np.ones(len(pairs), dtype=np.float32)


@pytest.fixture(scope="module")
def redis_for_rag() -> Generator[redis_module.Redis, None, None]:
    with RedisContainer("redis:7") as container:
        client: redis_module.Redis = container.get_client()
        yield client


@pytest.mark.integration
def test_retrieve_and_generate_shapes_and_caches(
    pg_dsn: str,
    redis_for_rag: redis_module.Redis,
) -> None:
    # Seed a few rows for tenant-a
    rng = np.random.default_rng(300)
    seed_rows = [
        CorpusRow(
            doc_id=f"rag-gen-doc-{i}",
            chunk_idx=0,
            chunk_text=f"Business meal deduction rule {i}: substantiation required.",
            embedding=rng.random(EMBEDDING_DIM).astype(np.float32),
            model_version="all-MiniLM-L6-v2",
            tenant_id="tenant-a",
        )
        for i in range(8)
    ]
    load_rows(pg_dsn, seed_rows)

    fake_anthropic = _FakeAnthropic()
    embedder = _DeterministicEmbedder()
    query = "What meal expenses are deductible?"

    with psycopg.connect(pg_dsn) as conn:
        answer1 = retrieve_and_generate(
            query,
            "tenant-a",
            anthropic=fake_anthropic,
            conn=conn,
            r=redis_for_rag,
            use_hybrid=True,
            use_mmr=True,
            use_rerank=True,
            use_filter=False,
            embedder=embedder,
            scorer=_fake_scorer,
        )

    assert "text" in answer1
    assert isinstance(answer1["text"], str)
    assert "citations" in answer1
    citations = answer1["citations"]
    assert isinstance(citations, list)
    for cit in citations:
        assert isinstance(cit, dict)
        assert cit.get("tenant_id") == "tenant-a", (
            f"Citation tenant_id mismatch: {cit.get('tenant_id')!r}"
        )
    assert "rerank_timed_out" in answer1
    assert fake_anthropic.call_count == 1

    # Second identical call must hit the cache — anthropic NOT called again
    with psycopg.connect(pg_dsn) as conn:
        answer2 = retrieve_and_generate(
            query,
            "tenant-a",
            anthropic=fake_anthropic,
            conn=conn,
            r=redis_for_rag,
            use_hybrid=True,
            use_mmr=True,
            use_rerank=True,
            use_filter=False,
            embedder=embedder,
            scorer=_fake_scorer,
        )

    assert fake_anthropic.call_count == 1, "Cache hit: Anthropic must not be called a second time"
    assert answer2["text"] == answer1["text"]
