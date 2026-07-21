# expense-agent-svc On-Call Runbook

## Top-5 Signals

---

### 1. synthesis cost p99 breach

**Alert:** `synthesis_agent_duration_p99 > 8s` or `cost_usd_e5 per request > 50000`

**Triage (0–30 min)**
- Check LangSmith project for recent traces — look for `synthesis_agent` spans with high token counts.
- Verify `EXPENSE_AGENT_REQUEST_BUDGET_USD_E5` is set correctly (default 25000 = $0.25).
- Check if a prompt-injection attempt is inflating context: look at `docs` and `tool_results` sizes in the trace.

**Stabilise (30–60 min)**
- Lower `request_budget_usd_e5` temporarily via config bump to shed overspend.
- If a specific tenant is responsible, rate-limit at the API gateway level.
- Roll back any recent `_build_system_prompt` changes that expanded the context window.

**Resolve (60–90 min)**
- Root-cause the token spike (bad retrieval hitting unrelated docs, MCP tool returning large payloads).
- Add a `max_tokens` cap to `synthesis_agent` if not already in place.
- Update the RAGAS golden set with the offending question to catch regressions in CI.

---

### 2. retrieval p99 over deadline

**Alert:** `retrieval_agent_duration_p99 > 3s` — the `@deadline(seconds=3.0)` sentinel fires and `docs=[]` reaches synthesis.

**Triage (0–30 min)**
- Check Postgres query latency on `doc_chunks` — run `EXPLAIN ANALYZE` on the pgvector HNSW index scan.
- Check embedding model latency (`sentence-transformers/all-MiniLM-L6-v2`) via LangSmith spans.
- Verify Postgres connection pool isn't exhausted (`pg_stat_activity` idle-in-transaction count).

**Stabilise (30–60 min)**
- Increase HNSW `ef_search` if the index was recently rebuilt with lower `ef_construction`.
- Temporarily reduce retrieval `k` from 8 to 4 to halve embedding distance computations.
- Add a read replica and route `retrieve_chunks` to it if primary is under write pressure.

**Resolve (60–90 min)**
- Vacuum and reindex `doc_chunks` if index bloat is the root cause.
- Tune `ef_search` parameter and document the change in `expense-ai/README.md`.
- Re-run the CI golden set to confirm p50 latency is back below 1 s.

---

### 3. RAGAS 7-day median faithfulness drop > 0.10

**Alert:** Weekly RAGAS cron reports `faithfulness_7d_median < (prior_7d_median - 0.10)`.

**Triage (0–30 min)**
- Pull the `evals/last_run.json` artifacts from CI for the last 7 days and diff the per-scenario results.
- Identify which question categories degraded (docs-only vs api-only vs both).
- Check for recent changes to `synthesis_agent`'s system prompt or `FinalAnswer` schema.

**Stabilise (30–60 min)**
- Revert any system-prompt change if it correlates with the drop date.
- If a schema change broke citation extraction, roll back the `FinalAnswer` model change.
- Re-pin the `anthropic` package version if a model version bump introduced regression.

**Resolve (60–90 min)**
- Expand the golden set with the failing scenarios.
- Add a faithfulness assertion to `test_trajectory_eval.py` when a key is available in CI.
- Document the incident in `PROMPT_JOURNAL.md`.

---

### 4. BudgetAction fired (AWS Budgets action applied DenyLlmProxyInvoke policy)

**Alert:** AWS SNS notification from `expense-agent-anthropic-monthly` budget at 100%.  
IAM policy `DenyLlmProxyInvoke` has been auto-applied to `expense-agent-svc-role`.

**Triage (0–30 min)**
- Confirm the IAM action in AWS Console → Budgets → `expense-agent-anthropic-monthly` → Actions.
- Check CloudWatch Logs for `budget_exceeded` 503 responses hitting the API.
- Identify the tenant driving the spend spike via LangSmith or API gateway access logs.

**Stabilise (30–60 min)**
- Remove the IAM policy manually if service restoration is prioritised over cost control: `aws iam detach-role-policy --role-name expense-agent-svc-role --policy-arn <arn>`.
- Rate-limit the offending tenant at the API gateway level.
- Raise a Jira ticket for quota review with finance.

**Resolve (60–90 min)**
- Adjust monthly budget threshold if legitimate usage has grown.
- Lower `request_budget_usd_e5` per-request ceiling to reduce blast radius per call.
- Add a per-tenant cost attribution tag to LangSmith traces for future forensics.

---

### 5. Argo CD OutOfSync

**Alert:** Argo CD reports `expense-agent-svc` application as `OutOfSync` for > 5 minutes.

**Triage (0–30 min)**
- `argocd app diff expense-agent-svc` — identify what diverged (image tag, ConfigMap, Deployment replicas).
- Check if the platform-config repo has a conflicting commit (e.g., manual kubectl edit that Argo CD is trying to revert).
- Verify the Argo CD application is still `Automated` with `prune=true selfHeal=true`.

**Stabilise (30–60 min)**
- If the drift is expected (e.g., temporary manual scale-down), suspend auto-sync: `argocd app set expense-agent-svc --sync-policy none`.
- If the drift is unintended, force sync: `argocd app sync expense-agent-svc`.
- Check Argo CD controller logs for resource-hook or webhook errors.

**Resolve (60–90 min)**
- Merge the desired state change into `platform-config` main and let Argo CD re-sync.
- Re-enable auto-sync if it was suspended.
- If selfHeal is fighting a persistent manual change, add the resource to the `ignoreDifferences` list in `argo-apps/expense-agent-svc.yaml`.
