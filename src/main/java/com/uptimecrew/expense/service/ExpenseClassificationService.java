package com.uptimecrew.expense.service;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.repository.MerchantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
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

    // Simple keyword-based MCC guesser for merchants created via manual entry
    // (no MCC supplied by the caller). Falls back to null if nothing matches.
    private static final Map<String, String> MCC_KEYWORDS = Map.ofEntries(
        Map.entry("starbucks", "5812"),
        Map.entry("chick fil a", "5812"),
        Map.entry("chick-fil-a", "5812"),
        Map.entry("mcdonald", "5812"),
        Map.entry("restaurant", "5812"),
        Map.entry("cafe", "5812"),
        Map.entry("grocery", "5411"),
        Map.entry("market", "5411"),
        Map.entry("target", "5411"),
        Map.entry("whole foods", "5411"),
        Map.entry("walmart", "5411"),
        Map.entry("kroger", "5411"),
        Map.entry("costco", "5411"),
        Map.entry("uber", "4121"),
        Map.entry("lyft", "4121"),
        Map.entry("transit", "4111"),
        Map.entry("metro", "4111"),
        Map.entry("subway", "4111"),
        Map.entry("bus", "4111"),
        Map.entry("electric", "4911"),
        Map.entry("utility", "4911"),
        Map.entry("netflix", "7922"),
        Map.entry("spotify", "7922"),
        Map.entry("streaming", "7922")
    );

    private static String guessMccCode(String merchantName) {
        String lower = merchantName.toLowerCase();
        for (Map.Entry<String, String> entry : MCC_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public ExpenseClassificationService(TransactionClassifier strategy,
                                        MerchantRepository repository) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.repository = Objects.requireNonNull(repository, "repository");
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

        MerchantEntity entity = repository.findByNameIgnoreCase(transaction.merchantName())
            .orElseGet(() -> new MerchantEntity(
                UUID.randomUUID().toString(),
                transaction.merchantName(),
                guessMccCode(transaction.merchantName()),
                Instant.now()
            ));
        return repository.save(entity);
    }

    @Cacheable(value = CACHE_NAME, unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<MerchantReadModel> findById(String id) {
        LOG.info("cache miss on id={}; reading from postgres", id);
        return repository.findById(id).map(e -> {
            List<MerchantReadModel.EmbeddedLine> lines = IntStream.range(0, e.getTransactions().size())
                    .mapToObj(i -> new MerchantReadModel.EmbeddedLine(i + 1, e.getTransactions().get(i).getAmount()))
                    .toList();
            return new MerchantReadModel(e.getId(), e.getMccCode(), e.getCreatedAt(), lines);
        });
    }
}
