package com.uptimecrew.expense.mcp;

import com.uptimecrew.expense.service.TransactionService;

import java.math.BigDecimal;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

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
    public TransactionView placeTransaction(
            @ToolParam(description = "Merchant id this transaction is charged against.")
            String merchantId,

            @ToolParam(description = "Transaction amount, decimal string with 2 places, must be >= 0.")
            BigDecimal amount,

            @ToolParam(description = "Transaction direction: DEBIT or CREDIT.")
            String kind,

            @ToolParam(description = "Idempotency key (UUID); replaying the same key returns the prior result.")
            String idempotencyKey
    ) {
        return transactionService.recordTransactionView(merchantId, amount, kind, idempotencyKey);
    }
}
