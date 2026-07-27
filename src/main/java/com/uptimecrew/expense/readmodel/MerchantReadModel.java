package com.uptimecrew.expense.readmodel;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class MerchantReadModel implements Serializable {

    private String id;

    private String mccCode;

    private Instant capturedAt;

    private List<EmbeddedLine> transactions;

    public MerchantReadModel() {}

    public MerchantReadModel(String id, String mccCode, Instant capturedAt, List<EmbeddedLine> transactions) {
        this.id = id;
        this.mccCode = mccCode;
        this.capturedAt = capturedAt;
        this.transactions = transactions;
    }

    public String getId() { return id; }
    public String getMccCode() { return mccCode; }
    public Instant getCapturedAt() { return capturedAt; }
    public List<EmbeddedLine> getTransactions() { return transactions; }

    public static class EmbeddedLine implements Serializable {
        private int line;
        private java.math.BigDecimal amount;

        public EmbeddedLine() {}
        public EmbeddedLine(int line, java.math.BigDecimal amount) {
            this.line = line;
            this.amount = amount;
        }

        public int getLine() { return line; }
        public java.math.BigDecimal getAmount() { return amount; }
    }
}
