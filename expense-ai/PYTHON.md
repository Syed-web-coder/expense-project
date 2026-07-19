# PYTHON.md

## W7 D2 delta — deviations from typical Claude output

### (a) Fake deterministic encoder with explicit float64→float32 boundary cast

Tests in `test_corpus.py` use a `_FakeModel` that intentionally returns `NDArray[np.float64]` (not float32) so the test can assert that `embed_dataframe` casts the output to `float32` before storing it in `CorpusRow.embedding`. Typical generated code would either use the real `SentenceTransformer` (slow, requires network) or stub with float32 directly and never verify the cast. The explicit `assert row.embedding.dtype == np.float32` on every row catches a silent data-loss bug if the `.astype(np.float32)` line is ever dropped from `corpus.py`.

### (b) RAGAS test guarded by `pytest.mark.skipif` on missing Anthropic key

`tests/test_ragas_thresholds.py` checks `EXPENSE_AI_ANTHROPIC_API_KEY` at module load time and applies `pytestmark = pytest.mark.skipif(...)` rather than raising `pytest.skip()` inside the test body or failing with a missing-env-var error. This means `uv run pytest -v` in a CI environment without the secret emits a clear `SKIPPED` line rather than a red failure, and the overall suite stays green while the secret is not yet provisioned.

### (c) LangSmith visibility CI step conditioned on secret presence so forks stay green

---

## W7 D3 delta — deviations from typical Claude output

### (d) RRF uses rank-based scores, not weighted score fusion

`rrf_fuse` accumulates `w/(k_const + rank)` for each list, not a scaled or normalised version of the raw cosine/BM25 scores. Typical "hybrid search" implementations normalise each score list to [0,1] before combining, which introduces a scale assumption. Rank-based RRF is scale-invariant: a score of 0.99 and a score of 0.01 both get rank-1 treatment. The explicit `grep -RIn 'normalize.*score|min[_-]max'` gate in the task spec enforces this; the code has zero normalisation calls.

### (e) Reranker has a timeout-and-fallback path, not a blocking call

`bge_rerank` submits the CrossEncoder scoring to a `ThreadPoolExecutor` and calls `future.result(timeout=timeout_ms/1000)`. On `TimeoutError` it returns the candidates in their original order and increments `RERANK_TIMEOUT_COUNT`. This means the overall latency of `retrieve_and_generate` is bounded even if the reranker hangs, at the cost of degraded ranking quality for that request. The counter lets operators detect model performance regressions in production monitoring.

### (f) Tenant ID embedded in both the cache key and every citation

The semantic cache key includes `tenant_id` in the sha256 input (`_bucket_key`), ensuring tenant-A's cached response is never served for tenant-B's same query. Additionally, `cache_lookup` inspects every citation dict in the stored answer and returns `None` if any citation's `tenant_id` does not match the requesting tenant. This defence-in-depth prevents a stale or maliciously-crafted cache entry from leaking cross-tenant content even if the key lookup somehow succeeds.

### (g) Injectable anthropic/scorer/embedder parameters for test isolation

`retrieve_and_generate` accepts `embedder`, `scorer`, and `anthropic` as parameters so unit tests can inject fakes without downloading the 420 MB sentence-transformer or BGE reranker, and without making real Anthropic API calls. The `_DeterministicEmbedder` in `test_ragas_gate.py` seeds each embedding from an md5 hash of the input text, so the same query string always produces the same vector — making the cache-hit assertion (`call_count == 1` after two identical calls) deterministic.

### (h) Airflow DAG import is guarded by a `try/except ImportError`

`rag_svc_ingest.py` wraps all Airflow imports and DAG construction in a `try/except ImportError` block. On Windows (where `apache-airflow` is listed as a dev dependency but fails to install cleanly), `expense_ai_ingest_dag` is set to `None` and the module is still importable. `test_dag_import.py` is decorated with `@pytest.mark.skipif(sys.platform == "win32", ...)` so the full import + structure check only runs in CI (Ubuntu), where Airflow is available.

The `LangSmith run visibility` step in `.github/workflows/python-ci.yml` cannot use the expression `if: ${{ secrets.KEY != '' }}` directly (GitHub does not evaluate secret expressions in `if:` conditions for fork PRs — the secret is always empty there). Instead, a preceding `Set LangSmith key flag` step writes `HAS_LS_KEY=true/false` into `$GITHUB_ENV`, and the visibility step uses `if: env.HAS_LS_KEY == 'true'`. Fork pull requests therefore skip the step cleanly rather than erroring on an exit-code-1 script failure caused by a missing API key.
