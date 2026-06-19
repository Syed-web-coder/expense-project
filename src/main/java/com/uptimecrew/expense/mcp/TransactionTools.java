package com.uptimecrew.expense.mcp;

import com.uptimecrew.expense.entity.TransactionEntity;
import com.uptimecrew.expense.service.TransactionService;

import java.math.BigDecimal;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Exposes the expense-project's "record a transaction" write path as an
 * MCP tool, so Claude Code (or any MCP client) can place a transaction
 * directly rather than going through the REST API.
 *
 * Mirrors the curriculum's place_order example (Topic 10):
 *   - @PreAuthorize reuses Day 1's JWT/method-security infrastructure;
 *     MCP is transport, not policy — the same scope check a REST call
 *     would need still applies here.
 *   - idempotencyKey ties this tool back to Day 2's idempotency-key
 *     contract: an agent that retries a slow/ambiguous call gets the
 *     original result back, not a duplicate transaction.
 */
@Component
public class TransactionTools {

    private final TransactionService transactionService;

    public TransactionTools(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Tool(
        name = "place_transaction",
        description = """
            Record a new financial transaction for a known merchant. Returns
            the recorded Transaction with its server-assigned id. Caller must
            have SCOPE_transactions:write. Idempotent on the supplied
            idempotency_key: replaying the same key returns the original
            transaction rather than creating a duplicate.
            """
    )
    @PreAuthorize("hasAuthority('SCOPE_transactions:write')")
    public TransactionEntity placeTransaction(
            @ToolParam(description = "Merchant id this transaction is charged against.")
            String merchantId,

            @ToolParam(description = "Transaction amount, decimal string with 2 places, must be >= 0.")
            BigDecimal amount,

            @ToolParam(description = "Transaction direction: DEBIT or CREDIT.")
            String kind,

            @ToolParam(description = "Idempotency key (UUID); replaying the same key returns the prior result.")
            String idempotencyKey
    ) {
        return transactionService.recordTransaction(merchantId, amount, kind, idempotencyKey);
    }
}
