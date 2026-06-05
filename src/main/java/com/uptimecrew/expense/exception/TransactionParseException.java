package com.uptimecrew.expense.exception;

public final class TransactionParseException extends ExpenseClassificationException {

    public TransactionParseException(String message) {
        super(message);
    }

    public TransactionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
