package com.uptimecrew.expense.service;

import com.uptimecrew.expense.exception.TransactionParseException;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;

import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ParseFailureClassifier implements TransactionClassifier {

    @Override
    public TransactionKind classify(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        try {
            throw new IOException("synthetic cause");
        } catch (IOException cause) {
            throw new TransactionParseException("failed parsing transaction row", cause);
        }
    }
}
