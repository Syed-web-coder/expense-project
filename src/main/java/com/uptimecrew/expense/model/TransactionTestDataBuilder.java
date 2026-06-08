package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TransactionTestDataBuilder {

    private String id = "txn-001";
    private String accountId = "acc-001";
    private BigDecimal amount = new BigDecimal("100.00");
    private String merchantName = "Test Merchant";
    private LocalDate occurredOn = LocalDate.of(2026, 1, 1);

    public static TransactionTestDataBuilder aTransaction() {
        return new TransactionTestDataBuilder();
    }

    public TransactionTestDataBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public TransactionTestDataBuilder withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public TransactionTestDataBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public TransactionTestDataBuilder withMerchantName(String merchantName) {
        this.merchantName = merchantName;
        return this;
    }

    public TransactionTestDataBuilder withOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
        return this;
    }

    public Transaction build() {
        return new Transaction(id, accountId, amount, merchantName, occurredOn);
    }
}
