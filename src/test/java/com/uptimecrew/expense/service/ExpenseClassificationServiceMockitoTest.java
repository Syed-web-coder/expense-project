package com.uptimecrew.expense.service;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import com.uptimecrew.expense.repository.MerchantRepository;
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

    @Mock
    MerchantRepository repo;

    @Test
    void classify_delegatesToStrategy_andReturnsSavedEntity() {
        Transaction transaction = new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        when(strategy.classify(any(Transaction.class))).thenReturn(TransactionKind.PURCHASE);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExpenseClassificationService service = new ExpenseClassificationService(strategy, repo);
        MerchantEntity result = service.classify(transaction);

        assertEquals("Office Depot", result.getName());
        verify(strategy).classify(transaction);
        verify(repo).save(any(MerchantEntity.class));
    }
}
