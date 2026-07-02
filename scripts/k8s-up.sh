#!/usr/bin/env bash
# scripts/k8s-up.sh - one-shot bring-up for the k3d cluster + manifests.
# Idempotent: re-running just re-applies; no state lost.
set -euo pipefail

CLUSTER="expense"
NAMESPACE="expense-dev"
TAG="${TAG:-0.1.0}"
IMAGE="uptimecrew/expense-api:${TAG}"

# 1. Cluster (create only if missing). Traefik disabled; we install
#    the real NGINX ingress controller below, matching manifests/60-*.
if ! k3d cluster list | awk 'NR>1 { print $1 }' | grep -qx "${CLUSTER}"; then
  echo "Creating k3d cluster ${CLUSTER}..."
  k3d cluster create "${CLUSTER}" \
    --servers 1 --agents 2 \
    --port "8080:80@loadbalancer" \
    --k3s-arg "--disable=traefik@server:0"
fi

# 2. Install NGINX ingress controller if not already present.
if ! kubectl get deploy ingress-nginx-controller -n ingress-nginx >/dev/null 2>&1; then
  echo "Installing NGINX ingress controller..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.3/deploy/static/provider/baremetal/deploy.yaml
  kubectl patch svc ingress-nginx-controller -n ingress-nginx -p '{"spec":{"type":"LoadBalancer"}}'
  kubectl rollout status deploy/ingress-nginx-controller -n ingress-nginx --timeout=2m
fi

# 3. Import the locally-built image into the cluster's containerd cache
#    so nodes don't need a registry round-trip.
echo "Importing ${IMAGE} into ${CLUSTER}..."
k3d image import "${IMAGE}" -c "${CLUSTER}"

# 4. Apply the manifest tree in lexical order (00-, 10-, 20-, ...),
#    including the local-only throwaway Postgres/Mongo (95-*, gitignored).
echo "Applying manifests/..."
kubectl apply -f manifests/

# 5. Wait for Postgres/Mongo, then seed the schema (idempotent-ish;
#    V2's deliberately-bad row always errors, that's expected).
kubectl rollout status deploy/postgres -n "${NAMESPACE}" --timeout=2m
kubectl rollout status deploy/mongo -n "${NAMESPACE}" --timeout=2m
POD=$(kubectl get pod -l app.kubernetes.io/name=postgres -n "${NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
for f in db/V1__schema.sql db/V2__seed.sql db/V3__outbox.sql db/V4__transaction_idempotency_key.sql; do
  kubectl exec -i "${POD}" -n "${NAMESPACE}" -- psql -U expense_dev -d expense_dev < "${f}" || true
done

# 6. Block until the app rollout completes (or fail loudly).
kubectl rollout status deploy/expense-api \
  -n "${NAMESPACE}" --timeout=5m

echo ""
echo "Cluster ready. Reach the service through the Ingress:"
echo "  curl -H 'Host: expense.dev.uptimecrew.internal' http://localhost:8080/actuator/health/readiness"
