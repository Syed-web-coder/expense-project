package com.uptimecrew.expense.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * v2 schema for the TransactionPlaced event — backward compatible with v1.
 *
 * Adds one optional field, `category`, over TransactionPlacedEvent (v1):
 * a classification tag (e.g. "FOOD", "TRANSPORT") that wasn't captured
 * when v1 events were published. Under backward compatibility, a v2
 * consumer must still be able to parse a v1-shaped event on the wire —
 * `category` simply comes back null for those older events.
 *
 * This is what makes it backward-compatible, not just "has more fields":
 *   - The field is OPTIONAL (no @JsonProperty(required = true)).
 *   - Nothing was removed or renamed from v1.
 *   - No existing field's type changed.
 * Per Topic 5: narrowing a type or removing/renaming a field would have
 * broken this; only ADDING optional fields keeps backward compatibility.
 */
public record TransactionPlacedEventV2(
        String eventId,
        Instant eventTime,
        String transactionId,
        String merchantId,
        String merchantName,
        BigDecimal amount,
        Instant occurredAt,
        String kind,
        String category   // new in v2; absent/null when reading a v1 event
) {
}
