package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class TransactionDraft {
    private final String id;
    private final BigDecimal amount;
    private final String merchantName;
    private final LocalDate occurredOn;

    public TransactionDraft(String id, BigDecimal amount,
                            String merchantName, LocalDate occurredOn) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.merchantName = Objects.requireNonNull(merchantName, "merchantName must not be null");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative");
    }

    public String getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getMerchantName() { return merchantName; }
    public LocalDate getOccurredOn() { return occurredOn; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionDraft other)) return false;
        return id.equals(other.id)
            && amount.compareTo(other.amount) == 0
            && merchantName.equals(other.merchantName)
            && occurredOn.equals(other.occurredOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount.stripTrailingZeros(), merchantName, occurredOn);
    }

    @Override
    public String toString() {
        return "TransactionDraft{id=" + id + ", amount=" + amount +
            ", merchantName=" + merchantName + ", occurredOn=" + occurredOn + "}";
    }
}
