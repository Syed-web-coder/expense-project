package com.uptimecrew.expense.readmodel;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "merchants")
public class MerchantReadModel implements Serializable {

    @Id
    private String id;

    @Indexed
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
