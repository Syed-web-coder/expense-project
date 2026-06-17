package com.uptimecrew.expense.service;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import com.uptimecrew.expense.repository.MerchantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseClassificationService {
    private static final Logger LOG = LoggerFactory.getLogger(ExpenseClassificationService.class);
    public static final String CACHE_NAME = "expense.byId"; 
    private final TransactionClassifier strategy;
    private final MerchantRepository repository;
    private final MerchantReadModelRepository readModelRepository;

    public ExpenseClassificationService(TransactionClassifier strategy,
                                        MerchantRepository repository,
                                        MerchantReadModelRepository readModelRepository) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.readModelRepository = Objects.requireNonNull(readModelRepository, "readModelRepository");
    }

    @Transactional
    public MerchantEntity classify(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        LOG.info("Classifying transaction {}", transaction.id());
        TransactionKind result;
        try {
            result = strategy.classify(transaction);
        } catch (RuntimeException e) {
            LOG.warn("Classification failed for transaction {}: {}", transaction.id(), e.getMessage());
            throw e;
        }
        LOG.info("Transaction {} classified as {}", transaction.id(), result);

        MerchantEntity entity = new MerchantEntity(
            UUID.randomUUID().toString(),
            transaction.merchantName(),
            null,
            Instant.now()
        );
        MerchantEntity saved = repository.save(entity);

        MerchantReadModel projection = new MerchantReadModel(
            saved.getId(),
            null,
            Instant.now(),
            List.of()
        );
        readModelRepository.save(projection);
        LOG.info("write-through to mongo id={}", saved.getId());

        return saved;
    }

    @Cacheable(value = CACHE_NAME, unless = "#result == null")
    public Optional<MerchantReadModel> findById(String id) {
        LOG.info("cache miss on id={}; reading from mongo", id);
        Optional<MerchantReadModel> fromMongo = readModelRepository.findById(id);
        if (fromMongo.isPresent()) return fromMongo;

        return repository.findById(id).map(e ->
            new MerchantReadModel(e.getId(), e.getMccCode(), Instant.now(), List.of()));
    }
}
