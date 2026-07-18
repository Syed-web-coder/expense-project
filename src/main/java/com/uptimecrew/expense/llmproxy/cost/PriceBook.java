// src/main/java/com/uptimecrew/expense/llmproxy/cost/PriceBook.java
//
// modelId -> price per 1k tokens (USD). Seeded for the two models this
// service actually calls. Unknown modelId throws rather than silently
// pricing at zero.
package com.uptimecrew.expense.llmproxy.cost;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class PriceBook implements PriceLookup {

    private static final Map<String, BigDecimal> PRICES_PER_1K_TOKENS = Map.of(
            "claude-sonnet-4-5", new BigDecimal("0.003"),
            "anthropic.claude-3-5-haiku-20241022-v1:0", new BigDecimal("0.0008"),
            "amazon.titan-embed-text-v2:0", new BigDecimal("0.00002")
    );

    @Override
    public BigDecimal priceFor(String modelId) {
        BigDecimal price = PRICES_PER_1K_TOKENS.get(modelId);
        if (price == null) {
            throw new IllegalArgumentException("no price seeded for modelId: " + modelId);
        }
        return price;
    }
}
