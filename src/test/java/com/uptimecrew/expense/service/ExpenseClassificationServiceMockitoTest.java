package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseClassificationServiceMockitoTest {

    @Mock
    TransactionClassifier strategy;

    @Test
    void classify_delegatesToStrategy_andReturnsSameResult() {
        Transaction transaction = new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        when(strategy.classify(any(Transaction.class))).thenReturn(TransactionKind.PURCHASE);

        ExpenseClassificationService service = new ExpenseClassificationService(strategy);
        TransactionKind result = service.classify(transaction);

        assertEquals(TransactionKind.PURCHASE, result);
        verify(strategy).classify(transaction);
    }
}
