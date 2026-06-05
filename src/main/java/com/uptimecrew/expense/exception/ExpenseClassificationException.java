package com.uptimecrew.expense.exception;

public abstract class ExpenseClassificationException extends RuntimeException {

    protected ExpenseClassificationException(String message) {
        super(message);
    }

    protected ExpenseClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
