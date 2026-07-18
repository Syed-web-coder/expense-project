// src/main/java/com/uptimecrew/expense/llmproxy/cost/EmfEmitter.java
//
// One println per LLM call. CloudWatch agent tails stdout and
// forwards to CloudWatch Logs; the service extracts metrics from
// the EMF document automatically.
//
// Dimensions: [[service, tenant, feature]] is what makes the
// $/tenant and $/feature drill-downs work. Renaming any of these
// dimensions silently breaks the alarm this task's CFN work will add.
package com.uptimecrew.expense.llmproxy.cost;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class EmfEmitter {

    public void emit(Instant at,
                     String service,
                     String tenant,
                     String feature,
                     String modelId,
                     long latencyMs,
                     boolean success,
                     long costUsdE5) {

        // CloudWatch metric values must be doubles; render the
        // integer minor-units field back to dollars for the
        // metric value only. The cost_usd_e5 long is preserved
        // in the same JSON for CloudWatch Logs Insights queries
        // that want exact pennies.
        double costUsdMetric = costUsdE5 / 100_000.0;

        String json = "{"
            + "\"_aws\":{"
            + "\"Timestamp\":" + at.toEpochMilli() + ","
            + "\"CloudWatchMetrics\":[{"
            + "\"Namespace\":\"acme/llmproxy\","
            + "\"Dimensions\":[[\"service\",\"tenant\",\"feature\"]],"
            + "\"Metrics\":["
            + "{\"Name\":\"CostUsd\",\"Unit\":\"None\"},"
            + "{\"Name\":\"LatencyMs\",\"Unit\":\"Milliseconds\"}"
            + "]}]"
            + "},"
            + "\"service\":\""  + service  + "\","
            + "\"tenant\":\""   + tenant   + "\","
            + "\"feature\":\""  + feature  + "\","
            + "\"modelId\":\""  + modelId  + "\","
            + "\"success\":"     + success  + ","
            + "\"CostUsd\":"     + costUsdMetric + ","
            + "\"CostUsdE5\":"   + costUsdE5    + ","
            + "\"LatencyMs\":"   + latencyMs
            + "}";
        System.out.println(json);
    }
}
