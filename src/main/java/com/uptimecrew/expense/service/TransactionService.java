package com.uptimecrew.expense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.entity.OutboxEvent;
import com.uptimecrew.expense.entity.TransactionEntity;
import com.uptimecrew.expense.events.TransactionPlacedEvent;
import com.uptimecrew.expense.exception.UnrecognizedMerchantException;
import com.uptimecrew.expense.repository.MerchantRepository;
import com.uptimecrew.expense.repository.OutboxRepository;
import com.uptimecrew.expense.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    public static final String TOPIC = "expense.transactions.v1";
    private static final String AGGREGATE_TYPE = "Transaction";
    private static final String EVENT_TYPE = "TransactionPlaced";

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository transactionRepository,
                               MerchantRepository merchantRepository,
                               OutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository, "transactionRepository");
        this.merchantRepository    = Objects.requireNonNull(merchantRepository, "merchantRepository");
        this.outboxRepository      = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.objectMapper          = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public TransactionEntity recordTransaction(String merchantId, BigDecimal amount, String kind) {
        return recordTransaction(merchantId, amount, kind, null);
    }

    @Transactional
    public TransactionEntity recordTransaction(String merchantId, BigDecimal amount, String kind,
                                                String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new UnrecognizedMerchantException(
                        "No merchant found for id " + merchantId));

        String transactionId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now();

        TransactionEntity entity = new TransactionEntity(
                transactionId,
                merchant,
                merchant.getName(),
                amount,
                occurredAt,
                kind,
                idempotencyKey
        );
        TransactionEntity saved = transactionRepository.save(entity);

        TransactionPlacedEvent event = new TransactionPlacedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                saved.getId(),
                merchant.getId(),
                merchant.getName(),
                amount,
                occurredAt,
                kind
        );

        outboxRepository.save(new OutboxEvent(
                AGGREGATE_TYPE,
                saved.getId(),
                EVENT_TYPE,
                writeJson(event)
        ));

        return saved;
    }

    @Transactional
    public com.uptimecrew.expense.mcp.TransactionView recordTransactionView(
            String merchantId, BigDecimal amount, String kind, String idempotencyKey) {

        if (idempotencyKey != null) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TransactionEntity e = existing.get();
                return new com.uptimecrew.expense.mcp.TransactionView(
                        e.getId(), merchantId, e.getMerchantName(), e.getAmount(), e.getOccurredAt(), e.getKind());
            }
        }

        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new UnrecognizedMerchantException(
                        "No merchant found for id " + merchantId));

        String transactionId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now();

        TransactionEntity entity = new TransactionEntity(
                transactionId,
                merchant,
                merchant.getName(),
                amount,
                occurredAt,
                kind,
                idempotencyKey
        );
        TransactionEntity saved = transactionRepository.save(entity);

        TransactionPlacedEvent event = new TransactionPlacedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                saved.getId(),
                merchant.getId(),
                merchant.getName(),
                amount,
                occurredAt,
                kind
        );

        outboxRepository.save(new OutboxEvent(
                AGGREGATE_TYPE,
                saved.getId(),
                EVENT_TYPE,
                writeJson(event)
        ));

        return new com.uptimecrew.expense.mcp.TransactionView(
                saved.getId(), merchant.getId(), merchant.getName(), amount, occurredAt, kind);
    }

    private String writeJson(TransactionPlacedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise " + EVENT_TYPE + " event", e);
        }
    }
}
