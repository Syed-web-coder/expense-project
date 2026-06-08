package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    @Test
    void constructs_with_valid_inputs() {
        Transaction t = new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertEquals("txn-001", t.id());
        assertEquals("acc-001", t.accountId());
        assertEquals(0, new BigDecimal("487.50").compareTo(t.amount()));
        assertEquals("Office Depot", t.merchantName());
        assertEquals(LocalDate.of(2026, 3, 1), t.occurredOn());
    }

    @Test
    void rejects_null_id() {
        assertThrows(NullPointerException.class, () -> new Transaction(
            null, "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        ));
    }

    @Test
    void rejects_negative_amount() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
            "txn-001", "acc-001", new BigDecimal("-1.00"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        ));
    }

    @Test
    void rejects_blank_merchantName() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "   ", LocalDate.of(2026, 3, 1)
        ));
    }

    @Test
    void equal_instances_have_equal_hashcodes() {
        Transaction a = new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        Transaction b = new Transaction(
            "txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1)
        );
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
