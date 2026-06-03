package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public final class TransactionDraft {

    private final String id;
    private final BigDecimal amount;
    private final String merchantName;
    private final LocalDate occurredOn;

    public TransactionDraft(String id, BigDecimal amount, String merchantName, LocalDate occurredOn) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.merchantName = Objects.requireNonNull(merchantName, "merchantName must not be null");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn must not be null");
        BigDecimal checkedAmount = Objects.requireNonNull(amount, "amount must not be null");
        if (checkedAmount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.merchantName.isBlank()) {
            throw new IllegalArgumentException("merchantName must not be blank");
        }
        this.amount = checkedAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public String getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransactionDraft that = (TransactionDraft) o;
        return Objects.equals(id, that.id)
            && amount.compareTo(that.amount) == 0
            && Objects.equals(merchantName, that.merchantName)
            && Objects.equals(occurredOn, that.occurredOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount.stripTrailingZeros(), merchantName, occurredOn);
    }

    @Override
    public String toString() {
        return "TransactionDraft{id='" + id + "', amount=" + amount
            + ", merchantName='" + merchantName + "', occurredOn=" + occurredOn + "}";
    }
}
