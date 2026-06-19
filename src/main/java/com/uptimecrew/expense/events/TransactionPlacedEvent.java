package com.uptimecrew.expense.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * v1 schema for the TransactionPlaced event.
 *
 * Envelope fields (eventId, eventTime) are first per the curriculum's
 * standard envelope convention — every event type carries them so
 * consumers can dedupe and order regardless of payload shape.
 *
 * This is the schema published on "orders.events.v1"-equivalent topic
 * ("expense.transactions.v1"). See TransactionPlacedEventV2 for the
 * backward-compatible evolution used in the schema-evolution test.
 */
public record TransactionPlacedEvent(
        String eventId,
        Instant eventTime,
        String transactionId,
        String merchantId,
        String merchantName,
        BigDecimal amount,
        Instant occurredAt,
        String kind
) {
}
