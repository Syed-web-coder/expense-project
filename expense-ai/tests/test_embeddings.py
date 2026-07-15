import math

import pytest

from expense_ai.embeddings import cosine_similarity, top_k
from expense_ai.value_types import RetrievalHit


def test_cosine_similarity_identical() -> None:
    a = (1.0, 0.0, 0.0)
    assert cosine_similarity(a, a) == pytest.approx(1.0)


def test_cosine_similarity_orthogonal() -> None:
    a = (1.0, 0.0)
    b = (0.0, 1.0)
    assert cosine_similarity(a, b) == pytest.approx(0.0)


def test_cosine_similarity_opposite() -> None:
    a = (1.0, 0.0)
    b = (-1.0, 0.0)
    assert cosine_similarity(a, b) == pytest.approx(-1.0)


def test_cosine_similarity_symmetric() -> None:
    a = (1.0, 2.0, 3.0)
    b = (4.0, 5.0, 6.0)
    assert cosine_similarity(a, b) == pytest.approx(cosine_similarity(b, a))


def test_cosine_similarity_known_value() -> None:
    a = (1.0, 0.0)
    b = (1.0, 1.0)
    expected = 1.0 / math.sqrt(2.0)
    assert cosine_similarity(a, b) == pytest.approx(expected)


def test_cosine_similarity_length_mismatch_raises() -> None:
    with pytest.raises(ValueError, match="length mismatch"):
        cosine_similarity((1.0, 2.0), (1.0, 2.0, 3.0))


def test_cosine_similarity_zero_vector_a_raises() -> None:
    with pytest.raises(ValueError, match="zero vector"):
        cosine_similarity((0.0, 0.0), (1.0, 0.0))


def test_cosine_similarity_zero_vector_b_raises() -> None:
    with pytest.raises(ValueError, match="zero vector"):
        cosine_similarity((1.0, 0.0), (0.0, 0.0))


def test_top_k_returns_sorted_descending() -> None:
    query = (1.0, 0.0)
    corpus: dict[str, tuple[float, ...]] = {
        "identical": (1.0, 0.0),
        "diagonal": (1.0, 1.0),
        "orthogonal": (0.0, 1.0),
    }
    results = top_k(query, corpus, k=3)
    assert len(results) == 3
    assert results[0].doc_id == "identical"
    assert results[0].score == pytest.approx(1.0)
    assert results[2].doc_id == "orthogonal"
    assert results[2].score == pytest.approx(0.0)


def test_top_k_k_limits_results() -> None:
    query = (1.0, 0.0)
    corpus: dict[str, tuple[float, ...]] = {
        "a": (1.0, 0.0),
        "b": (1.0, 0.1),
        "c": (1.0, 0.5),
        "d": (0.5, 1.0),
    }
    results = top_k(query, corpus, k=2)
    assert len(results) == 2


def test_top_k_returns_retrieval_hit_instances() -> None:
    query = (1.0, 0.0)
    corpus: dict[str, tuple[float, ...]] = {"doc": (1.0, 0.0)}
    results = top_k(query, corpus, k=1)
    assert isinstance(results[0], RetrievalHit)


def test_top_k_empty_corpus() -> None:
    results = top_k((1.0, 0.0), {}, k=5)
    assert results == []
