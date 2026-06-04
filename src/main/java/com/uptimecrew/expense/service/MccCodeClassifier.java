package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Classifies transactions based on MCC (Merchant Category Code).
 * Transactions whose merchant name matches a known refund code are REFUND;
 * others are PURCHASE.
 */
public final class MccCodeClassifier implements TransactionClassifier {

    private static final Logger LOG =
        Logger.getLogger(MccCodeClassifier.class.getName());

    private final Set<String> refundCodes;

    /**
     * @param refundCodes set of merchant name codes that indicate a refund,
     *                    must not be null
     * @throws NullPointerException if refundCodes is null
     */
    public MccCodeClassifier(Set<String> refundCodes) {
        this.refundCodes = Set.copyOf(
            Objects.requireNonNull(refundCodes, "refundCodes must not be null")
        );
    }

    /**
     * @param transaction the transaction to classify, must not be null
     * @return REFUND if merchant name matches a refund code, PURCHASE otherwise
     * @throws NullPointerException if transaction is null
     */
    @Override
    public TransactionKind classify(Transaction transaction) {
        if (transaction == null) {
            LOG.warning("classify called with null transaction — rejecting input");
            throw new NullPointerException("transaction must not be null");
        }
        TransactionKind result = refundCodes.contains(
            transaction.getMerchantName().toLowerCase())
            ? TransactionKind.REFUND
            : TransactionKind.PURCHASE;
        LOG.info("Classified transaction " + transaction.getId() + " as " + result);
        return result;
    }

    /** @return the set of refund codes */
    public Set<String> getRefundCodes() { return refundCodes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MccCodeClassifier other)) return false;
        return refundCodes.equals(other.refundCodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refundCodes);
    }

    @Override
    public String toString() {
        return "MccCodeClassifier{refundCodes=" + refundCodes + "}";
    }
}
