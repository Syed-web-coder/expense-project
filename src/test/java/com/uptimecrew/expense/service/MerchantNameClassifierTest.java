package com.uptimecrew.expense.service;

import com.uptimecrew.expense.exception.UnrecognizedMerchantException;
import com.uptimecrew.expense.model.ExpenseCategory;
import com.uptimecrew.expense.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MerchantNameClassifierTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);
    private static final BigDecimal AMOUNT = new BigDecimal("487.50");

    private MerchantNameClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = MerchantNameClassifier.withDefaults();
    }

    // --- interface contract ---

    @Test
    void classifyReturnsExpenseCategoryInstance() {
        Transaction tx = new Transaction(
                UUID.randomUUID().toString(), "acc_1", AMOUNT, "Emirates Airline", DATE);

        ExpenseCategory result = classifier.classify(tx);

        assertNotNull(result);
        assertInstanceOf(ExpenseCategory.class, result);
    }

    @Test
    void airlineMerchantClassifiedAsTravel() {
        Transaction tx = new Transaction(
                UUID.randomUUID().toString(), "acc_1", AMOUNT, "Emirates Airline", DATE);

        ExpenseCategory result = classifier.classify(tx);

        assertNotNull(result);
        assertEquals("Travel", result.getName());
    }

    @Test
    void restaurantMerchantClassifiedAsMeals() {
        Transaction tx = new Transaction(
                UUID.randomUUID().toString(), "acc_1", AMOUNT, "The Grand Restaurant", DATE);

        ExpenseCategory result = classifier.classify(tx);

        assertNotNull(result);
        assertEquals("Meals", result.getName());
    }

    @Test
    void unknownMerchantThrowsUnrecognizedMerchantException() {
        Transaction tx = new Transaction(
                UUID.randomUUID().toString(), "acc_1", AMOUNT, "Random Shop", DATE);

        assertThrows(UnrecognizedMerchantException.class, () -> classifier.classify(tx));
    }

    @Test
    void classifyIsCaseInsensitive() {
        Transaction tx = new Transaction(
                UUID.randomUUID().toString(), "acc_1", AMOUNT, "CITY PHARMACY", DATE);

        ExpenseCategory result = classifier.classify(tx);

        assertNotNull(result);
        assertEquals("Healthcare", result.getName());
    }

    // --- null rejection ---

    @Test
    void nullTransactionThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> classifier.classify(null));
    }

    @Test
    void nullKeywordMapThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new MerchantNameClassifier(null));
    }

    // --- custom keyword map ---

    @Test
    void customMapIsRespected() {
        ExpenseCategory officeSupplies = new ExpenseCategory(
                UUID.randomUUID().toString(), "Office Supplies", new BigDecimal("100"));

        MerchantNameClassifier custom = new MerchantNameClassifier(
                Map.of("staples", officeSupplies));

        Transaction tx = new Transaction(
                UUID.randomUUID().toString(), "acc_1", AMOUNT, "Staples Store", DATE);

        ExpenseCategory result = custom.classify(tx);

        assertNotNull(result);
        assertEquals("Office Supplies", result.getName());
    }
}
