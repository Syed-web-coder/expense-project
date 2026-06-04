package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service that classifies transactions using an injected strategy.
 * The strategy is provided at construction time and never constructed internally.
 */
public final class ExpenseClassificationService {

    private static final Logger LOG =
        Logger.getLogger(ExpenseClassificationService.class.getName());

    private final TransactionClassifier classifier;

    /**
     * @param classifier the classification strategy to use, must not be null
     * @throws NullPointerException if classifier is null
     */
    public ExpenseClassificationService(TransactionClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
    }

    /**
     * @param transaction the transaction to classify, must not be null
     * @return the TransactionKind for this transaction
     * @throws NullPointerException if transaction is null
     */
    public TransactionKind classify(Transaction transaction) {
        if (transaction == null) {
            LOG.warning("classify called with null transaction — rejecting input");
            throw new NullPointerException("transaction must not be null");
        }
        LOG.info("Classifying transaction " + transaction.id());
        TransactionKind result = classifier.classify(transaction);
        LOG.info("Transaction " + transaction.id() + " classified as " + result);
        return result;
    }
}
