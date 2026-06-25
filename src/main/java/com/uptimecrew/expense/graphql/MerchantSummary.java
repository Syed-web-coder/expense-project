package com.uptimecrew.expense.graphql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record MerchantSummary(
        String mccCode,
        BigDecimal totalSpend,
        int transactionCount,
        String primaryCategory,
        Confidence confidence,
        int tokensIn,
        int tokensOut) {

    public enum Confidence { HIGH, MEDIUM, LOW }

    // Used by Jackson when deserializing LLM JSON (which never contains token counts)
    // and by existing callers (tests) that don't supply token counts.
    @JsonCreator
    public MerchantSummary(
            @JsonProperty("mccCode") String mccCode,
            @JsonProperty("totalSpend") BigDecimal totalSpend,
            @JsonProperty("transactionCount") int transactionCount,
            @JsonProperty("primaryCategory") String primaryCategory,
            @JsonProperty("confidence") Confidence confidence) {
        this(mccCode, totalSpend, transactionCount, primaryCategory, confidence, 0, 0);
    }
}
