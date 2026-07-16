# expense-api -- Cost & LLM Observability Notes

This document covers the cost-tracking infrastructure for
`expense-api`'s LLM path: what's actually deployed as of W6D4, how the
in-process cost middleware emits metrics, and what's still blocked.

## Stack layout

| Stack                  | Purpose                                            | Status |
|-------------------------|-----------------------------------------------------|--------|
| expense-network-dev     | VPC, subnets, NAT, app security group                | Deployed (W6D3) |
| expense-artifacts-dev   | Hardened artefact S3 bucket                          | Deployed (W6D3) |
| expense-app-dev         | RDS Postgres + Secrets Manager + DB security group   | Deployed (W6D3) |
| expense-cost-dev        | Bedrock IRSA, SNS, cost alarm, budget, CUR bucket    | **Not deployed** -- blocked, see below |

**Why expense-cost-dev doesn't exist yet:** it requires importing an
EKS OIDC provider ARN and issuer host from an EKS cluster. No EKS
cluster has been provisioned in this AWS account (confirmed via
`aws eks list-clusters` returning empty, and `aws iam
list-open-id-connect-providers` showing only the GitHub Actions OIDC
provider, no EKS one). Both Argo CD deployments in this cohort
currently target local k3d, not real EKS. This is a genuine
curriculum-sequencing gap, not a configuration mistake -- flagged to
the instructor/Code Coach separately.

## Per-request cost path (as actually implemented)

Note: this codebase has no standalone `llm-proxy` microservice. The
LLM call and cost tracking both live inside `expense-api` itself.

1. A client calls the `summarizeMerchant(id: ID!)` GraphQL mutation
   (`/graphql`, `permitAll()` -- no auth required).
2. `LlmSummaryService.summarize()` calls Spring AI's `ChatClient`
   against the configured model (`claude-sonnet-4-5`).
3. On response, `CostMiddleware.observe()` computes cost in
   `BigDecimal` with `RoundingMode.HALF_UP`, converts to `cost_usd_e5`
   as a `long` via `longValueExact()` (throws `ArithmeticException`
   on overflow rather than silently truncating), and `HINCRBY`s the
   daily tally in Redis via `RedisCostStore`. `HINCRBYFLOAT` is
   banned in this package -- a CI grep step in `ci.yml` fails the
   build if it appears anywhere in `llmproxy/cost/`.
4. `EmfEmitter` prints one EMF-format JSON document per call
   (dimensions `[[service, tenant, feature]]`) to stdout, intended
   for CloudWatch Logs to extract metrics from once deployed to a
   real cluster with the CloudWatch agent attached.

**Known simplification:** tenant is hardcoded to `"default"` in
`CallContext.defaultTenant()`. This codebase has no multi-tenant
concept anywhere (confirmed via `grep -rn tenant src/main/java/`
returning nothing prior to this work) -- per-tenant cost breakdown
isn't meaningful yet. Revisit when/if multi-tenancy is added.

**Known placeholder:** `PriceBook`'s price for `claude-sonnet-4-5`
(`$0.003` per 1k tokens) is not a verified real rate -- it's a
plausible placeholder pending confirmation of actual Bedrock/Anthropic
API pricing for the model in use.

## Mandatory tags (once expense-cost-dev is deployed)

| Tag      | Value for this stack   | Why                                  |
|----------|--------------------------|---------------------------------------|
| service  | `expense`                | drives per-service Budget filter      |
| env      | `dev` / `stg` / `prod`   | separates non-prod from prod signal   |
| tenant   | `shared` on infra resources | per-tenant resources set the real id |
| feature  | `categorize-expense`    | drives `$/feature` SLI                |

Tags must be activated in the AWS Billing console as cost-allocation
tags before they appear in Cost & Usage Reports; activation lag means
historical rows from before activation can never be backfilled. This
step can't be done until `expense-cost-dev` is actually deployed.

## What to do when the alarm fires (once deployed)

This section describes intended behavior; it has not been exercised
against a live alarm since `expense-cost-dev` doesn't exist yet.

1. Open the alarm in CloudWatch. State is `ALARM` (real spike) or
   `INSUFFICIENT_DATA` (cost pipeline dead -- check the pod's logs
   for EMF emission).
2. Drill the metric by tenant via Logs Insights to identify the
   offending tenant or feature.
3. If the budget hits 100% actual, `BudgetActionAttachDeny` should
   have attached a deny policy to the Bedrock invoke role; the proxy
   would then need to return an error until a human resets it.
4. Reset path: confirm with the service owner; detach the deny
   policy; tighten the budget or upstream rate-limit; then re-enable.

## Synthetic spike script

`scripts/llm-cost-spike.sh` sends repeated calls to
`summarizeMerchant` to drive cost up. **Not yet run for real** --
it needs a reachable `expense-api` instance and, to observe any
alarm transition, the (currently nonexistent) `CostPerRequestAlarm`
from `expense-cost-dev`. See inline comments in the script for the
tenant-tagging limitation.

## cost-author Skill audit

**Not performed.** No Claude Skill named `cost-author` exists in
this environment -- confirmed via `claude skills list` inside Claude
Code, which returned only Claude Code's built-in general-purpose
skills (code-review, verify, run, security-review, etc.), nothing
project- or cost-specific. This appears to be a curriculum/tooling
gap rather than a setup mistake on this machine. Flagged alongside
the `expense-cost-dev` EKS blocker.
