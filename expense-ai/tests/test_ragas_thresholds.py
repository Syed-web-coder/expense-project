from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

import pytest

_ANTHROPIC_KEY = os.environ.get("EXPENSE_AI_ANTHROPIC_API_KEY", "")
_SKIP = _ANTHROPIC_KEY in ("", "PLACEHOLDER")

pytestmark = pytest.mark.skipif(
    _SKIP,
    reason="RAGAS evaluator requires Anthropic API key — pending",
)

_GOLDEN_FILE = Path(__file__).parent / "golden" / "expense_golden_50.jsonl"

FAITHFULNESS_THRESHOLD = 0.80
ANSWER_RELEVANCY_THRESHOLD = 0.80
CONTEXT_PRECISION_THRESHOLD = 0.65
CONTEXT_RECALL_THRESHOLD = 0.70


@pytest.mark.slow
def test_ragas_thresholds() -> None:
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

    assert scores["faithfulness"] >= FAITHFULNESS_THRESHOLD, (
        f"faithfulness {scores['faithfulness']:.3f} < {FAITHFULNESS_THRESHOLD}"
    )
    assert scores["answer_relevancy"] >= ANSWER_RELEVANCY_THRESHOLD, (
        f"answer_relevancy {scores['answer_relevancy']:.3f} < {ANSWER_RELEVANCY_THRESHOLD}"
    )
    assert scores["context_precision"] >= CONTEXT_PRECISION_THRESHOLD, (
        f"context_precision {scores['context_precision']:.3f} < {CONTEXT_PRECISION_THRESHOLD}"
    )
    assert scores["context_recall"] >= CONTEXT_RECALL_THRESHOLD, (
        f"context_recall {scores['context_recall']:.3f} < {CONTEXT_RECALL_THRESHOLD}"
    )
