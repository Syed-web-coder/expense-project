package com.uptimecrew.expense.service;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import com.uptimecrew.expense.repository.MerchantRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service; 
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that classifies transactions using an injected strategy.
 * The strategy is provided at construction time and never constructed internally.
 */
@Service
public class ExpenseClassificationService { 
    private static final Logger LOG =
        LoggerFactory.getLogger(ExpenseClassificationService.class);

    private final TransactionClassifier classifier;
    private final MerchantRepository repository;

    /**
     * @param classifier the classification strategy to use, must not be null
     * @param repository the merchant repository for persistence, must not be null
     * @throws NullPointerException if either argument is null
     */
    public ExpenseClassificationService(TransactionClassifier classifier, MerchantRepository repository) {
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * @param transaction the transaction to classify, must not be null
     * @return the saved MerchantEntity derived from this transaction
     * @throws NullPointerException if transaction is null
     */
    @Transactional
    public MerchantEntity classify(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        LOG.info("Classifying transaction {}", transaction.id());
        try {
            TransactionKind result = classifier.classify(transaction);
            LOG.info("Transaction {} classified as {}", transaction.id(), result);
            MerchantEntity entity = new MerchantEntity(
                UUID.randomUUID().toString(),
                transaction.merchantName(),
                null,
                Instant.now()
            );
            return repository.save(entity);
        } catch (RuntimeException e) {
            LOG.warn("Classification failed for transaction {}: {}", transaction.id(), e.getMessage());
            throw e;
        }
    }
}
