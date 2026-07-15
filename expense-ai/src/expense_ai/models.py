from typing import Annotated, Self

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class Merchant(BaseModel):
    model_config = ConfigDict(extra="forbid")

    merchant_id: str
    name: str
    category: str
    country: str

    @field_validator("country")
    @classmethod
    def validate_country(cls, v: str) -> str:
        normalized = v.strip().upper()
        if len(normalized) != 2 or not normalized.isalpha():
            raise ValueError(f"country must be a 2-letter ISO code, got {v!r}")
        return normalized


class DeductionClassifyRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    merchant: Merchant
    amount_cents: Annotated[int, Field(gt=0)]
    description: str
    tenant_id: str


class DeductionClassifyResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    deductible: bool
    confidence: Annotated[float, Field(ge=0.0, le=1.0)]
    rationale: str
    model_id: str

    @model_validator(mode="after")
    def check_rationale_for_high_confidence(self) -> Self:
        if self.confidence >= 0.9 and not self.rationale:
            raise ValueError("rationale must not be empty when confidence >= 0.9")
        return self
