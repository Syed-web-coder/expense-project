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

---

## W7 D3 — 2026-07-19

### Prompt 1 — hybrid.py scaffolding

**Prompt given:**
> Write `src/expense_ai/hybrid.py` with `K_CONST=60`. Expose `dense_topk_filtered(conn, query_vec, tenant_id, metadata_filter=None, k=50, model_version="all-MiniLM-L6-v2")` (cosine ANN + optional `chunk_metadata @> %s::jsonb` filter, `@traceable retriever`); `sparse_topk_fts(conn, query_text, tenant_id, k=50)` (`websearch_to_tsquery` + `ts_rank_cd`, `@traceable retriever`); `rrf_fuse(dense, sparse, k_const=60, w_dense=1.0, w_sparse=1.0, top_k=60)` (rank-based RRF, **no score normalisation**, `@traceable chain`); and `coverage(dense, sparse) -> dict[str, float]` with dense_only/sparse_only/both/jaccard. Tests in `tests/test_hybrid_rrf.py` covering metadata filter, FTS keyword hit, disjoint-list union, and Jaccard range.

**What came back:** `hybrid.py` with `CROSS JOIN LATERAL (SELECT websearch_to_tsquery(...) AS tsq)` for FTS, rank-based RRF accumulation (`w/(k_const+rank)`) with no normalisation, and all four tests passing with Testcontainers Postgres.

---

### Prompt 2 — rerank.py scaffolding

**Prompt given:**
> Write `src/expense_ai/rerank.py` with `RERANKER_MODEL="BAAI/bge-reranker-base"`, `RERANK_TIMEOUT_MS=300`, `MMR_LAMBDA=0.7`. Expose `mmr_pick(query_vec, candidates, embedder, k=20, lambda_param=0.7)` (greedy MMR, `@traceable chain`, injectable embedder); `bge_rerank(query_text, candidates, top_k=6, timeout_ms=300, scorer=None)` (CrossEncoder lazy-loaded, `ThreadPoolExecutor` timeout, increments `RERANK_TIMEOUT_COUNT`, `@traceable chain`, injectable scorer). Tests in `tests/test_rerank.py` with four deterministic cases (lambda=1.0 cosine order, lambda=0.0 diversity, gold-chunk lift, timeout fallback), all using fakes — no model downloads.

**What came back:** `rerank.py` with the two functions; `test_rerank.py` using exact orthogonal basis vectors (not random noise) for deterministic cluster tests, and a `time.sleep(50ms)` scorer with `timeout_ms=1` for the timeout test.

---

### Prompt 3 — retrieve_and_generate, semantic cache, and Airflow DAG

**Prompt given:**
> Write `src/expense_ai/cache.py` (semantic cache: epoch-keyed bucket hash, `get_epoch`/`bump_epoch`/`cache_lookup`/`cache_store`, defence-in-depth citation tenant check). Rewrite `src/expense_ai/rag.py` keeping `retrieve_chunks` and adding `retrieve_and_generate(query_text, tenant_id, *, anthropic, conn, r, …, embedder=None, scorer=None)` that wires the full pipeline (embed → cache → dense → hybrid → mmr → rerank → generate → cache-store). Write `src/expense_ai/dags/rag_svc_ingest.py` as a TaskFlow DAG (Airflow 3.x, five `@task` functions, POSIX-only import guard). Add tests for semantic cache, tenant isolation, RAGAS gate (faithfulness hard-fail / others warn), and a retrieve_and_generate integration test that verifies cache hit on second call.

**What came back:** All three modules plus four test files. The retrieve_and_generate integration test uses a `_DeterministicEmbedder` (md5-hashed seed) so the same query produces the same embedding twice, enabling a real cache hit assertion (`fake_anthropic.call_count == 1` after two calls).

