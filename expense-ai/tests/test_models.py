import json
from pathlib import Path

import pytest
from pydantic import BaseModel, ValidationError

from expense_ai.models import DeductionClassifyRequest, DeductionClassifyResult, Merchant

_MERCHANT_DEFAULTS = {"merchant_id": "m-1", "name": "Acme", "category": "travel"}


def _merchant(**kwargs: str) -> Merchant:
    return Merchant(**{**_MERCHANT_DEFAULTS, **kwargs})


def test_merchant_valid() -> None:
    m = _merchant(country="US")
    assert m.country == "US"
    assert m.merchant_id == "m-1"


def test_merchant_country_normalized() -> None:
    m = _merchant(country="us")
    assert m.country == "US"


def test_merchant_country_with_whitespace_normalized() -> None:
    m = _merchant(country=" gb ")
    assert m.country == "GB"


@pytest.mark.parametrize("country", ["USA", "U", "U1", "12", ""])
def test_merchant_invalid_countries_raise(country: str) -> None:
    with pytest.raises(ValidationError):
        _merchant(country=country)


def test_merchant_extra_field_raises() -> None:
    with pytest.raises(ValidationError):
        Merchant(merchant_id="m-1", name="Acme", category="travel", country="US", extra="bad")  # type: ignore[call-arg]


def test_deduction_classify_request_valid() -> None:
    m = _merchant(country="DE")
    req = DeductionClassifyRequest(
        merchant=m, amount_cents=1000, description="Hotel", tenant_id="t-1"
    )
    assert req.amount_cents == 1000
    assert req.tenant_id == "t-1"


def test_deduction_classify_request_zero_amount_raises() -> None:
    m = _merchant(country="DE")
    with pytest.raises(ValidationError):
        DeductionClassifyRequest(merchant=m, amount_cents=0, description="Hotel", tenant_id="t-1")


def test_deduction_classify_request_negative_amount_raises() -> None:
    m = _merchant(country="DE")
    with pytest.raises(ValidationError):
        DeductionClassifyRequest(merchant=m, amount_cents=-50, description="Hotel", tenant_id="t-1")


def test_deduction_classify_result_valid() -> None:
    result = DeductionClassifyResult(
        deductible=True,
        confidence=0.8,
        rationale="Business travel",
        model_id="gpt-4",
    )
    assert result.deductible is True
    assert result.confidence == pytest.approx(0.8)


def test_deduction_classify_result_high_confidence_with_rationale_ok() -> None:
    result = DeductionClassifyResult(
        deductible=False,
        confidence=0.95,
        rationale="Personal expense",
        model_id="gpt-4",
    )
    assert result.confidence == pytest.approx(0.95)


def test_deduction_classify_result_high_confidence_empty_rationale_raises() -> None:
    with pytest.raises(ValidationError, match="rationale must not be empty"):
        DeductionClassifyResult(
            deductible=True,
            confidence=0.9,
            rationale="",
            model_id="gpt-4",
        )


def test_deduction_classify_result_extra_field_raises() -> None:
    with pytest.raises(ValidationError):
        DeductionClassifyResult(  # type: ignore[call-arg]
            deductible=True,
            confidence=0.5,
            rationale="ok",
            model_id="gpt-4",
            unexpected="field",
        )


def test_deduction_classify_result_confidence_out_of_range_raises() -> None:
    with pytest.raises(ValidationError):
        DeductionClassifyResult(deductible=True, confidence=1.1, rationale="ok", model_id="m")


def test_schema_file_with_tmp_path(tmp_path: Path) -> None:
    schema = Merchant.model_json_schema()
    schema_file = tmp_path / "Merchant.json"
    schema_file.write_text(json.dumps(schema, indent=2, sort_keys=True))
    loaded = json.loads(schema_file.read_text())
    assert loaded == schema


def test_schema_drift() -> None:
    schemas_dir = Path(__file__).parent.parent / "schemas"
    models: list[type[BaseModel]] = [Merchant, DeductionClassifyRequest, DeductionClassifyResult]
    for model_class in models:
        committed_path = schemas_dir / f"{model_class.__name__}.json"
        committed = json.loads(committed_path.read_text())
        fresh = model_class.model_json_schema()
        assert committed == fresh, f"{model_class.__name__} schema has drifted from committed file"
