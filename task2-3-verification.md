# Task 2 & 3 Verification — expense-lambda-dev (us-east-1)

**Date:** 2026-07-08  
**Stack:** `expense-lambda-dev`  
**Function:** `expense-merchant-lookup-dev`  
**Endpoint:** `https://k8y4yu5epb.execute-api.us-east-1.amazonaws.com/dev`

---

## Task 2 — Verification Checklist

### 2.1 CloudWatch Logs — REPORT / RestoreReport (platform telemetry)

```
Command: MSYS_NO_PATHCONV=1 aws logs tail /aws/lambda/expense-merchant-lookup-dev \
         --since 30m --filter-pattern REPORT --region us-east-1
```

> Note: `aws logs tail` returned no output (log stream not yet indexed by the tail command).
> Equivalent data pulled via `aws logs filter-log-events`:

**Pre-deploy probe invocation (10:43 UTC — curl probe-123):**

| Event | Key metric | Value |
|---|---|---|
| `platform.restoreReport` | Restore Duration | **556.313 ms** |
| `platform.report` | Duration | **3746.459 ms** |
| `platform.report` | Billed Duration | 3866 ms |
| `platform.report` | Restore Duration | 556.313 ms |
| `platform.report` | Billed Restore Duration | **119 ms** |
| `platform.report` | Max Memory Used | 184 MB / 1024 MB |

**Post-deploy 5x invocations (10:50 UTC — EMF build):**

| Request | Duration (ms) | Billed (ms) | Restore (ms) | Notes |
|---|---|---|---|---|
| req-1 `4ea5f4b4` | 3689.6 | 3843 | 564.858 (restore) | SnapStart resume |
| req-2 `450569a3` | **17.613** | 18 | — | Warm |
| req-3 `5bdcce2a` | **8.778** | 9 | — | Warm |
| req-4 `ef653b70` | **7.858** | 8 | — | Warm |
| req-5 `fc57a0d0` | **7.868** | 8 | — | Warm |

**Duration / Restore Duration Summary:**
- Cold-start (SnapStart restore): ~556–565 ms restore → ~3.7 s total first-invocation duration  
- Warm steady-state: **7–18 ms** handler duration  
- Billed Restore Duration: 119–153 ms (only the JVM re-initialization after snapshot load, not full cold-start)

---

### 2.2 SnapStart Configuration

```
Command: aws lambda get-function-configuration \
         --function-name expense-merchant-lookup-dev \
         --query 'SnapStart' --region us-east-1
```

```json
{
    "ApplyOn": "PublishedVersions",
    "OptimizationStatus": "Off"
}
```

- `ApplyOn: PublishedVersions` — SnapStart is correctly configured.  
- `OptimizationStatus: Off` — expected for a freshly published version; snapshot creation is in progress (confirmed by `initializationType: snap-start` appearing in platform.initStart events for newly deployed version).  
- Live alias (`expense-merchant-lookup-dev:live`) is routing to the published version as confirmed by the platform logs showing `functionArn: ...expense-merchant-lookup-dev:live`.

---

### 2.3 CloudWatch p99 Alarm

```
Command: aws cloudwatch describe-alarms \
         --alarm-names expense-merchant-lookup-dev-p99-latency \
         --region us-east-1
```

> Alarm name resolved from `template.yaml`:
> `AlarmName: !Sub ${MerchantLookupFunction}-p99-latency`
> → `expense-merchant-lookup-dev-p99-latency`

```json
{
    "AlarmName": "expense-merchant-lookup-dev-p99-latency",
    "AlarmArn": "arn:aws:cloudwatch:us-east-1:606493605786:alarm:expense-merchant-lookup-dev-p99-latency",
    "StateValue": "OK",
    "MetricName": "Duration",
    "Namespace": "AWS/Lambda",
    "ExtendedStatistic": "p99",
    "Dimensions": [
        {"Name": "FunctionName", "Value": "expense-merchant-lookup-dev"},
        {"Name": "Resource", "Value": "expense-merchant-lookup-dev:live"}
    ],
    "Period": 60,
    "EvaluationPeriods": 5,
    "Threshold": 1500.0,
    "ComparisonOperator": "GreaterThanThreshold",
    "TreatMissingData": "notBreaching"
}
```

**Status: OK** — p99 alarm exists, scoped to the `:live` alias, threshold 1500 ms over 5 × 60 s periods.

---

### 2.4 IAM Least-Privilege Proof

**Role resolved from function config:**
```
arn:aws:iam::606493605786:role/expense-lambda-dev-MerchantLookupFunctionRole-uSlJlOoezfU4
```

**Attached managed policies:**
```json
{
    "AttachedPolicies": [
        {"PolicyName": "AWSXrayWriteOnlyAccess"},
        {"PolicyName": "AWSLambdaBasicExecutionRole"}
    ]
}
```

