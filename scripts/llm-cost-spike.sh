#!/usr/bin/env bash
# scripts/llm-cost-spike.sh
#
# Synthetic spike: N calls to expense-api's summarizeMerchant GraphQL
# mutation, which triggers LlmSummaryService -> CostMiddleware. Intended
# to push the (not-yet-deployed) CostPerRequestAlarm into ALARM state
# once Task 1's expense-cost-dev stack exists.
#
# KNOWN LIMITATION: this codebase's CostMiddleware hardcodes tenant to
# "default" (see W6D4 Task 2 commit) -- there is no per-request tenant
# tagging yet. The curriculum's reference script sends an X-Tenant
# header assuming the proxy reads it; that header does nothing here,
# so it is deliberately omitted rather than left in as dead/misleading
# config. All spike cost will show up under tenant "default" until
# multi-tenancy is implemented.
#
# BLOCKED: this script cannot be meaningfully run yet. It targets a
# GraphQL endpoint that must be reachable (in-cluster or port-forwarded)
# AND requires Task 1's CostPerRequestAlarm to exist to observe any
# state transition. Neither exists in this account as of W6D4 (see
# COST.md and the Task 1 blocker notes).
set -euo pipefail

HOST="${HOST:-http://localhost:8080}"
MERCHANT_ID="${MERCHANT_ID:-merchant-001}"
CALLS="${CALLS:-200}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-1.5}"

QUERY='{"query":"mutation { summarizeMerchant(id: \"'"${MERCHANT_ID}"'\") { mccCode totalSpend transactionCount primaryCategory confidence } }"}'

echo "Starting spike against ${HOST}/graphql (merchantId=${MERCHANT_ID}, calls=${CALLS})"
for i in $(seq 1 "${CALLS}"); do
  curl -s -o /dev/null -X POST "${HOST}/graphql" \
    -H "Content-Type: application/json" \
    -d "${QUERY}"
  sleep "${INTERVAL_SECONDS}"
done

echo "Spike complete."
echo "Once Task 1's expense-cost-dev stack is deployed, verify with:"
echo "  aws cloudwatch describe-alarms --alarm-names expense/cost-per-request-dev"
