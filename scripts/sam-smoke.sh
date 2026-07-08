#!/usr/bin/env bash
set -euo pipefail

STACK="${STACK:-expense-lambda-sandbox}"
REGION="${AWS_REGION:-us-east-1}"

echo "==> Resolving HttpApiUrl for stack: $STACK"
API_URL=$(aws cloudformation describe-stacks \
  --stack-name "$STACK" \
  --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='HttpApiUrl'].OutputValue" \
  --output text)
echo "    API URL: $API_URL"

# Capture a timestamp (ms) before the first request so the log search is bounded
LOG_START=$(( $(date +%s) * 1000 - 60000 ))

# --- known-good path: accept 200 or 404 ---
echo "==> GET /merchants/mer_synth_001  (expecting 200 or 404)..."
STATUS=$(curl -sf -o /tmp/sam-smoke-body -w "%{http_code}" \
  "$API_URL/merchants/mer_synth_001" || true)
echo "    HTTP $STATUS"
[[ "$STATUS" == "200" || "$STATUS" == "404" ]] || {
  echo "ERROR: unexpected status $STATUS for /merchants/mer_synth_001" >&2
  exit 1
}

# --- unrouted path: must 404 ---
echo "==> GET /merchants/  (expecting 404)..."
STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
  "$API_URL/merchants/" || true)
echo "    HTTP $STATUS"
[[ "$STATUS" == "404" ]] || {
  echo "ERROR: expected 404 for /merchants/, got $STATUS" >&2
  exit 1
}

# --- CloudWatch: recent REPORT line ---
FUNCTION_NAME=$(aws cloudformation describe-stacks \
  --stack-name "$STACK" \
  --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='FunctionName'].OutputValue" \
  --output text)

LOG_GROUP="/aws/lambda/$FUNCTION_NAME"
echo "==> Checking $LOG_GROUP for a recent REPORT line..."

FOUND=false
for i in 1 2 3 4; do
  REPORT=$(aws logs filter-log-events \
    --log-group-name "$LOG_GROUP" \
    --filter-pattern "REPORT" \
    --start-time "$LOG_START" \
    --region "$REGION" \
    --query "events[0].message" \
    --output text 2>/dev/null || true)
  if [[ -n "$REPORT" && "$REPORT" != "None" ]]; then
    FOUND=true
    echo "    $REPORT"
    break
  fi
  echo "    (attempt $i — waiting 5 s for logs to flush)"
  sleep 5
done

$FOUND || { echo "ERROR: no recent REPORT line found in $LOG_GROUP" >&2; exit 1; }
echo "==> Smoke test passed."
