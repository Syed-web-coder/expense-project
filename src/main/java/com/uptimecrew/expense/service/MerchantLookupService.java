package com.uptimecrew.expense.service;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.repository.MerchantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public final class MerchantLookupService {

    private static final Logger LOG = LoggerFactory.getLogger(MerchantLookupService.class);

    private final MerchantRepository repository;
    private final Counter merchantLookups;
    private final Counter merchantNotFound;
    private final Counter businessCounter;
    private final Timer lookupTimer;

    public MerchantLookupService(MerchantRepository repository, MeterRegistry registry) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.merchantLookups = Counter.builder("expense-api_merchant_lookups_total")
                .description("Total merchant lookup attempts")
                .register(registry);
        this.merchantNotFound = Counter.builder("expense-api_merchant_lookups_not_found_total")
                .description("Merchant lookups that returned no result")
                .register(registry);
        this.businessCounter = Counter.builder("expense_deductions_identified_total")
                .description("Merchants identified as potential business deductions via MCC code")
                .register(registry);
        this.lookupTimer = Timer.builder("expense-api_merchant_lookup_seconds")
                .description("Latency of merchant lookup by id")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Transactional(readOnly = true)
    public Optional<MerchantEntity> findById(String id) {
        Objects.requireNonNull(id, "id");
        merchantLookups.increment();
        Timer.Sample sample = Timer.start();
        try {
            Optional<MerchantEntity> result = repository.findById(id);
            if (result.isEmpty()) {
                merchantNotFound.increment();
                LOG.info("merchant not found id={}", id);
            } else {
                if (result.get().getMccCode() != null) {
                    businessCounter.increment();
                }
                LOG.info("merchant found id={} mccCode={}", id, result.get().getMccCode());
            }
            return result;
        } finally {
            sample.stop(lookupTimer);
        }
    }
}
