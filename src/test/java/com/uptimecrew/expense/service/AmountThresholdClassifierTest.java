package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.model.TransactionKind;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.uptimecrew.expense.model.TransactionTestDataBuilder.aTransaction;
import static org.junit.jupiter.api.Assertions.*;

class AmountThresholdClassifierTest {

    private AmountThresholdClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AmountThresholdClassifier(new BigDecimal("100.00"));
    }

    @Test
    void classify_amountAboveThreshold_returnsPurchase() {
        Transaction t = aTransaction().withAmount(new BigDecimal("150.00")).build();
        assertEquals(TransactionKind.PURCHASE, classifier.classify(t));
    }

    @Test
    void classify_amountAtThreshold_returnsOther() {
        Transaction t = aTransaction().withAmount(new BigDecimal("100.00")).build();
        assertEquals(TransactionKind.OTHER, classifier.classify(t));
    }

    @Test
    void classify_amountBelowThreshold_returnsOther() {
        Transaction t = aTransaction().withAmount(new BigDecimal("50.00")).build();
        assertEquals(TransactionKind.OTHER, classifier.classify(t));
    }

    @Test
    void classify_nullTransaction_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> classifier.classify(null));
    }

    @Test
    void constructor_negativeThreshold_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new AmountThresholdClassifier(new BigDecimal("-0.01")));
    }

    @Test
    void constructor_nullThreshold_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new AmountThresholdClassifier(null));
    }

    @Test
    void constructor_zeroThreshold_isAccepted() {
        AmountThresholdClassifier c = new AmountThresholdClassifier(BigDecimal.ZERO);
        assertEquals(0, c.getThreshold().compareTo(BigDecimal.ZERO));
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
    void equals_equalThreshold_returnsTrue() {
        AmountThresholdClassifier other = new AmountThresholdClassifier(new BigDecimal("100.00"));
        assertEquals(classifier, other);
        assertEquals(classifier.hashCode(), other.hashCode());
    }

    @Test
    void equals_differentThreshold_returnsFalse() {
        AmountThresholdClassifier other = new AmountThresholdClassifier(new BigDecimal("200.00"));
        assertNotEquals(classifier, other);
    }

    @Test
    void equals_differentType_returnsFalse() {
        assertNotEquals(classifier, "not a classifier");
    }
}
