// src/main/java/com/uptimecrew/expense/llmproxy/cost/UpstreamResponse.java
//
// Minimal shape describing what came back from the LLM call, enough
// for cost + latency accounting. Not a full response wrapper.
package com.uptimecrew.expense.llmproxy.cost;

import java.util.Objects;

public record UpstreamResponse(
        String modelId,
        long inputTokens,
        long outputTokens,
        long latencyMs,
        boolean success) {

    public UpstreamResponse {
        Objects.requireNonNull(modelId, "modelId");
    }
}
