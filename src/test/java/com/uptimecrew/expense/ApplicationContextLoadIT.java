package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.service.ExpenseClassificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadIT {

    @Autowired
    ExpenseClassificationService service;

    @Test
    void context_loads_and_service_bean_is_wired() {
        assertThat(service)
            .as("Spring-managed ExpenseClassificationService should be wired by the context")
            .isNotNull();
    }

    @Test
    void service_delegates_to_primary_strategy() {
        Transaction input = new Transaction("txn-it-01", "acc-001", new BigDecimal("5.00"), "Starbucks", LocalDate.now());
        var result = service.classify(input);
        assertThat(result).isNotNull();
    }
}
