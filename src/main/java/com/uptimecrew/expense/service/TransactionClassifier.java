package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;

/**
 * Classifies a transaction into a TransactionKind.
 */
public interface TransactionClassifier {
    /**
     * @param transaction the transaction to classify
     * @return the TransactionKind for this transaction
     */
    TransactionKind classify(Transaction transaction);
}
