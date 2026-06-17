package com.uptimecrew.expense.clients;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Pattern reference for Task 3 (Resilience4j @CircuitBreaker around Feign).
//
// @CircuitBreaker is evaluated by a Spring AOP advisor on the Service proxy.
// Putting the annotation directly on the Feign interface does NOT work
// because the Feign proxy stack short-circuits before the Resilience4j
// advisor gets a chance — see Section 9 sticking point #1.
@Service
public class IdentityService {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);

    private final MerchantIdentityClient client;

    public IdentityService(MerchantIdentityClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "identity", fallbackMethod = "fallbackProfile")
    public IdentityProfile getProfile(String userId) {
        return client.getProfile(userId);
    }

    // Fallback signature: SAME return type, SAME parameters PLUS one trailing
    // Throwable. Resilience4j picks this up by reflection.
    @SuppressWarnings("unused")
    private IdentityProfile fallbackProfile(String userId, Throwable t) {
        LOG.warn("identity breaker fallback for userId={} cause={}", userId, t.toString());
        return new IdentityProfile(userId, "", "unknown");
    }
}
