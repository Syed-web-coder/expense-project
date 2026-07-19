from __future__ import annotations

# Airflow requires a POSIX environment; guard the import so the module is
# importable on Windows (where CI skips the DAG-import test entirely).
expense_ai_ingest_dag: object = None

try:
    import os
    from datetime import timedelta

    from airflow.decorators import dag, task  # type: ignore[import-untyped]

    @dag(
        dag_id="expense_ai_ingest",
        schedule=None,
        max_active_runs=1,
        default_args={
            "retries": 2,
            "retry_delay": timedelta(minutes=5),
        },
        catchup=False,
        tags=["expense-ai", "rag"],
    )
    def _expense_ai_ingest() -> None:
        @task(task_id="load_docs")
        def load_docs() -> list[str]:
            """Return paths / keys of raw documents to ingest."""
            # In production wire to an Airflow Variable or S3 sensor.
            return []

        @task(task_id="chunk_docs")
        def chunk_docs(paths: list[str]) -> list[dict[str, object]]:
            """Split raw document texts into overlapping chunks."""
            from langchain_core.documents import Document

            from expense_ai.chunker import chunk_docs as _chunk_docs

            docs = [Document(page_content=p, metadata={"doc_id": p}) for p in paths]
            chunks = _chunk_docs(docs)
            return [
                {
                    "doc_id": str(c.metadata.get("doc_id", "")),
                    "chunk_text": c.page_content,
                    "chunk_id": str(c.metadata.get("chunk_id", "")),
                }
                for c in chunks
            ]

        @task(task_id="embed_chunks")
        def embed_chunks(chunks: list[dict[str, object]]) -> list[dict[str, object]]:
            """Embed chunk texts and attach float32 embedding vectors."""
            import pandas as pd

            from expense_ai.corpus import embed_dataframe

            if not chunks:
                return []
            df = pd.DataFrame(chunks)
            df["tenant_id"] = df.get("tenant_id", "tenant-a")
            df["chunk_idx"] = range(len(df))
            rows = embed_dataframe(df)
            return [
                {
                    "doc_id": r.doc_id,
                    "chunk_idx": r.chunk_idx,
                    "chunk_text": r.chunk_text,
                    "model_version": r.model_version,
                    "tenant_id": r.tenant_id,
                }
                for r in rows
            ]

        @task(task_id="upsert_chunks")
        def upsert_chunks(rows: list[dict[str, object]]) -> int:
            """Upsert embedded rows into pgvector doc_chunks table."""
            if not rows:
                return 0
            import pandas as pd

            from expense_ai.corpus import embed_dataframe
            from expense_ai.pgvector_loader import dsn_from_env, load_rows

            df = pd.DataFrame(rows)
            corpus_rows = embed_dataframe(df)
            return load_rows(dsn_from_env(), corpus_rows)

        @task(task_id="bump_cache_epochs")
        def bump_cache_epochs(count: int) -> None:
            """Invalidate the semantic cache for all tenants after new data lands."""
            import redis

            from expense_ai.cache import bump_epoch

            redis_url = os.environ.get("EXPENSE_AI_REDIS_URL", "redis://localhost:6379")
            r: redis.Redis = redis.from_url(redis_url)  # type: ignore[assignment]
            for tenant in ("tenant-a", "tenant-b", "tenant-c"):
                bump_epoch(r, tenant)

        paths = load_docs()
        chunks = chunk_docs(paths)
        embedded = embed_chunks(chunks)
        n = upsert_chunks(embedded)
        bump_cache_epochs(n)

    expense_ai_ingest_dag = _expense_ai_ingest()

except ImportError:
    # Airflow not available (Windows dev machine); expense_ai_ingest_dag stays None.
    pass
