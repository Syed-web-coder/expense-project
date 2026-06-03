package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.ExpenseCategory;
import com.uptimecrew.expense.model.Transaction;

public interface TransactionClassifier {

    /**
     * Returns the best-matching category for the given transaction, or
     * {@code null} if no category can be determined.
     */
    ExpenseCategory classify(Transaction transaction);
}
