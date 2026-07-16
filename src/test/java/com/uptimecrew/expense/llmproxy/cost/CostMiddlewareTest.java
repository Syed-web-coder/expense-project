// src/test/java/com/uptimecrew/expense/llmproxy/cost/CostMiddlewareTest.java
//
// Covers the three cases the W6D4 Task 2 spec requires: (1k-input,
// 0-output), (0-input, 1k-output), and an overflow case that must
// throw ArithmeticException from longValueExact().
//
// Uses hand-written fakes rather than Mockito mocks for PriceLookup /
// CostStore -- this project's mock maker (ProxyMockMaker, set in
// build.gradle for JDK compatibility) can mock interfaces fine, but
// we need deterministic control over the price (including an
// artificially huge one to force overflow) that a generic mock
// wouldn't give us as cleanly as a real fake implementation.
package com.uptimecrew.expense.llmproxy.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CostMiddlewareTest {

    private static final String MODEL_ID = "test-model";

    /** Records every increment call for assertion. */
    private static final class FakeCostStore implements CostStore {
        final List<Long> increments = new ArrayList<>();
        String lastTenant;
        LocalDate lastDay;

        @Override
        public void incrementTenantDay(String tenant, LocalDate day, long costUsdE5Delta) {
            this.lastTenant = tenant;
            this.lastDay = day;
            this.increments.add(costUsdE5Delta);
        }
    }

    private static PriceLookup fixedPrice(BigDecimal pricePer1kTokens) {
        return modelId -> pricePer1kTokens;
    }

    @Test
    void observe_1kInputZeroOutput_recordsExpectedCostUsdE5() {
        FakeCostStore store = new FakeCostStore();
        CostMiddleware middleware = new CostMiddleware(
                fixedPrice(new BigDecimal("0.0008")), store, new EmfEmitter());

        Instant at = Instant.now();
        middleware.observe(
                new CallContext("t1", at),
                new UpstreamResponse(MODEL_ID, 1000, 0, 42, true));

        // price 0.0008 * (1000+0)/1000 = 0.0008 USD -> 80 in cost_usd_e5
        assertThat(store.increments).containsExactly(80L);
        assertThat(store.lastTenant).isEqualTo("t1");
        assertThat(store.lastDay).isEqualTo(LocalDate.ofInstant(at, ZoneOffset.UTC));
    }

    @Test
    void observe_zeroInput1kOutput_recordsExpectedCostUsdE5() {
        FakeCostStore store = new FakeCostStore();
        CostMiddleware middleware = new CostMiddleware(
                fixedPrice(new BigDecimal("0.0008")), store, new EmfEmitter());

        middleware.observe(
                new CallContext("t1", Instant.now()),
                new UpstreamResponse(MODEL_ID, 0, 1000, 42, true));

        // price 0.0008 * (0+1000)/1000 = 0.0008 USD -> 80 in cost_usd_e5
        assertThat(store.increments).containsExactly(80L);
    }

    @Test
    void observe_costExceedsLongRange_throwsArithmeticException() {
        FakeCostStore store = new FakeCostStore();
        // An artificially huge price forces costUsdE5 past Long.MAX_VALUE
        // even for a modest token count -- no real seeded price could
        // reach this through legitimate token counts, so we inject one
        // here specifically to exercise the overflow guard.
        CostMiddleware middleware = new CostMiddleware(
                fixedPrice(new BigDecimal("100000000000000")), store, new EmfEmitter());

        assertThatThrownBy(() -> middleware.observe(
                new CallContext("t1", Instant.now()),
                new UpstreamResponse(MODEL_ID, 1000, 0, 42, true)))
                .isInstanceOf(ArithmeticException.class);

        // Nothing should have been recorded -- the exception must
        // happen before the store is touched.
        assertThat(store.increments).isEmpty();
    }
}
