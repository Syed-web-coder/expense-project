#!/usr/bin/env bash
# scripts/w6d5-integration-spike.sh
# TASK 4 (W6D5): posts 4000 synthetic events to expense-ingest-dev
# so KEDA scales expense-api-worker from 0. Tenant tag is
# "tenant-synth" so the W6D4 cost pipeline can subtract these from
# real spend. Genuinely runnable -- unlike loadtests/expense-api-p99.js,
# this only needs the real SQS queue + ScaledObject, both of which
# exist and are verified working (see W6D5 Task 1/2 commit messages).
set -euo pipefail

QUEUE_URL="${QUEUE_URL:-https://sqs.us-east-1.amazonaws.com/$(aws sts get-caller-identity --query Account --output text)/expense-ingest-dev}"
TENANT="tenant-synth"
FEATURE="categorize-expense"
COUNT="${COUNT:-4000}"

echo "Posting ${COUNT} synthetic events to ${QUEUE_URL}"
echo "tenant=${TENANT} feature=${FEATURE}"

for i in $(seq 1 "${COUNT}"); do
  aws sqs send-message --queue-url "${QUEUE_URL}" \
    --message-body "{\"tenant\":\"${TENANT}\",\"feature\":\"${FEATURE}\",\"merchantId\":\"synth-${i}\"}" \
    --region us-east-1 \
    >/dev/null
  if (( i % 250 == 0 )); then
    echo "  posted ${i} / ${COUNT}"
  fi
done

echo "Spike posted. Now watch:"
echo "  kubectl -n expense-dev get scaledobject expense-worker-scaledobject -w"
echo "  kubectl -n expense-dev get pods -l app=expense-api-worker -w"
echo "Expect KEDA to scale worker's DESIRED replica count up based on"
echo "queue depth. NOTE: worker pods will show ImagePullBackOff (known"
echo "gap from W6D5 Task 1 -- the image is a local-k3d-only tag never"
echo "pushed to a real registry). KEDA's replica-count management is"
echo "still genuinely observable via the Deployment's replica count,"
echo "even though pods never reach Running."
