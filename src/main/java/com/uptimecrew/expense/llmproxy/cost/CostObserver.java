// src/main/java/com/uptimecrew/expense/llmproxy/cost/CostObserver.java
//
// Interface boundary so callers (and tests) can depend on this rather
// than the concrete CostMiddleware. This project's mock maker
// (ProxyMockMaker, set in build.gradle for JDK compatibility) can only
// mock interfaces, not classes -- this exists so cost tracking stays
// mockable in tests.
package com.uptimecrew.expense.llmproxy.cost;

public interface CostObserver {
    void observe(CallContext ctx, UpstreamResponse resp);
}
