# expense-ai

Python AI sidecar for the `expense-project` monorepo. Built in Week 7 Day 1 as a typed `uv` project; Days 2–5 of Week 7 extend it with NumPy/Pandas + LangSmith, RAG pipelines, an MCP server, and a LangGraph agent.

## Stack

| Tool | Version | Role |
|---|---|---|
| Python | 3.12 | Runtime |
| uv | latest | Package manager & virtualenv |
| Pydantic v2 | ≥ 2.7 | Data models with strict validation |
| pydantic-settings | ≥ 2.3 | `EXPENSE_AI_*` env-var config |
| FastAPI / httpx | wired in later weeks | API server & HTTP client |
| sentence-transformers | ≥ 3.0 | `all-mpnet-base-v2` embeddings |
| mypy | ≥ 1.10 | `--strict` type checking |
| ruff | ≥ 0.5 | Lint (E, F, I, UP, B) + format |
| pytest + pytest-cov | ≥ 8.2 | 44 tests, 100 % coverage |

## Project layout

```
expense-ai/
├── src/expense_ai/
│   ├── __init__.py
│   ├── models.py        # Merchant, DeductionClassifyRequest, DeductionClassifyResult
│   ├── value_types.py   # 5 frozen slotted dataclasses (EmbeddingVector, RetrievalHit, …)
│   ├── settings.py      # ExpenseAISettings (pydantic-settings, EXPENSE_AI_ prefix)
│   └── embeddings.py    # cosine_similarity(), top_k()
├── tests/
│   ├── test_models.py
│   ├── test_value_types.py
│   ├── test_settings.py
│   └── test_embeddings.py
├── schemas/             # JSON Schema files exported by scripts/export_schemas.py
├── notebooks/
│   └── embeddings_intro.ipynb   # W7D1 embeddings walkthrough
├── scripts/
│   └── export_schemas.py        # writes schemas/<ModelName>.json
└── pyproject.toml
```

## Setup

```bash
# Install uv (once)
curl -LsSf https://astral.sh/uv/install.sh | sh   # Linux/macOS
# or: winget install astral-sh.uv                  # Windows

# Install all dependencies into an isolated virtualenv
uv sync --frozen
```

## Running checks

Single one-liner that mirrors CI:

```bash
uv run ruff check && \
uv run ruff format --check && \
uv run mypy src/ tests/ scripts/ && \
uv run pytest -v --cov=src --cov-fail-under=85 && \
MPLBACKEND=Agg uv run jupyter nbconvert \
  --to notebook --execute notebooks/embeddings_intro.ipynb \
  --output executed.ipynb --ExecutePreprocessor.timeout=600
```

Expected result: **44 passed, 100 % coverage, mypy strict clean, notebook executes headless.**

## Environment variables

`ExpenseAISettings` reads from the environment with the `EXPENSE_AI_` prefix.

| Variable | Default | Notes |
|---|---|---|
| `EXPENSE_AI_API_KEY` | *(required)* | `SecretStr` — **never commit this value** |
| `EXPENSE_AI_LLM_PROXY_URL` | `http://localhost:8080` | Upstream LLM proxy |
| `EXPENSE_AI_REQUEST_TIMEOUT_S` | `10.0` | Per-request timeout in seconds |
| `EXPENSE_AI_LOG_LEVEL` | `INFO` | Standard Python log level |

Copy `.env.example` (not included) to `.env` and set `EXPENSE_AI_API_KEY`. The `.gitignore` excludes `.env`.

## Notebook — `embeddings_intro.ipynb`

Demonstrates semantic embeddings over an expense transaction corpus using `sentence-transformers/all-mpnet-base-v2`:

| Cell | Content |
|---|---|
| 1 | Headless-safe imports (`matplotlib.use("Agg")` before pyplot) |
| 2 | Load model, encode 10 expense sentences (coffee, SaaS, flights, hotels, …) |
| 3 | 2-D PCA scatter with per-point labels → `notebooks/output/pca_scatter.png` |
| 4 | 10×10 cosine-similarity heatmap → `notebooks/output/cosine_heatmap.png` |
| 5 | Top-3 retrieval bar chart for a sample query → `notebooks/output/top3_retrieval.png` |

