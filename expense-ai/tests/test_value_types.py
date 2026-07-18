from dataclasses import FrozenInstanceError

import pytest

from expense_ai.value_types import EmbeddingVector, ModelCard, RetrievalHit, TenantRef, TokenBudget


def test_embedding_vector_creation() -> None:
    vec = EmbeddingVector(values=(1.0, 2.0, 3.0), model_name="text-embed-3")
    assert vec.values == (1.0, 2.0, 3.0)
    assert vec.model_name == "text-embed-3"


def test_embedding_vector_frozen() -> None:
    vec = EmbeddingVector(values=(1.0, 2.0), model_name="test")
    with pytest.raises(FrozenInstanceError):
        vec.model_name = "other"  # type: ignore[misc]


def test_retrieval_hit_creation() -> None:
    hit = RetrievalHit(doc_id="doc-42", score=0.87)
    assert hit.doc_id == "doc-42"
    assert hit.score == pytest.approx(0.87)


def test_retrieval_hit_frozen() -> None:
    hit = RetrievalHit(doc_id="x", score=0.5)
    with pytest.raises(FrozenInstanceError):
        hit.score = 0.9  # type: ignore[misc]


def test_token_budget_creation() -> None:
    budget = TokenBudget(max_input=4096, max_output=1024)
    assert budget.max_input == 4096
    assert budget.max_output == 1024


def test_tenant_ref_creation() -> None:
    ref = TenantRef(tenant_id="tenant-abc", region="us-east-1")
    assert ref.tenant_id == "tenant-abc"
    assert ref.region == "us-east-1"


def test_model_card_capabilities_frozenset() -> None:
    card = ModelCard(
        model_id="claude-3",
        provider="anthropic",
        capabilities=frozenset({"text", "vision", "tools"}),
    )
    assert isinstance(card.capabilities, frozenset)
    assert "text" in card.capabilities
    assert "vision" in card.capabilities


def test_model_card_frozen() -> None:
    card = ModelCard(model_id="m", provider="p", capabilities=frozenset())
    with pytest.raises(FrozenInstanceError):
        card.provider = "other"  # type: ignore[misc]


def test_embedding_vector_uses_slots() -> None:
    vec = EmbeddingVector(values=(0.1,), model_name="m")
    assert not hasattr(vec, "__dict__")


def test_model_card_uses_slots() -> None:
    card = ModelCard(model_id="m", provider="p", capabilities=frozenset())
    assert not hasattr(card, "__dict__")
