package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a single financial transaction on an account.
 *
 * @param id           unique transaction ID
 * @param accountId    account this transaction belongs to
 * @param amount       transaction amount, must be >= 0
 * @param merchantName name of the merchant, must be non-blank
 * @param occurredOn   date transaction occurred
 */
public record Transaction(
    String id,
    String accountId,
    BigDecimal amount,
    String merchantName,
    LocalDate occurredOn
) {
    public Transaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(merchantName, "merchantName must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative");
        if (merchantName.isBlank())
            throw new IllegalArgumentException("merchantName must not be blank");
    }
}
