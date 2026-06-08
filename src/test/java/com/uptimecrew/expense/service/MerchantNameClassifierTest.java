package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.uptimecrew.expense.model.TransactionTestDataBuilder.aTransaction;
import static org.junit.jupiter.api.Assertions.*;

class MerchantNameClassifierTest {

    private MerchantNameClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new MerchantNameClassifier();
    }

    @Test
    void classify_regularMerchant_returnsPurchase() {
        Transaction t = aTransaction().withId("txn-001").withMerchantName("Office Depot").build();
        TransactionKind result = classifier.classify(t);
        assertNotNull(result);
        assertEquals(TransactionKind.PURCHASE, result);
    }

    @Test
    void classify_refundMerchant_returnsRefund() {
        Transaction t = aTransaction().withId("txn-002").withMerchantName("refund - Office Depot").build();
        assertEquals(TransactionKind.REFUND, classifier.classify(t));
    }

    @Test
    void classify_nullTransaction_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> classifier.classify(null));
    }
}
