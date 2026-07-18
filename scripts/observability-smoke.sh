#!/usr/bin/env bash
set -euo pipefail
NS="expense-dev"
APP="expense-api"
URL="http://${APP}.${NS}.svc.cluster.local:8080/merchants/mer_synth_001"

echo "[1/4] generating 20 requests against ${URL}"
kubectl run --rm -i --restart=Never -n "${NS}" smoke-curl \
    --image=curlimages/curl:8.7.1 -- \
    sh -c 'for i in $(seq 1 20); do curl --silent --output /dev/null -H "x-correlation-id: smoke-$i" '"${URL}"'; sleep 0.2; done'

echo "[2/4] Prometheus: checking http_server_requests_seconds_count"
PROM=$(kubectl -n observability get svc kube-prometheus-stack-prometheus -o jsonpath="{.metadata.name}")
kubectl -n observability run --rm -i --restart=Never promquery \
    --image=curlimages/curl:8.7.1 -- \
    sh -c "curl --silent --get \
        --data-urlencode 'query=sum(rate(http_server_requests_seconds_count{app=\"${APP}\",uri=\"/merchants/{merchantId}\"}[1m]))' \
        http://${PROM}:9090/api/v1/query | grep -E '\"value\"' >/dev/null"

echo "[3/4] Loki: checking for JSON log lines"
kubectl -n observability run --rm -i --restart=Never lokiquery \
    --image=curlimages/curl:8.7.1 -- \
    sh -c "curl --silent --get \
        --data-urlencode 'query={app=\"${APP}\"} |= \"lookup\"' \
        --data 'limit=5' \
        http://loki.observability.svc.cluster.local:3100/loki/api/v1/query \
        | grep -E 'lookup attempted' >/dev/null"

echo "[4/4] Tempo: checking for traces"
kubectl -n observability run --rm -i --restart=Never tempoquery \
    --image=curlimages/curl:8.7.1 -- \
    sh -c "curl --silent --get \
        --data-urlencode 'q={ resource.service.name=\"${APP}\" }' \
        http://tempo.observability.svc.cluster.local:3100/api/search \
        | grep -E 'traceID' >/dev/null"

echo "OK: metrics + logs + traces all visible for ${APP}."
