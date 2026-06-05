package com.uptimecrew.expense.service;

import com.uptimecrew.expense.exception.ExpenseClassificationException;
import com.uptimecrew.expense.model.ExpenseCategory;
import com.uptimecrew.expense.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class ExpenseClassificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ExpenseClassificationService.class);

    private final TransactionClassifier classifier;

    public ExpenseClassificationService(TransactionClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
    }

    public ExpenseCategory classify(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        LOG.info("classifying transaction {}", transaction.getId());
        try {
            ExpenseCategory result = classifier.classify(transaction);
            LOG.info("transaction {} classified as {}", transaction.getId(), result);
            return result;
        } catch (ExpenseClassificationException ex) {
            LOG.warn("strategy failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