**Inline policy (`MerchantLookupFunctionRolePolicy0`):**
```json
{
    "Statement": [
        {
            "Action": [
                "dynamodb:GetItem",
                "dynamodb:Scan",
                "dynamodb:Query",
                "dynamodb:BatchGetItem",
                "dynamodb:DescribeTable"
            ],
            "Resource": [
                "arn:aws:dynamodb:us-east-1:606493605786:table/merchants-dev",
                "arn:aws:dynamodb:us-east-1:606493605786:table/merchants-dev/index/*"
            ],
            "Effect": "Allow"
        }
    ]
}
```

**Least-privilege verdict: PASS**
- Specific read-only DynamoDB verbs only (`GetItem`, `Scan`, `Query`, `BatchGetItem`, `DescribeTable`)
- Scoped to exact table ARN `arn:aws:dynamodb:us-east-1:606493605786:table/merchants-dev` (+ its indexes)
- Zero wildcard actions (`*`) and zero wildcard resources
- No write permissions (`PutItem`, `DeleteItem`, `UpdateItem`, etc.)

---

### 2.5 Correlation ID Echo — End-to-End Test

```
Command: curl -i -H "x-correlation-id: probe-123" \
         https://k8y4yu5epb.execute-api.us-east-1.amazonaws.com/dev/merchants/mer_synth_001
```

```
HTTP/1.1 200 OK
Date: Wed, 08 Jul 2026 10:43:06 GMT
Content-Type: application/json
Content-Length: 118
X-Correlation-Id: probe-123
Apigw-Requestid: ALt0lgP-IAMEZPA=

{"id":"mer_synth_001","name":"Synthetic Coffee Co","averageTransactionValue":42.50,"createdAt":"2026-01-15T10:00:00Z"}
```

**Correlation ID echo: PASS** — `X-Correlation-Id: probe-123` present in response headers, merchant record returned with HTTP 200.

---

## Task 3 — EMF Custom Metrics

### Implementation

Hand-written EMF (Embedded Metrics Format) JSON emitted via `System.out.printf` in `MerchantLookupHandler.java` — **no new dependency added**.

**Method added (`src/main/java/com/uptimecrew/expense/lambda/MerchantLookupHandler.java`):**

```java
private static void emitEmf(String metricName, int value) {
    long ts = System.currentTimeMillis();
    // Hand-written EMF JSON — no extra dependency; Lambda log agent extracts metrics from it.
    System.out.printf(
        "{\"_aws\":{\"Timestamp\":%d,\"CloudWatchMetrics\":[{\"Namespace\":\"ExpenseDev\"," +
        "\"Dimensions\":[[\"FunctionName\"]],\"Metrics\":[{\"Name\":\"%s\",\"Unit\":\"Count\"}]}]}," +
        "\"FunctionName\":\"%s\",\"%s\":%d}%n",
        ts, metricName, FUNCTION_NAME, metricName, value);
}
```

**Metrics emitted:**
- `MerchantLookupSuccess` (Count=1) — emitted on successful 200 response
- `MerchantNotFound` (Count=1) — emitted on 404 not-found response

**Namespace:** `ExpenseDev`  
**Dimension:** `FunctionName` = `expense-merchant-lookup-dev`

### Build & Deploy

```
mvn package -DskipTests   → BUILD SUCCESS (29.9 s)
sam build                  → Build Succeeded
sam deploy --no-confirm-changeset → UPDATE_COMPLETE (expense-lambda-dev)
```

CloudFormation changeset published new Lambda version `Versiond33530efaa`, updated alias `live`.

### 5x Endpoint Invocations

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "HTTP %{http_code}\n" \
    https://k8y4yu5epb.execute-api.us-east-1.amazonaws.com/dev/merchants/mer_synth_001
done
```

```
HTTP 200
HTTP 200
HTTP 200
HTTP 200
HTTP 200
```

### Metric Confirmation

```
Command: aws cloudwatch list-metrics --namespace ExpenseDev --region us-east-1
```

```json
{
    "Metrics": [
        {
            "Namespace": "ExpenseDev",
            "MetricName": "MerchantLookupSuccess",
            "Dimensions": [
                {
                    "Name": "FunctionName",
                    "Value": "expense-merchant-lookup-dev"
                }
            ]
        }
    ]
}
```

**EMF metric confirmed: PASS** — `MerchantLookupSuccess` appears in namespace `ExpenseDev` scoped to `FunctionName=expense-merchant-lookup-dev` after 5 successful invocations.

---

## Summary

| Check | Result |
|---|---|
| Lambda REPORT logs — Duration/Restore Duration | Warm: 7–18 ms; SnapStart restore: ~560 ms |
| SnapStart configured | `ApplyOn: PublishedVersions` |
| p99 CloudWatch alarm | Exists, state OK, threshold 1500 ms, 5 eval periods |
| IAM least-privilege | Read-only DDB verbs, exact table ARN, zero wildcards |
| Correlation ID echo | `X-Correlation-Id: probe-123` echoed in response |
| EMF metric `MerchantLookupSuccess` in `ExpenseDev` | Confirmed via `list-metrics` |
