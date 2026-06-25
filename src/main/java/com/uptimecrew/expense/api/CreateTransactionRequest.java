package com.uptimecrew.expense.api;

import java.math.BigDecimal;

public record CreateTransactionRequest(String merchantId, BigDecimal amount, String kind) {
}
