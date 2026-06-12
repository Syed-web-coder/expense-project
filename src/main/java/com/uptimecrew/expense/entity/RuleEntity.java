package com.uptimecrew.expense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(schema = "expense", name = "rule")
public class RuleEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private MerchantEntity merchant;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "amount_threshold", nullable = true, precision = 12, scale = 2)
    private BigDecimal amountThreshold;

    @Column(name = "mcc_code", nullable = true)
    private String mccCode;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    protected RuleEntity() {}

    public RuleEntity(String id, MerchantEntity merchant, String category,
                      BigDecimal amountThreshold, String mccCode, String name) {
        this.id = id;
        this.merchant = merchant;
        this.category = category;
        this.amountThreshold = amountThreshold;
        this.mccCode = mccCode;
        this.name = name;
    }

    public String getId() { return id; }
    public MerchantEntity getMerchant() { return merchant; }
    public String getCategory() { return category; }
    public BigDecimal getAmountThreshold() { return amountThreshold; }
    public String getMccCode() { return mccCode; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleEntity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
