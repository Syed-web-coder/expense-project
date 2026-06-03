package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.ExpenseCategory;
import com.uptimecrew.expense.model.Transaction;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MerchantNameClassifier implements TransactionClassifier {

    // keyword (lower-case) → category.  Checked in insertion order.
    private final Map<String, ExpenseCategory> keywordToCategory;

    public MerchantNameClassifier(Map<String, ExpenseCategory> keywordToCategory) {
        this.keywordToCategory = Map.copyOf(
                Objects.requireNonNull(keywordToCategory, "keywordToCategory must not be null"));
    }

    /**
     * Convenience factory with a built-in set of common merchant keywords.
     */
    public static MerchantNameClassifier withDefaults() {
        return new MerchantNameClassifier(Map.of(
                "airline",  new ExpenseCategory(uuid(), "Travel",        new BigDecimal("100")),
                "hotel",    new ExpenseCategory(uuid(), "Accommodation",  new BigDecimal("100")),
                "restaurant", new ExpenseCategory(uuid(), "Meals",        new BigDecimal("50")),
                "cafe",     new ExpenseCategory(uuid(), "Meals",          new BigDecimal("50")),
                "pharmacy", new ExpenseCategory(uuid(), "Healthcare",     new BigDecimal("100")),
                "software", new ExpenseCategory(uuid(), "Software",       new BigDecimal("100"))
        ));
    }

    @Override
    public ExpenseCategory classify(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        String lower = transaction.getMerchantName().toLowerCase();
        for (Map.Entry<String, ExpenseCategory> entry : keywordToCategory.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
