package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Classifies transactions based on amount threshold.
 * Transactions above the threshold are PURCHASE; at or below are OTHER.
 */
public final class AmountThresholdClassifier implements TransactionClassifier {

    private static final Logger LOG =
        Logger.getLogger(AmountThresholdClassifier.class.getName());

    private final BigDecimal threshold;

    /**
     * @param threshold the amount threshold to classify against, must not be null
     * @throws NullPointerException if threshold is null
     * @throws IllegalArgumentException if threshold is negative
     */
    public AmountThresholdClassifier(BigDecimal threshold) {
        this.threshold = Objects.requireNonNull(threshold, "threshold must not be null");
        if (threshold.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("threshold must not be negative");
    }

    /**
     * @param transaction the transaction to classify, must not be null
     * @return PURCHASE if amount exceeds threshold, OTHER otherwise
     * @throws NullPointerException if transaction is null
     */
    @Override
    public TransactionKind classify(Transaction transaction) {
        if (transaction == null) {
            LOG.warning("classify called with null transaction — rejecting input");
            throw new NullPointerException("transaction must not be null");
        }
        TransactionKind result = transaction.amount().compareTo(threshold) > 0
            ? TransactionKind.PURCHASE
            : TransactionKind.OTHER;
        LOG.info("Classified transaction " + transaction.id() + " as " + result);
        return result;
    }

    /** @return the threshold */
    public BigDecimal getThreshold() { return threshold; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AmountThresholdClassifier other)) return false;
        return threshold.compareTo(other.threshold) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(threshold.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "AmountThresholdClassifier{threshold=" + threshold + "}";
    }
}
