package com.uptimecrew.expense.mcp;

import com.uptimecrew.expense.entity.TransactionEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain, JPA-free view of a recorded transaction, returned by MCP tools.
 *
 * TransactionEntity.getMerchant() is a lazy-loaded JPA association
 * (FetchType.LAZY). Returning the entity itself from an MCP tool means
 * Jackson tries to serialise that lazy proxy outside of any active
 * Hibernate session by the time the tool result is converted to JSON --
 * which fails ("Conversion from Object to JSON failed"). Mapping to this
 * record inside the @Transactional service call (while the session is
 * still open) avoids the problem entirely, and keeps JPA internals from
 * leaking across the MCP tool boundary regardless.
 */
public record TransactionView(
        String id,
        String merchantId,
        String merchantName,
        BigDecimal amount,
        Instant occurredAt,
        String kind
) {
    public static TransactionView from(TransactionEntity entity) {
        return new TransactionView(
                entity.getId(),
                entity.getMerchant().getId(),
                entity.getMerchantName(),
                entity.getAmount(),
                entity.getOccurredAt(),
                entity.getKind()
        );
    }
}
