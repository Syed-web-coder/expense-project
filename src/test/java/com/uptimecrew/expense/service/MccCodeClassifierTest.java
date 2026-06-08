package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.uptimecrew.expense.model.TransactionTestDataBuilder.aTransaction;
import static org.junit.jupiter.api.Assertions.*;

class MccCodeClassifierTest {

    private MccCodeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new MccCodeClassifier(Set.of("refund", "credit"));
    }

    @Test
    void classify_merchantMatchesRefundCode_returnsRefund() {
        Transaction t = aTransaction().withMerchantName("REFUND").build();
        assertEquals(TransactionKind.REFUND, classifier.classify(t));
    }

    @Test
    void classify_merchantDoesNotMatchRefundCode_returnsPurchase() {
        Transaction t = aTransaction().withMerchantName("Office Depot").build();
        assertEquals(TransactionKind.PURCHASE, classifier.classify(t));
    }

    @Test
    void classify_nullTransaction_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> classifier.classify(null));
    }

    @Test
    void constructor_nullRefundCodes_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new MccCodeClassifier(null));
    }

    @Test
    void equals_sameInstance_returnsTrue() {
        assertEquals(classifier, classifier);
    }

    @Test
    void equals_null_returnsFalse() {
        assertNotEquals(null, classifier);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(classifier, "not a classifier");
    }

    @Test
    void equals_equalRefundCodes_returnsTrue() {
        MccCodeClassifier other = new MccCodeClassifier(Set.of("refund", "credit"));
        assertEquals(classifier, other);
        assertEquals(classifier.hashCode(), other.hashCode());
    }

    @Test
    void equals_differentRefundCodes_returnsFalse() {
        MccCodeClassifier other = new MccCodeClassifier(Set.of("chargeback"));
        assertNotEquals(classifier, other);
    }
}
