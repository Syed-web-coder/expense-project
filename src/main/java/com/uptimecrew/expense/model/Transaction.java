package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public final class Transaction {

    private final String id;
    private final String accountId;
    private final BigDecimal amount;
    private final String merchantName;
    private final LocalDate occurredOn;

    public Transaction(String id, String accountId, BigDecimal amount,
                       String merchantName, LocalDate occurredOn) {
        this.id = requireNonBlank(id, "id");
        this.accountId = requireNonBlank(accountId, "accountId");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be >= 0, got: " + amount);
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.merchantName = requireNonBlank(merchantName, "merchantName");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    public String getId()           { return id; }
    public String getAccountId()    { return accountId; }
    public BigDecimal getAmount()   { return amount; }
    public String getMerchantName() { return merchantName; }
    public LocalDate getOccurredOn(){ return occurredOn; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction t)) return false;
        return id.equals(t.id)
                && accountId.equals(t.accountId)
                && amount.equals(t.amount)
                && merchantName.equals(t.merchantName)
                && occurredOn.equals(t.occurredOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, amount, merchantName, occurredOn);
    }

    @Override
    public String toString() {
        return "Transaction{id='" + id + "', accountId='" + accountId
                + "', amount=" + amount + ", merchantName='" + merchantName
                + "', occurredOn=" + occurredOn + '}';
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
