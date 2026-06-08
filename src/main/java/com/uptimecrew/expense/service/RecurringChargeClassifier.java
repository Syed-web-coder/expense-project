package com.uptimecrew.expense.service;

import com.uptimecrew.expense.exception.UnrecognizedMerchantException;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;

import java.util.Set;

public final class RecurringChargeClassifier implements TransactionClassifier {

    private static final Set<String> KNOWN_SUBSCRIPTIONS = Set.of(
            "Netflix", "Spotify", "Hulu", "Disney+", "Apple TV+",
            "Amazon Prime", "YouTube Premium", "HBO Max", "Peacock",
            "Adobe", "Microsoft 365", "iCloud", "Google One"
    );

    public boolean classify(String merchant) {
        if (merchant == null) {
            throw new UnrecognizedMerchantException("merchant name is null");
        }
        if (merchant.isBlank()) {
            throw new UnrecognizedMerchantException("merchant name is blank");
        }
        return KNOWN_SUBSCRIPTIONS.contains(merchant);
    }

    @Override
    public TransactionKind classify(Transaction transaction) {
        boolean recurring = classify(transaction.merchantName());
        return recurring ? TransactionKind.OTHER : TransactionKind.PURCHASE;
    }
}
