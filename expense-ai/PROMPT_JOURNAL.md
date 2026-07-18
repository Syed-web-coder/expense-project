# PROMPT_JOURNAL

## W7 D2 — 2026-07-18

### Prompt 1 — corpus.py scaffolding

**Prompt given:**
> Write `src/expense_ai/corpus.py`. It should expose a `CorpusRow` frozen dataclass (doc_id, chunk_idx, chunk_text, embedding as NDArray[float32], model_version, tenant_id), a `load_corpus(path: Path) -> pd.DataFrame` that reads parquet or JSONL, drops duplicates on (doc_id, chunk_idx), and silently drops rows where chunk_text length is outside [1, 8000], and an `embed_dataframe(df, model=None, batch_size=64) -> list[CorpusRow]` that lazy-loads SentenceTransformer("all-MiniLM-L6-v2") when model is None, calls model.encode() in one batched call, and casts the output to float32.

**What came back:** Full implementation of `corpus.py` with `MODEL_NAME = "all-MiniLM-L6-v2"`, `EMBEDDING_DIM = 384`, the `CorpusRow` dataclass, `load_corpus` supporting `.parquet`/`.jsonl`/`.json`, dedup and length-filter, and `embed_dataframe` with the `.astype(np.float32)` cast — plus `test_corpus.py` covering dedup, length bounds, unsupported extension, and the float64→float32 dtype assertion via `_FakeModel`.

---

### Prompt 2 — pgvector_loader.py scaffolding

**Prompt given:**
> Write `src/expense_ai/pgvector_loader.py`. It should expose `load_rows(dsn: str, rows: Iterable[CorpusRow]) -> int` that connects via psycopg (v3), calls `register_vector`, upserts all rows into `doc_chunks` using `ON CONFLICT (doc_id, chunk_idx, model_version) DO UPDATE`, and returns the count inserted. Also expose `dsn_from_env() -> str` that reads `EXPENSE_AI_PG_DSN`. Keep it under 50 lines. Add integration tests in `tests/test_pgvector_loader.py` that reuse the session-scoped `pg_dsn` Testcontainers fixture from conftest: test 100-row load, idempotency (double-load still 100 rows), HNSW index presence, and EXPLAIN output confirms the HNSW index is used for cosine ORDER BY LIMIT 5.

**What came back:** `pgvector_loader.py` with `executemany` upsert, and `test_pgvector_loader.py` with all four integration tests — including `SET LOCAL enable_seqscan = off` before `EXPLAIN` to force the planner onto the HNSW index.

---

### Prompt 3 — Great Expectations suite scaffolding

**Prompt given:**
> Write `tests/test_great_expectations_suite.py` using great-expectations 1.x APIs (GX 1.19 is installed). The test should: reuse the session-scoped `pg_dsn` fixture from conftest; seed 100+ chunks via `load_corpus(fixtures/corpus_seed.jsonl)` + a fake-encoder `_FakeModel` + `load_rows`; build an ephemeral GX context with `gx.get_context(mode="ephemeral")`; add a postgres datasource using `ctx.data_sources.add_postgres()` with a `postgresql+psycopg://` connection string (GX's SQLAlchemy needs the psycopg v3 dialect, not psycopg2); add a table asset for `doc_chunks` and a whole-table batch definition; build an ExpectationSuite named `doc_chunks_v1` with five expectations: not-null on doc_id, embedding, model_version; row count between 100 and 10_000_000; chunk_text lengths between 1 and 8000; run the checkpoint and assert `result.success is True`. Mark it `@pytest.mark.integration`.

**What came back:** `test_great_expectations_suite.py` with `_FakeModel`, GX 1.x fluent datasource wiring (`add_postgres` → `add_table_asset` → `add_batch_definition_whole_table` → `ValidationDefinition` → `Checkpoint`), `import pgvector.sqlalchemy` to register the vector type with SQLAlchemy so GX can reflect the table, and all five expectations in the suite.
