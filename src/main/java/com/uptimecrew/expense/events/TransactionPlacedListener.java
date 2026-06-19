package com.uptimecrew.expense.events;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes TransactionPlaced events from expense.transactions.v1.
 *
 * Intentionally minimal: logs successfully-parsed events and stops there.
 * The point of this listener (for Day 3's purposes) is to exercise the
 * DLQ path in KafkaConfig — a message that fails to parse as JSON throws
 * out of this method, and Spring Kafka's DefaultErrorHandler +
 * DeadLetterPublishingRecoverer take it from there, routing it to
 * expense.transactions.v1.dlq instead of retrying forever or blocking
 * the partition.
 */
@Component
public class TransactionPlacedListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionPlacedListener.class);

    private final ObjectMapper objectMapper;

    public TransactionPlacedListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "expense.transactions.v1", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String rawJson) throws Exception {
        // Throws JsonProcessingException (a permanent, non-retryable
        // failure per Topic 8) if rawJson isn't valid JSON for this type.
        // That exception is what routes the message to the DLQ.
        TransactionPlacedEvent event = objectMapper.readValue(rawJson, TransactionPlacedEvent.class);
        LOG.info("Consumed TransactionPlaced for transactionId={}", event.transactionId());
    }
}
