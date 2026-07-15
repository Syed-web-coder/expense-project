from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class EmbeddingVector:
    values: tuple[float, ...]
    model_name: str


@dataclass(frozen=True, slots=True)
class RetrievalHit:
    doc_id: str
    score: float


@dataclass(frozen=True, slots=True)
class TokenBudget:
    max_input: int
    max_output: int


@dataclass(frozen=True, slots=True)
class TenantRef:
    tenant_id: str
    region: str


@dataclass(frozen=True, slots=True)
class ModelCard:
    model_id: str
    provider: str
    capabilities: frozenset[str]
