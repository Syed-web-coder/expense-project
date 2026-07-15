import math

from expense_ai.value_types import RetrievalHit


def cosine_similarity(a: tuple[float, ...], b: tuple[float, ...]) -> float:
    if len(a) != len(b):
        raise ValueError(f"Vector length mismatch: {len(a)} vs {len(b)}")
    dot = sum((x * y for x, y in zip(a, b, strict=True)), 0.0)
    norm_a = math.sqrt(sum((x * x for x in a), 0.0))
    norm_b = math.sqrt(sum((x * x for x in b), 0.0))
    if norm_a == 0.0 or norm_b == 0.0:
        raise ValueError("Cannot compute cosine similarity of a zero vector")
    return dot / (norm_a * norm_b)


def top_k(
    query: tuple[float, ...],
    corpus: dict[str, tuple[float, ...]],
    k: int,
) -> list[RetrievalHit]:
    hits = [
        RetrievalHit(doc_id=doc_id, score=cosine_similarity(query, vec))
        for doc_id, vec in corpus.items()
    ]
    hits.sort(key=lambda h: h.score, reverse=True)
    return hits[:k]
