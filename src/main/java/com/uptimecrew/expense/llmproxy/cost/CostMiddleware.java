// src/main/java/com/uptimecrew/expense/llmproxy/cost/CostMiddleware.java
//
// W3D1 LLM-proxy: cost middleware. Invariants this file enforces:
//   * Costs are stored in Redis as integer minor units (cost_usd_e5,
//     a long). The floating-point Redis increment variant is BANNED in this package -- a CI grep
//     fails the build if it appears.
//   * BigDecimal arithmetic sets scale + RoundingMode.HALF_UP. No
//     doubles in the cost-arithmetic path.
//   * EMF emission may use a double for the metric VALUE field
//     (CloudWatch requires double there); the cost_usd_e5 integer
//     is preserved in the same JSON for audit-trail queries.
package com.uptimecrew.expense.llmproxy.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class CostMiddleware implements CostObserver {

    private final PriceLookup priceBook;
    private final CostStore store;
    private final EmfEmitter emf;

    public CostMiddleware(PriceLookup priceBook,
                          CostStore store,
                          EmfEmitter emf) {
        this.priceBook = Objects.requireNonNull(priceBook);
        this.store     = Objects.requireNonNull(store);
        this.emf       = Objects.requireNonNull(emf);
    }

    @Override
    public void observe(CallContext ctx, UpstreamResponse resp) {
        // 1. Compute cost in BigDecimal with explicit scale + HALF_UP.
        BigDecimal pricePerKTokens = priceBook.priceFor(resp.modelId());
        BigDecimal costUsd = pricePerKTokens
            .multiply(BigDecimal.valueOf(resp.inputTokens() + resp.outputTokens()))
            .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);

        // 2. Convert to integer minor units (cost_usd_e5) as a long.
        //    longValueExact() throws on overflow rather than silently
        //    truncating -- a runaway prompt becomes an exception, not
        //    a silently-corrupt Redis tally.
        long costUsdE5 = costUsd
            .setScale(5, RoundingMode.HALF_UP)
            .movePointRight(5)
            .longValueExact();

        // 3. HINCRBY on the integer field -- never its floating-point variant.
        LocalDate day = LocalDate.ofInstant(ctx.at(), ZoneOffset.UTC);
        store.incrementTenantDay(ctx.tenant(), day, costUsdE5);

        // 4. Emit one EMF document; CloudWatch extracts the metric
        //    server-side. No PutMetricData call on the hot path.
        emf.emit(
            ctx.at(),
            "expense",
            ctx.tenant(),
            "categorize-expense",
            resp.modelId(),
            resp.latencyMs(),
            resp.success(),
            costUsdE5
        );
    }
}