Run headless (model downloads ~420 MB on first run, then cached in `~/.cache/huggingface`):

```bash
MPLBACKEND=Agg uv run jupyter nbconvert \
  --to notebook --execute notebooks/embeddings_intro.ipynb \
  --output executed.ipynb --ExecutePreprocessor.timeout=600
```

## CI — `.github/workflows/python-ci.yml`

Workflow name **`python-ci`**. Triggers on **pushes and PRs to `main`** when any file under `expense-ai/**` changes (paths filter keeps Java/web CI unaffected).

Steps (all run with `working-directory: expense-ai`):

1. Checkout
2. `astral-sh/setup-uv@v5` with `enable-cache: true` (caches venv by lock-file hash)
3. `uv python install 3.12`
4. `uv sync --frozen`
5. `uv run ruff check`
6. `uv run ruff format --check`
7. `uv run mypy src/ tests/ scripts/`
8. `uv run pytest -v --cov=src --cov-fail-under=85`
9. `actions/cache@v4` — caches `~/.cache/huggingface` keyed on model name (avoids 420 MB re-download)
10. Execute notebook with `MPLBACKEND=Agg`
11. *(W7D2)* `uv run pytest -v tests/test_great_expectations_suite.py` — GX checkpoint via Testcontainers
12. *(W7D2)* `uv run pytest -v tests/test_ragas_thresholds.py` — skipped when `EXPENSE_AI_ANTHROPIC_API_KEY` is unset
13. *(W7D2)* `uv run python -m expense_ai.scripts.assert_langsmith_run_visible` — only runs when `EXPENSE_AI_LANGSMITH_API_KEY` is set (fork-safe via `$GITHUB_ENV` flag)

## Week 7 Day 2 additions

### New modules

| Module | Purpose |
|---|---|
| `src/expense_ai/corpus.py` | `load_corpus(path)` — reads Parquet/JSONL, deduplicates on `(doc_id, chunk_idx)`, drops rows with `chunk_text` outside [1, 8000] chars; `embed_dataframe(df, model)` — batched MiniLM encode, casts output to `float32` |
| `src/expense_ai/pgvector_loader.py` | `load_rows(dsn, rows)` — psycopg v3 + `register_vector`, upserts into `doc_chunks` via `ON CONFLICT (doc_id, chunk_idx, model_version) DO UPDATE`, returns row count |
| `src/expense_ai/rag.py` | `retrieve_chunks(dsn, query, k, tenant_id)` — cosine ANN query against pgvector, decorated with `@traceable(name="expense_ai.retrieve_chunks")` for LangSmith |
| `src/expense_ai/scripts/assert_langsmith_run_visible.py` | Spins a Testcontainers Postgres, seeds rows, calls `retrieve_chunks`, polls the LangSmith API and exits non-zero if the named run is not found |
| `sql/V001__doc_chunks.sql` | `doc_chunks` table — `vector(384)` column, HNSW cosine index (`m=16`, `ef_construction=64`), composite unique constraint `(doc_id, chunk_idx, model_version)` |

### New environment variables

Copy `.env.example` to `.env` and fill in the values. The `.gitignore` already excludes `.env`.

| Variable | Default | Notes |
|---|---|---|
| `EXPENSE_AI_LANGSMITH_API_KEY` | *(optional)* | LangSmith API key — enables tracing and the visibility check |
| `EXPENSE_AI_LANGSMITH_PROJECT` | `expense-ai-dev` | LangSmith project name |
| `EXPENSE_AI_PG_DSN` | *(optional)* | PostgreSQL DSN for `load_rows` / `retrieve_chunks` when not using Testcontainers |
| `EXPENSE_AI_ANTHROPIC_API_KEY` | *(optional)* | Anthropic API key — required to run the RAGAS threshold test |

### Running the LangSmith visibility check locally

The script spins its own Testcontainers Postgres instance, so Docker must be running. It requires `LANGSMITH_API_KEY` (or `EXPENSE_AI_LANGSMITH_API_KEY`) and `LANGSMITH_TRACING=true`:

