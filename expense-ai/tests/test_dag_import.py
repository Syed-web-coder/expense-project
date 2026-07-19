from __future__ import annotations

import sys
from typing import Any

import pytest


@pytest.mark.skipif(sys.platform == "win32", reason="Airflow requires a POSIX environment")
def test_dag_import_and_structure() -> None:
    """Importing the DAG module must succeed and expose the correct DAG object."""
    from expense_ai.dags.rag_svc_ingest import expense_ai_ingest_dag

    dag: Any = expense_ai_ingest_dag
    assert dag is not None, "expense_ai_ingest_dag must not be None on POSIX"
    assert dag.dag_id == "expense_ai_ingest"
    assert len(dag.task_ids) == 5, (
        f"Expected 5 tasks, got {len(dag.task_ids)}: {sorted(dag.task_ids)}"
    )
