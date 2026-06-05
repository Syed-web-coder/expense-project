package com.uptimecrew.expense.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseCategoryTest {

    private static final BigDecimal PERCENT = new BigDecimal("75");

    private ExpenseCategory category;

    @BeforeEach
    void setUp() {
        category = new ExpenseCategory("cat_001", "Travel", PERCENT);
    }

    // --- happy path ---

    @Test
    void gettersReturnConstructorValues() {
        assertEquals("cat_001", category.getId());
        assertEquals("Travel", category.getName());
        assertEquals(0, category.getDeductiblePercent().compareTo(PERCENT));
    }

    @Test
    void percentIsStoredAtScale2() {
        assertEquals(2, category.getDeductiblePercent().scale());
    }

    @Test
    void zeroPecentIsAccepted() {
        ExpenseCategory c = new ExpenseCategory("id", "Personal", BigDecimal.ZERO);
        assertEquals(0, c.getDeductiblePercent().compareTo(BigDecimal.ZERO));
    }

    @Test
    void hundredPercentIsAccepted() {
        ExpenseCategory c = new ExpenseCategory("id", "Software", new BigDecimal("100"));
        assertEquals(0, c.getDeductiblePercent().compareTo(new BigDecimal("100")));
    }

    // --- null rejection ---

    @Test
    void nullIdThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new ExpenseCategory(null, "Travel", PERCENT));
    }

    @Test
    void nullNameThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new ExpenseCategory("cat_001", null, PERCENT));
    }

    @Test
    void nullDeductiblePercentThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new ExpenseCategory("cat_001", "Travel", null));
    }

    // --- range / blank rejection ---

    @Test
    void negativePercentThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpenseCategory("cat_001", "Travel", new BigDecimal("-1")));
    }

    @Test
    void percentAbove100ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpenseCategory("cat_001", "Travel", new BigDecimal("100.01")));
    }

    @Test
    void blankNameThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpenseCategory("cat_001", "  ", PERCENT));
    }

    @Test
    void blankIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpenseCategory("", "Travel", PERCENT));
    }
}
