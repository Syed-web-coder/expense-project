// src/main/java/com/uptimecrew/expense/llmproxy/cost/RedisCostStore.java
//
// Wraps StringRedisTemplate (Spring Data Redis / Lettuce). Uses the
// long-typed opsForHash().increment() overload only, which maps to
// HINCRBY. The double-typed overload maps to the floating-point Redis variant and is
// intentionally never called in this package -- see CostMiddleware.
package com.uptimecrew.expense.llmproxy.cost;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCostStore implements CostStore {

    private final StringRedisTemplate redis;

    public RedisCostStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public void incrementTenantDay(String tenant, LocalDate day, long costUsdE5Delta) {
        String key = "cost:" + tenant + ":" + day;
        // long-typed increment -> HINCRBY. Never call the double-typed
        // overload here (that would map to the floating-point Redis variant, banned in this package).
        redis.opsForHash().increment(key, "cost_usd_e5", costUsdE5Delta);
    }
}
