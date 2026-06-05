package com.uptimecrew.expense.exception;

public final class UnrecognizedMerchantException extends ExpenseClassificationException {

    public UnrecognizedMerchantException(String message) {
        super(message);
    }

    public UnrecognizedMerchantException(String message, Throwable cause) {
        super(message, cause);
    }
}
