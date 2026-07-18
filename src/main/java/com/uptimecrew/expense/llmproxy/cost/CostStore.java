// src/main/java/com/uptimecrew/expense/llmproxy/cost/CostStore.java
//
// Interface boundary over the Redis-backed cost tally so tests can
// substitute an in-memory fake instead of mocking a concrete class.
package com.uptimecrew.expense.llmproxy.cost;

import java.time.LocalDate;

public interface CostStore {
    void incrementTenantDay(String tenant, LocalDate day, long costUsdE5Delta);
}
