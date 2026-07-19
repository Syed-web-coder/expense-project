from __future__ import annotations

import os
import time

import numpy as np
from numpy.typing import NDArray

import expense_ai.rerank as rerank_module
from expense_ai.rerank import RERANKER_MODEL, bge_rerank, mmr_pick

os.environ.setdefault("LANGSMITH_API_KEY", "test-dummy-langsmith-key")
os.environ["LANGSMITH_TRACING"] = "false"

_DIM = 16  # tiny dimension for fast tests


# ---------------------------------------------------------------------------
# Fake embedder — returns rows from a pre-built matrix by call order
# ---------------------------------------------------------------------------
class _FakeEmbedder:
    def __init__(self, matrix: NDArray[np.float32]) -> None:
        self._matrix = matrix

    def encode(
        self,
        sentences: list[str],
        *,
        normalize_embeddings: bool = True,
        convert_to_numpy: bool = True,
    ) -> NDArray[np.float32]:
        return self._matrix[: len(sentences)]


def _unit(v: NDArray[np.float32]) -> NDArray[np.float32]:
    n = float(np.linalg.norm(v))
    return (v / (n + 1e-10)).astype(np.float32)


# ---------------------------------------------------------------------------
# Test (i) — lambda=1.0 gives same order as pure cosine ranking
# ---------------------------------------------------------------------------
def test_mmr_lambda1_matches_cosine_order() -> None:
    # query = e0; candidate cosines to e0 are exact: [0.9, 0.3, 0.7, 0.1, 0.5]
    # Expected descending order: 0.9, 0.7, 0.5, 0.3, 0.1 → doc-0, doc-2, doc-4, doc-1, doc-3
    query_vec = np.zeros(_DIM, dtype=np.float32)
    query_vec[0] = 1.0

    cos_vals = [0.9, 0.3, 0.7, 0.1, 0.5]
    vecs = np.zeros((5, _DIM), dtype=np.float32)
    for i, c in enumerate(cos_vals):
        vecs[i, 0] = c
        vecs[i, 1] = float(np.sqrt(max(1.0 - c * c, 0.0)))

    candidates: list[tuple[str, str, float]] = [(f"doc-{i}", f"text-{i}", 0.5) for i in range(5)]
    expected_order = sorted(range(5), key=lambda i: cos_vals[i], reverse=True)

    embedder = _FakeEmbedder(vecs)
    result = mmr_pick(query_vec, candidates, embedder, k=5, lambda_param=1.0)

    result_ids = [r[0] for r in result]
    expected_ids = [f"doc-{i}" for i in expected_order]
    assert result_ids == expected_ids, f"got {result_ids}, expected {expected_ids}"


# ---------------------------------------------------------------------------
# Test (ii) — lambda=0.0 spreads picks across two tight clusters
# ---------------------------------------------------------------------------
def test_mmr_lambda0_spreads_across_clusters() -> None:
    # Cluster A = e0, Cluster B = e1 — exactly orthogonal (cross-cluster cosine = 0.0)
    # lambda=0 forces MMR to maximise diversity: consecutive picks must alternate clusters.
    e0 = np.zeros(_DIM, dtype=np.float32)
    e0[0] = 1.0
    e1 = np.zeros(_DIM, dtype=np.float32)
    e1[1] = 1.0

    # interleave A B A B A B — identical within each cluster so intra-cluster cosine = 1.0
    vecs = np.stack([e0, e1, e0, e1, e0, e1])

    query_vec = e0.copy()  # lambda=0 ignores query, but must be non-zero
    candidates: list[tuple[str, str, float]] = [(f"doc-{i}", f"text-{i}", 0.5) for i in range(6)]

    embedder = _FakeEmbedder(vecs)
    result = mmr_pick(query_vec, candidates, embedder, k=6, lambda_param=0.0)

    result_vecs = np.stack([vecs[int(r[0].split("-")[1])] for r in result])
    for i in range(len(result) - 1):
        cos = float(np.dot(result_vecs[i], result_vecs[i + 1]))
        assert cos < 0.95, (
            f"Consecutive picks {result[i][0]} and {result[i + 1][0]} "
            f"have cosine {cos:.3f} >= 0.95 — MMR did not spread"
        )


# ---------------------------------------------------------------------------
# Test (iii) — fake scorer lifts gold chunk from rank 5 to rank 1
# ---------------------------------------------------------------------------
def test_bge_rerank_lifts_gold_chunk() -> None:
    candidates: list[tuple[str, str, float]] = [
        ("doc-1", "generic text about expenses", 0.9),
        ("doc-2", "quarterly budget review", 0.8),
        ("doc-3", "vendor payment schedule", 0.7),
        ("doc-4", "tax deduction categories", 0.6),
        ("doc-5", "gold chunk: exact answer here", 0.5),  # rank 5 initially
        ("doc-6", "unrelated filing document", 0.4),
    ]

    def _fake_scorer(pairs: list[list[str]]) -> NDArray[np.float32]:
        scores = np.zeros(len(pairs), dtype=np.float32)
        for idx, pair in enumerate(pairs):
            if "gold chunk" in pair[1]:
                scores[idx] = 10.0
            else:
                scores[idx] = float(idx) * 0.1
        return scores

    result, timed_out = bge_rerank(
        "What is the exact answer?", candidates, top_k=6, scorer=_fake_scorer
    )

    assert not timed_out
    assert result[0][0] == "doc-5", f"Expected doc-5 at rank 1, got {result[0][0]}"


# ---------------------------------------------------------------------------
# Test (iv) — timeout fallback: returns original order + flag + counter bump
# ---------------------------------------------------------------------------
def test_bge_rerank_timeout_fallback() -> None:
    candidates: list[tuple[str, str, float]] = [
        (f"doc-{i}", f"text {i}", float(i)) for i in range(4)
    ]

    def _slow_scorer(pairs: list[list[str]]) -> NDArray[np.float32]:
        time.sleep(0.05)  # 50 ms — much longer than timeout_ms=1
        return np.zeros(len(pairs), dtype=np.float32)

    before = rerank_module.RERANK_TIMEOUT_COUNT
    result, timed_out = bge_rerank("query", candidates, top_k=3, timeout_ms=1, scorer=_slow_scorer)

    assert timed_out is True
    assert result == candidates[:3], f"Expected first 3 in original order, got {result}"
    assert rerank_module.RERANK_TIMEOUT_COUNT == before + 1


# ---------------------------------------------------------------------------
# Sanity: constants are set correctly
# ---------------------------------------------------------------------------
def test_rerank_constants() -> None:
    assert RERANKER_MODEL == "BAAI/bge-reranker-base"
    assert rerank_module.RERANK_TIMEOUT_MS == 300
    assert rerank_module.MMR_LAMBDA == 0.7
