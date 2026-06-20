package com.uptimecrew.expense.graphql;

import java.math.BigDecimal;

public record MerchantSummary(
        String mccCode,
        BigDecimal totalSpend,
        int transactionCount,
        String primaryCategory,
        Confidence confidence) {

    public enum Confidence { HIGH, MEDIUM, LOW }
}
