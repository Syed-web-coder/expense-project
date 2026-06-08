package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionDraftTest {

    @Test
    void constructs_with_valid_inputs() {
        TransactionDraft subject = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertEquals("txn-synth-001", subject.getId());
        assertEquals(0, new BigDecimal("487.50").compareTo(subject.getAmount()));
        assertEquals("Office Depot", subject.getMerchantName());
        assertEquals(LocalDate.of(2026, 3, 1), subject.getOccurredOn());
    }

    @Test
    void rejects_null_merchantName() {
        assertThrows(NullPointerException.class, () -> new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            null, LocalDate.of(2026, 3, 1)
        ));
    }

    @Test
    void rejects_negative_amount() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionDraft(
            "txn-synth-001", new BigDecimal("-1.00"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        ));
    }

    @Test
    void equal_instances_have_equal_hashcodes() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        TransactionDraft b = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_sameInstance_returnsTrue() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertEquals(a, a);
    }

    @Test
    void equals_differentType_returnsFalse() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertNotEquals(a, "not a draft");
    }

    @Test
    void equals_differentId_returnsFalse() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        TransactionDraft b = new TransactionDraft(
            "txn-synth-999", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentAmount_returnsFalse() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        TransactionDraft b = new TransactionDraft(
            "txn-synth-001", new BigDecimal("999.99"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentMerchantName_returnsFalse() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        TransactionDraft b = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Staples", LocalDate.of(2026, 3, 1)
        );
        assertNotEquals(a, b);
    }

    @Test
    void equals_differentOccurredOn_returnsFalse() {
        TransactionDraft a = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        TransactionDraft b = new TransactionDraft(
            "txn-synth-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 12, 31)
        );
        assertNotEquals(a, b);
    }
}
