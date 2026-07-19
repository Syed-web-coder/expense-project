from __future__ import annotations

import concurrent.futures
from collections.abc import Callable
from typing import Any, Protocol, cast

import numpy as np
from langsmith import traceable
from numpy.typing import NDArray

RERANKER_MODEL = "BAAI/bge-reranker-base"
RERANK_TIMEOUT_MS = 300
MMR_LAMBDA = 0.7

RERANK_TIMEOUT_COUNT: int = 0

_reranker: Any = None


class _Embedder(Protocol):
    def encode(
        self,
        sentences: list[str],
        *,
        normalize_embeddings: bool = ...,
        convert_to_numpy: bool = ...,
    ) -> NDArray[np.float32]: ...


def _get_reranker() -> Any:
    global _reranker
    if _reranker is None:
        from sentence_transformers import CrossEncoder

        _reranker = CrossEncoder(RERANKER_MODEL, max_length=256)
    return _reranker


@traceable(run_type="chain", name="expense_ai.mmr_pick")
def mmr_pick(
    query_vec: NDArray[np.float32],
    candidates: list[tuple[str, str, float]],
    embedder: _Embedder,
    k: int = 20,
    lambda_param: float = MMR_LAMBDA,
) -> list[tuple[str, str, float]]:
    if not candidates:
        return []

    texts = [c[1] for c in candidates]
    vecs: NDArray[np.float32] = embedder.encode(
        texts,
        normalize_embeddings=True,
        convert_to_numpy=True,
    ).astype(np.float32)

    norm = float(np.linalg.norm(query_vec)) + 1e-10
    q: NDArray[np.float32] = (query_vec / norm).astype(np.float32)

    query_sims: NDArray[np.float32] = (vecs @ q).astype(np.float32)

    n = len(candidates)
    effective_k = min(k, n)
    selected: list[int] = []
    remaining = list(range(n))
    max_sel_sim: NDArray[np.float32] = np.full(n, -np.inf, dtype=np.float32)

    for _ in range(effective_k):
        best_idx = max(
            remaining,
            key=lambda i: (
                lambda_param * float(query_sims[i]) - (1.0 - lambda_param) * float(max_sel_sim[i])
            ),
        )
        selected.append(best_idx)
        remaining.remove(best_idx)
        for i in remaining:
            sim = float(np.dot(vecs[i], vecs[best_idx]))
            if sim > float(max_sel_sim[i]):
                max_sel_sim[i] = np.float32(sim)

    return [candidates[i] for i in selected]


@traceable(run_type="chain", name="expense_ai.bge_rerank")
def bge_rerank(
    query_text: str,
    candidates: list[tuple[str, str, float]],
    top_k: int = 6,
    timeout_ms: int = RERANK_TIMEOUT_MS,
    scorer: Callable[[list[list[str]]], NDArray[np.float32]] | None = None,
) -> tuple[list[tuple[str, str, float]], bool]:
    global RERANK_TIMEOUT_COUNT

    if not candidates:
        return [], False

    if scorer is None:
        scorer = cast(
            Callable[[list[list[str]]], NDArray[np.float32]],
            _get_reranker().predict,
        )

    pairs: list[list[str]] = [[query_text, c[1]] for c in candidates]

    def _score() -> NDArray[np.float32]:
        return scorer(pairs)

    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(_score)
        try:
            scores = future.result(timeout=timeout_ms / 1000.0)
        except TimeoutError:
            RERANK_TIMEOUT_COUNT += 1
            return candidates[:top_k], True

    idx_sorted = sorted(
        range(len(candidates)),
        key=lambda i: float(scores[i]),
        reverse=True,
    )
    return [candidates[i] for i in idx_sorted[:top_k]], False
