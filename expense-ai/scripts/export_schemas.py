import json
from pathlib import Path

from pydantic import BaseModel

from expense_ai.models import DeductionClassifyRequest, DeductionClassifyResult, Merchant


def main() -> None:
    schemas_dir = Path(__file__).parent.parent / "schemas"
    schemas_dir.mkdir(exist_ok=True)

    models: list[type[BaseModel]] = [Merchant, DeductionClassifyRequest, DeductionClassifyResult]
    for model_class in models:
        schema = model_class.model_json_schema()
        output_path = schemas_dir / f"{model_class.__name__}.json"
        output_path.write_text(json.dumps(schema, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
