# expense-mcp-server/tests/test_schemas.py
"""Schema-level tests: no network, no Testcontainers. Confirms the
input models are as strict as the assignment requires -- additionalProperties
is false everywhere, string amounts parse to Decimal, idempotency_key is
required, malformed tenant_id is rejected before any HTTP call is made.
"""
from decimal import Decimal
from uuid import uuid4

import pytest
from pydantic import BaseModel, ValidationError

from expense_mcp_server.tools.llm import ChatArgs
from expense_mcp_server.tools.orders import CreateRefundArgs, GetOrderArgs
from expense_mcp_server.tools.rag import RagArgs

ALL_INPUT_MODELS = [GetOrderArgs, CreateRefundArgs, ChatArgs, RagArgs]


@pytest.mark.parametrize("model", ALL_INPUT_MODELS)
def test_input_model_forbids_additional_properties(model: type[BaseModel]) -> None:
    schema = model.model_json_schema()
    assert schema.get("additionalProperties") is False, (
        f"{model.__name__} JSON Schema must set additionalProperties: false"
    )


def test_create_refund_args_amount_string_parses_to_decimal() -> None:
    # Deliberately passing a str: this is the whole point of the test --
    # Pydantic coerces it to Decimal at runtime even though the static
    # field type is Decimal, so mypy flags the literal as wrong.
    args = CreateRefundArgs(
        order_id="ord-synth-9001",
        amount="10.00",  # type: ignore[arg-type]
        reason="duplicate charge",
        tenant_id="tenant-a",
        idempotency_key=uuid4(),
    )
    assert args.amount == Decimal("10.00")


def test_create_refund_args_amount_rejects_sub_cent_precision() -> None:
    with pytest.raises(ValidationError):
        CreateRefundArgs(
            order_id="ord-synth-9001",
            amount="10.001",  # type: ignore[arg-type]
            reason="duplicate charge",
            tenant_id="tenant-a",
            idempotency_key=uuid4(),
        )


def test_create_refund_args_missing_idempotency_key_raises() -> None:
    # Deliberately omitting a required field to assert Pydantic raises
    # at runtime; mypy would (correctly) flag this as a static error.
    with pytest.raises(ValidationError):
        CreateRefundArgs(
            order_id="ord-synth-9001",
            amount="10.00",  # type: ignore[arg-type]
            reason="duplicate charge",
            tenant_id="tenant-a",
        )  # type: ignore[call-arg]


def test_get_order_args_malformed_tenant_id_rejected_before_http_call() -> None:
    with pytest.raises(ValidationError):
        GetOrderArgs(order_id="ord-synth-9001", tenant_id="tenant-d")
