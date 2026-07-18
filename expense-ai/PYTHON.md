# PYTHON.md

## W7 D2 delta — deviations from typical Claude output

### (a) Fake deterministic encoder with explicit float64→float32 boundary cast

Tests in `test_corpus.py` use a `_FakeModel` that intentionally returns `NDArray[np.float64]` (not float32) so the test can assert that `embed_dataframe` casts the output to `float32` before storing it in `CorpusRow.embedding`. Typical generated code would either use the real `SentenceTransformer` (slow, requires network) or stub with float32 directly and never verify the cast. The explicit `assert row.embedding.dtype == np.float32` on every row catches a silent data-loss bug if the `.astype(np.float32)` line is ever dropped from `corpus.py`.

### (b) RAGAS test guarded by `pytest.mark.skipif` on missing Anthropic key

`tests/test_ragas_thresholds.py` checks `EXPENSE_AI_ANTHROPIC_API_KEY` at module load time and applies `pytestmark = pytest.mark.skipif(...)` rather than raising `pytest.skip()` inside the test body or failing with a missing-env-var error. This means `uv run pytest -v` in a CI environment without the secret emits a clear `SKIPPED` line rather than a red failure, and the overall suite stays green while the secret is not yet provisioned.

### (c) LangSmith visibility CI step conditioned on secret presence so forks stay green

The `LangSmith run visibility` step in `.github/workflows/python-ci.yml` cannot use the expression `if: ${{ secrets.KEY != '' }}` directly (GitHub does not evaluate secret expressions in `if:` conditions for fork PRs — the secret is always empty there). Instead, a preceding `Set LangSmith key flag` step writes `HAS_LS_KEY=true/false` into `$GITHUB_ENV`, and the visibility step uses `if: env.HAS_LS_KEY == 'true'`. Fork pull requests therefore skip the step cleanly rather than erroring on an exit-code-1 script failure caused by a missing API key.
