package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that classifies transactions using an injected strategy.
 * The strategy is provided at construction time and never constructed internally.
 */
@Service
public final class ExpenseClassificationService {

    private static final Logger LOG =
        LoggerFactory.getLogger(ExpenseClassificationService.class);

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
        Objects.requireNonNull(transaction, "transaction must not be null");
        LOG.info("Classifying transaction {}", transaction.id());
        try {
            TransactionKind result = classifier.classify(transaction);
            LOG.info("Transaction {} classified as {}", transaction.id(), result);
            return result;
        } catch (RuntimeException e) {
            LOG.warn("Classification failed for transaction {}: {}", transaction.id(), e.getMessage());
            throw e;
        }
    }
}