```bash
export LANGSMITH_API_KEY=<your-key>
export LANGSMITH_PROJECT=expense-ai-dev   # or any project name
export LANGSMITH_TRACING=true
uv run python -m expense_ai.scripts.assert_langsmith_run_visible
```

A successful run prints:

```
SUCCESS: found 1 run(s) named 'expense_ai.retrieve_chunks' in LangSmith project 'expense-ai-dev'
```

and exits `0`. If no run is found within the search window it exits `1` with an error message on stderr.

## Week 7 Day 3 additions

### New modules

| Module | Purpose |
|---|---|
| `src/expense_ai/chunker.py` | `make_splitter(chunk_size, overlap)` + `chunk_docs(docs)` — LangChain `RecursiveCharacterTextSplitter`, stamps `chunk_id` and `chunk_ordinal` metadata |
| `src/expense_ai/hybrid.py` | `dense_topk_filtered`, `sparse_topk_fts` (FTS via `websearch_to_tsquery`), `rrf_fuse` (rank-based RRF), `coverage` — all `@traceable` |
| `src/expense_ai/rerank.py` | `mmr_pick` (greedy MMR, injectable embedder), `bge_rerank` (CrossEncoder, `ThreadPoolExecutor` timeout-and-fallback, `RERANK_TIMEOUT_COUNT`) — all `@traceable` |
| `src/expense_ai/cache.py` | Epoch-keyed semantic cache over Redis: `_bucket_key` (sha256 of `round(vec*100).int32`), `get_epoch`/`bump_epoch`, `cache_lookup` (with cross-tenant citation defence), `cache_store` |
| `src/expense_ai/rag.py` | **Extended** — keeps `retrieve_chunks`; adds `retrieve_and_generate(…, anthropic, conn, r)` wiring the full hybrid→MMR→rerank→generate→cache pipeline; all clients injectable for tests |
| `src/expense_ai/dags/rag_svc_ingest.py` | Airflow 3.x TaskFlow DAG (`expense_ai_ingest`) with five tasks: `load_docs → chunk_docs → embed_chunks → upsert_chunks → bump_cache_epochs`; POSIX-only, guarded by `try/except ImportError` |
| `sql/V002__rag2_metadata_and_partial_indexes.sql` | Adds `chunk_metadata jsonb`, `content_hash text`, `chunk_tsv tsvector GENERATED ALWAYS AS (to_tsvector(...)) STORED`; GIN indexes on metadata and tsv; per-tenant partial HNSW indexes (`m=24`, `ef_construction=128`) |

### New environment variable

| Variable | Default | Notes |
|---|---|---|
| `EXPENSE_AI_REDIS_URL` | `redis://localhost:6379` | Redis DSN for semantic cache and Airflow `bump_cache_epochs` task |

### Feature flags in `retrieve_and_generate`

| Flag | Default | Effect when `False` |
|---|---|---|
| `use_hybrid` | `True` | Skip sparse FTS; use dense-only top-60 |
| `use_mmr` | `True` | Skip MMR; use top-20 from fused list |
| `use_rerank` | `True` | Skip BGE rerank; use top-6 from diversified list |
| `use_filter` | `True` | Ignore `metadata_filter`; no `chunk_metadata @>` predicate |

### Running the new tests

```bash
# Semantic cache (Testcontainers Redis)
uv run pytest -v tests/test_semantic_cache.py

# Tenant isolation (Testcontainers Postgres)
uv run pytest -v tests/test_tenant_isolation.py

# retrieve_and_generate integration (fake anthropic + Testcontainers)
uv run pytest -v tests/test_ragas_gate.py::test_retrieve_and_generate_shapes_and_caches

# RAGAS faithfulness gate (requires EXPENSE_AI_ANTHROPIC_API_KEY)
EXPENSE_AI_ANTHROPIC_API_KEY=<key> uv run pytest -v -m slow tests/test_ragas_gate.py

# DAG import (Linux/macOS only — skipped on Windows)
uv run pytest -v tests/test_dag_import.py
```
