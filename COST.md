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
| expense-cost-dev        | Bedrock IRSA, SNS, cost alarm, budget, CUR bucket    | Deployed (W6D4 Task 1) |

**Update:** `expense-cost-dev` is now deployed. It required a real
EKS cluster's OIDC provider, which didn't exist anywhere in this
account when Task 1 was first attempted. Resolved by: building the
`cfn-author` Claude Skill from the W6D3 lecture's verbatim spec
(no install path existed anywhere in the curriculum materials),
using it to scaffold `cfn/expense-eks.yaml`, correcting it to import
the existing `expense-network-dev` stack's real subnets instead of a
duplicate generated VPC, and deploying a real EKS cluster
(`expense-dev`) with `t3.small` nodes (Free Tier restriction on this
account ruled out the originally-generated `t3.medium`). Full
narrative in commit history on `week06/day4/NishiS0205-cfn-author`.

Three genuine bugs were found in the assignment's own reference CFN
for `expense-cost-dev.yaml` while deploying it (all confirmed against
AWS's current documentation, not guessed): `ActionThreshold`'s
property names (`ActionThresholdType`/`Value` -> `Type`/`Value`),
`AWS::Budgets::BudgetsAction`'s `Subscriber` type using `Type` not
`SubscriptionType` (that name is only correct for the different
`AWS::Budgets::Budget` `Subscriber` type), and an invalid `AVG(m1)`
metric-math expression that CloudWatch rejects because `m1` is
already a time series, not a reducible scalar source.

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

## Mandatory tags

| Tag      | Value for this stack   | Why                                  |
|----------|--------------------------|---------------------------------------|
| service  | `expense`                | drives per-service Budget filter      |
| env      | `dev` / `stg` / `prod`   | separates non-prod from prod signal   |
| tenant   | `shared` on infra resources | per-tenant resources set the real id |
| feature  | `categorize-expense`    | drives `$/feature` SLI                |

Tags must be activated in the AWS Billing console as cost-allocation
tags before they appear in Cost & Usage Reports; activation lag means
historical rows from before activation can never be backfilled. Tag
activation itself is a separate manual step in the Billing console --
not yet done, since it isn't blocked by anything technical, just
hasn't been actioned.

## What to do when the alarm fires

This section describes the intended human response. The alarm's own
OK/ALARM state-transition mechanics ARE now verified for real -- see
"Synthetic spike script + alarm verification" below -- but the
downstream response steps here (drilling by tenant, confirming the
deny-policy attach, the reset path) have not themselves been
exercised end-to-end.

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

## Synthetic spike script + alarm verification

`scripts/llm-cost-spike.sh` sends repeated calls to
`summarizeMerchant` to drive cost up. It has **not** been run
against a real deployed instance of `expense-api` -- that would
require the app running inside the EKS cluster with a CloudWatch
agent forwarding EMF output, which is a real chunk of additional
work (Argo CD deployment, ServiceAccount IRSA annotation, agent
install) beyond what W6D4 covers.

**What was verified instead:** the actual deployed `CostPerRequestAlarm`
resource was exercised directly via `aws cloudwatch put-metric-data`,
injecting real datapoints into the real `acme/llmproxy/CostUsd`
metric (same namespace, same three dimensions the code emits) for
three real 5-minute evaluation periods -- first three points at
$0.01 (above the $0.005 threshold), then three at $0.002 (below it).
This is a direct metric injection against the real alarm resource,
not a live end-to-end request through the running app -- flagged
explicitly so this isn't overstated as more than it is.

**OK -> ALARM** (2026-07-17T00:19:08-04:00):

    {
        "State": "ALARM",
        "Reason": "Threshold Crossed: 3 out of the last 3 datapoints [0.01 (17/07/26 04:04:00), 0.01 (17/07/26 03:59:00), 0.01 (17/07/26 03:54:00)] were greater than the threshold (0.005) (minimum 3 datapoints for OK -> ALARM transition)."
    }

**ALARM -> OK** (2026-07-17T00:23:08-04:00):

    {
        "State": "OK",
        "Reason": "Threshold Crossed: 2 out of the last 3 datapoints [0.002 (17/07/26 04:13:00), 0.002 (17/07/26 04:08:00)] were not greater than the threshold (0.005) (minimum 1 datapoint for ALARM -> OK transition)."
    }

## cost-author Skill audit

`cost-author` did not exist in this environment when this task
was first attempted -- confirmed via `claude skills list`, which
returned only Claude Code's built-in general-purpose skills, none
project- or cost-specific. Unlike `cfn-author` (which had a
complete, verbatim `SKILL.md` quoted in the W6D3 lecture),
`cost-author` only had a documented inputs/outputs table, four
stated invariants, and one example output -- no full source file
anywhere in the curriculum materials searched.

A `SKILL.md` was reconstructed in good faith from that documented
behavior, following `cfn-author`'s structural pattern. It now
shows up in `claude skills list` and is usable, but it is a
reconstruction, not original curriculum text -- flagged to the
instructor alongside the missing-Skill report, with an offer to
swap in an official version if one exists.
