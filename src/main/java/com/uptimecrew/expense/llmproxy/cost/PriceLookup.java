// src/main/java/com/uptimecrew/expense/llmproxy/cost/PriceLookup.java
//
// Interface boundary so CostMiddleware can be tested with a fake price
// table (e.g. to force the overflow case deterministically) without
// needing to mock a concrete class under this project's ProxyMockMaker.
package com.uptimecrew.expense.llmproxy.cost;

import java.math.BigDecimal;

public interface PriceLookup {
    BigDecimal priceFor(String modelId);
}
