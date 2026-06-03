package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TransactionValidationTest {

    @ParameterizedTest
    @CsvSource({
        "0.00,  true",
        "1.00,  true",
        "487.50, true",
        "-0.01, false",
        "-1.00, false",
        "-999.99, false"
    })
    void amount_boundary(String amountStr, boolean valid) {
        BigDecimal amount = new BigDecimal(amountStr.trim());
        if (valid) {
            assertDoesNotThrow(() -> new Transaction(
                "txn-001", "acc-001", amount,
                "Office Depot", LocalDate.of(2026, 3, 1)
            ));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new Transaction(
                "txn-001", "acc-001", amount,
                "Office Depot", LocalDate.of(2026, 3, 1)
            ));
        }
    }
}
