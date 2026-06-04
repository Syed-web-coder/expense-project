package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Classifies transactions based on the merchant name.
 */
public final class MerchantNameClassifier implements TransactionClassifier {

    private static final Logger LOG =
        Logger.getLogger(MerchantNameClassifier.class.getName());

    /**
     * @param transaction the transaction to classify
     * @return REFUND if merchant name contains "refund", PURCHASE otherwise
     * @throws NullPointerException if transaction is null
     */
    @Override
    public TransactionKind classify(Transaction transaction) {
        if (transaction == null) {
            LOG.warning("classify called with null transaction — rejecting input");
            throw new NullPointerException("transaction must not be null");
        }
        String name = transaction.getMerchantName().toLowerCase();
        TransactionKind result = name.contains("refund")
            ? TransactionKind.REFUND
            : TransactionKind.PURCHASE;
        LOG.info("Classified transaction " + transaction.getId() + " as " + result);
        return result;
    }
}
