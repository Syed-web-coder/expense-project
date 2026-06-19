package com.uptimecrew.expense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(schema = "expense", name = "transaction")
public class TransactionEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private MerchantEntity merchant;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected TransactionEntity() {}

    public TransactionEntity(String id, MerchantEntity merchant, String merchantName,
                             BigDecimal amount, Instant occurredAt, String kind) {
        this(id, merchant, merchantName, amount, occurredAt, kind, null);
    }

    public TransactionEntity(String id, MerchantEntity merchant, String merchantName,
                             BigDecimal amount, Instant occurredAt, String kind, String idempotencyKey) {
        this.id = id;
        this.merchant = merchant;
        this.merchantName = merchantName;
        this.amount = amount;
        this.occurredAt = occurredAt;
        this.kind = kind;
        this.idempotencyKey = idempotencyKey;
    }

    public String getId() { return id; }
    public MerchantEntity getMerchant() { return merchant; }
    public String getMerchantName() { return merchantName; }
    public BigDecimal getAmount() { return amount; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getKind() { return kind; }
    public String getIdempotencyKey() { return idempotencyKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionEntity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
