package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a single financial transaction on an account.
 */
public final class Transaction {
    private final String id;
    private final String accountId;
    private final BigDecimal amount;
    private final String merchantName;
    private final LocalDate occurredOn;

    /**
     * @param id           unique transaction ID
     * @param accountId    account this transaction belongs to
     * @param amount       transaction amount, must be >= 0
     * @param merchantName name of the merchant, must be non-blank
     * @param occurredOn   date transaction occurred
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if amount is negative or merchantName is blank
     */
    public Transaction(String id, String accountId, BigDecimal amount,
                       String merchantName, LocalDate occurredOn) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.merchantName = Objects.requireNonNull(merchantName, "merchantName must not be null");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative");
        if (merchantName.isBlank())
            throw new IllegalArgumentException("merchantName must not be blank");
    }

    /** @return the transaction ID */
    public String getId() { return id; }
    /** @return the account ID */
    public String getAccountId() { return accountId; }
    /** @return the transaction amount */
    public BigDecimal getAmount() { return amount; }
    /** @return the merchant name */
    public String getMerchantName() { return merchantName; }
    /** @return the date the transaction occurred */
    public LocalDate getOccurredOn() { return occurredOn; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction other)) return false;
        return id.equals(other.id)
            && accountId.equals(other.accountId)
            && amount.compareTo(other.amount) == 0
            && merchantName.equals(other.merchantName)
            && occurredOn.equals(other.occurredOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, amount.stripTrailingZeros(), merchantName, occurredOn);
    }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", accountId=" + accountId +
            ", amount=" + amount + ", merchantName=" + merchantName +
            ", occurredOn=" + occurredOn + "}";
    }
}
