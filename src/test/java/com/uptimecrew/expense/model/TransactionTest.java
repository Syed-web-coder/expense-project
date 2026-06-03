package com.uptimecrew.expense.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private static final BigDecimal AMOUNT = new BigDecimal("487.50");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private Transaction tx;

    @BeforeEach
    void setUp() {
        tx = new Transaction("txn_001", "acc_42", AMOUNT, "Emirates Airline", DATE);
    }

    // --- happy path ---

    @Test
    void gettersReturnConstructorValues() {
        assertEquals("txn_001", tx.getId());
        assertEquals("acc_42", tx.getAccountId());
        assertEquals(0, tx.getAmount().compareTo(AMOUNT));
        assertEquals("Emirates Airline", tx.getMerchantName());
        assertEquals(DATE, tx.getOccurredOn());
    }

    @Test
    void amountIsStoredAtScale2() {
        Transaction t = new Transaction("id", "acc", new BigDecimal("10"), "Shop", DATE);
        assertEquals(2, t.getAmount().scale());
    }

    // --- null rejection ---

    @Test
    void nullIdThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(null, "acc_42", AMOUNT, "Emirates Airline", DATE));
    }

    @Test
    void nullAccountIdThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Transaction("txn_001", null, AMOUNT, "Emirates Airline", DATE));
    }

    @Test
    void nullAmountThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Transaction("txn_001", "acc_42", null, "Emirates Airline", DATE));
    }

    @Test
    void nullMerchantNameThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Transaction("txn_001", "acc_42", AMOUNT, null, DATE));
    }

    @Test
    void nullOccurredOnThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Transaction("txn_001", "acc_42", AMOUNT, "Emirates Airline", null));
    }

    // --- range / blank rejection ---

    @Test
    void negativeAmountThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("txn_001", "acc_42", new BigDecimal("-0.01"),
                        "Emirates Airline", DATE));
    }

    @Test
    void blankIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("   ", "acc_42", AMOUNT, "Emirates Airline", DATE));
    }

    @Test
    void blankMerchantNameThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("txn_001", "acc_42", AMOUNT, "", DATE));
    }
}
