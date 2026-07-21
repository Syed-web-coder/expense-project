# W7 D5 Prompt Journal

Dated transcripts of design decisions made during the capstone session.
Each entry notes whether the model output was Used-as-is, Modified, or Rejected.

---

## 2026-07-21 — Session 1: State design (injectable deps)

**Prompt:**
> "Should injectable clients (retriever, anthropic, instructor, mcp_session) go into AgentState or
> config['configurable']? I need them available to all nodes."

**Model response (paraphrased):**
> Put them in AgentState as `NotRequired` fields with `__` prefix so they are optional and clearly
> marked as internal. Use `_get_dep(config, state, key)` to check config first, state second.

**Decision: Modified.**
We initially put all injectables in AgentState and got them working. Then discovered that
`PostgresSaver` serialises the *entire* state to JSON — including the `AsyncAnthropic` client
object, which is not JSON-serialisable. The correct pattern is:

- Serialisable primitive fields → `AgentState` (question, tenant_id, docs, answer, cost_usd_e5, visited_nodes)
- Non-serialisable clients → `config["configurable"]` only (never touch state after the graph is compiled)

The `_get_dep()` helper checks `config["configurable"]` first, then falls back to state for
backward compat. This pattern is now documented in the module docstrings and CLAUDE.md.

**Lesson:** LangGraph's `PostgresSaver` serialises state, not config. Anything not JSON-serialisable
must live in config only.

---

## 2026-07-21 — Session 2: RunnableConfig annotation

**Prompt:**
> "Ruff is flagging `Optional[RunnableConfig]` with UP045 (use `RunnableConfig | None` instead).
> Should I fix it?"

**Model response (paraphrased):**
> Yes, `X | None` is the modern union syntax (PEP 604). Apply globally for consistency.

**Decision: Rejected.**
Changing `Optional[RunnableConfig]` to `RunnableConfig | None` broke LangGraph's introspection.
LangGraph inspects node function signatures at compile time to detect whether a node accepts a
config argument. It matches the string `"Optional[RunnableConfig]"` in the annotation — the bare
`RunnableConfig | None` form is NOT matched, causing the config to be silently dropped and `_get_dep`
to always fall back to state (where the clients don't live).

Fix: Add `UP045` to ruff's `ignore` list in `pyproject.toml` with a comment explaining why.
We keep `Optional[RunnableConfig]` throughout all node signatures.

---

## 2026-07-21 — Session 3: eval faithfulness guard

**Prompt:**
> "The RAGAS faithfulness computation needs a real Anthropic key. How should I handle CI where
> the key isn't available?"

**Model response (paraphrased):**
> Use an environment variable guard: if `EXPENSE_AI_ANTHROPIC_API_KEY` is unset or equals
> `PLACEHOLDER`, record `faithfulness: null` and `faithfulness_skipped_reason` in the JSON output.
> The gate script treats `null` faithfulness as a skip (not a fail).

**Decision: Used-as-is.**
This pattern is already established in the `expense-ai` repo for the RAGAS gate. The eval gate
in `scripts/eval.py` only fails on `faithfulness < 0.85` when `faithfulness is not None`. When
the key is absent, the CI job reports `PASS` on trajectory alone, and the faithfulness column
in `evals/last_run.json` is `null` with a reason string. This lets trajectory CI run everywhere
while faithfulness only runs in environments with real credentials.
