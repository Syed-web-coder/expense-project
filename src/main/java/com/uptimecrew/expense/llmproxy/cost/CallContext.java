// src/main/java/com/uptimecrew/expense/llmproxy/cost/CallContext.java
//
// Minimal per-call context the cost middleware needs. tenant() is
// hardcoded to "default" for now -- this codebase has no multi-tenant
// concept yet (see W6D4 task notes); revisit when tenancy lands.
package com.uptimecrew.expense.llmproxy.cost;

import java.time.Instant;
import java.util.Objects;

public record CallContext(String tenant, Instant at) {

    public CallContext {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(at, "at");
    }

    public static CallContext defaultTenant(Instant at) {
        return new CallContext("default", at);
    }
}
