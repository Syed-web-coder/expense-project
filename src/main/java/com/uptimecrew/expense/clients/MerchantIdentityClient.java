package com.uptimecrew.expense.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Pattern reference for Task 3 (Spring Cloud OpenFeign).
//
// Declarative HTTP client: Spring generates the proxy from the interface +
// the URL property `identity.base-url`. The Feign proxy does NOT run through
// the Resilience4j advisor — that's why we wrap the call in IdentityService
// (see Section 9 sticking point #1).
@FeignClient(name = "identity", url = "${identity.base-url}")
public interface MerchantIdentityClient {

    @GetMapping("/identity/{userId}/profile")
    IdentityProfile getProfile(@PathVariable("userId") String userId);
}
