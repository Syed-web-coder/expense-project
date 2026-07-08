#!/usr/bin/env bash
set -euo pipefail

STACK="${STACK:-expense-lambda-sandbox}"
REGION="${AWS_REGION:-us-east-1}"

# Derive stage name from stack name: expense-lambda-<stage>
STAGE="${STACK#expense-lambda-}"

echo "==> Deploying stack: $STACK  (stage: $STAGE)  region: $REGION"

sam deploy \
  --stack-name "$STACK" \
  --region "$REGION" \
  --resolve-s3 \
  --s3-prefix "$STACK" \
  --no-confirm-changeset \
  --no-fail-on-empty-changeset \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    StageName="$STAGE" \
    LambdaArchitecture=arm64 \
    FunctionTimeout=10

echo "==> Deploy complete."
