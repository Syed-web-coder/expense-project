#!/usr/bin/env bash
set -euo pipefail
NS="expense-dev"
APP="expense-api"
SLOTH_SPEC="slo/expense-api.sloth.yaml"
DASH_JSON=".grafana/dashboards/${APP}-red.json"

kubectl get ns observability >/dev/null

sloth generate -i "${SLOTH_SPEC}" -o manifests/observability/${APP}-prometheusrule.yaml
git diff --quiet -- manifests/observability/${APP}-prometheusrule.yaml || {
    echo "ERROR: PrometheusRule drifted from Sloth spec. Re-commit the regenerated file." >&2
    git --no-pager diff -- manifests/observability/${APP}-prometheusrule.yaml >&2
    exit 1
}

kubectl run --rm -i --restart=Never promtool-check \
    --image=prom/prometheus:v2.54.0 -- \
    promtool check rules /dev/stdin \
    < manifests/observability/${APP}-prometheusrule.yaml

kubectl -n observability create configmap "${APP}-grafana-dashboard" \
    --from-file="${APP}-red.json=${DASH_JSON}" \
    --dry-run=client -o yaml \
    | kubectl label --local -f - --dry-run=client -o yaml \
        grafana_dashboard=1 app.kubernetes.io/name="${APP}" \
    | kubectl apply -f -

kubectl apply -n "${NS}" -f manifests/observability/${APP}-servicemonitor.yaml
kubectl patch -n "${NS}" deployment "${APP}" \
    --patch-file manifests/observability/${APP}-deployment-patch.yaml
kubectl apply -n "${NS}" -f manifests/observability/${APP}-prometheusrule.yaml
kubectl apply -n "${NS}" -f manifests/observability/${APP}-alertmanagerconfig.yaml

kubectl -n "${NS}" rollout status deployment/${APP} --timeout=180s
echo "OK: ${APP} now scraped, traced, and alarming."
