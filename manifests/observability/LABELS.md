# Loki Label Discipline

## Labels the service emits

expense-api writes four Loki labels. `app` and `env` come from the `customFields` block in `logback-spring.xml` and are static per deployment — `app` is always `expense-api` and `env` is always `k8s` in the prod/k8s profile, giving Loki a low-cardinality stream key that lets operators filter to this service instantly. `level` (e.g. `INFO`, `WARN`, `ERROR`) is added automatically by Grafana Alloy's `loki.source.kubernetes` component when it reads the structured JSON field. `pod` is the Kubernetes pod name, also injected by Alloy from the pod's own metadata; it lets operators narrow a query to a single replica without writing a log-line filter.

## Identifiers excluded from labels

`merchantId`, `correlationId`, and the requesting user's id are logged as JSON fields inside the log line body but are deliberately never promoted to Loki stream labels. Each one is a cardinality bomb: `merchantId` has one distinct value per merchant in the system, meaning Loki would need to maintain a separate indexed stream for every merchant — unbounded stream growth that causes index bloat and query timeouts. `correlationId` is generated fresh per HTTP request, so its cardinality equals total request count and would create millions of streams within hours. The requesting user's id has the same per-user, per-session unboundedness. Loki is designed for a small, stable set of labels (single digits); these three belong in the log payload where `|= "value"` line filters — not label matchers — are used to find them at query time.
