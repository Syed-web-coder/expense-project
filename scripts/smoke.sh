#!/usr/bin/env bash
set -euo pipefail

PROJECT="expense_dev_smoke_$$"
COMPOSE="docker compose -p $PROJECT"

cleanup() {
  $COMPOSE down -v --remove-orphans 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Bringing up stack..."
$COMPOSE up -d --wait --wait-timeout 90

echo "==> Checking health..."
$COMPOSE ps

echo "==> Hitting actuator health..."
curl -sf http://localhost:8080/actuator/health | grep -q "UP"

echo "==> Smoke test passed."
