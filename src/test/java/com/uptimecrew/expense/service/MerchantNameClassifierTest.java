package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MerchantNameClassifierTest {

    private MerchantNameClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new MerchantNameClassifier();
    }

    @Test
    void classify_regularMerchant_returnsPurchase() {
        Transaction t = new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        TransactionKind result = classifier.classify(t);
        assertNotNull(result);
        assertEquals(TransactionKind.PURCHASE, result);
    }

    @Test
    void classify_refundMerchant_returnsRefund() {
        Transaction t = new Transaction(
            "txn-002", "acc-001", new BigDecimal("487.50"),
            "refund - Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertEquals(TransactionKind.REFUND, classifier.classify(t));
    }
}
