package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionLedgerTest {

    private TransactionLedger ledger;
    private Transaction t1;
    private Transaction t2;
    private Transaction t3;

    @BeforeEach
    void setUp() {
        t1 = new Transaction("txn-001", "acc-001", new BigDecimal("487.50"),
            "Office Depot", LocalDate.of(2026, 3, 1));
        t2 = new Transaction("txn-002", "acc-001", new BigDecimal("120.00"),
            "Office Max", LocalDate.of(2026, 3, 2));
        t3 = new Transaction("txn-003", "acc-002", new BigDecimal("50.00"),
            "Starbucks", LocalDate.of(2026, 3, 3));
        ledger = new TransactionLedger(List.of(t1, t2, t3));
    }

    @Test
    void size_returnsCorrectCount() {
        assertEquals(3, ledger.size());
    }

    @Test
    void findById_existingId_returnsTransaction() {
        Optional<Transaction> result = ledger.findById("txn-001");
        assertTrue(result.isPresent());
        assertEquals(t1, result.get());
    }

    @Test
    void findById_missingId_returnsEmpty() {
        Optional<Transaction> result = ledger.findById("txn-999");
        assertTrue(result.isEmpty());
    }

    @Test
    void findById_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> ledger.findById(null));
    }

    @Test
    void findByMerchantAbove_matchesFragmentAndThreshold() {
        List<Transaction> result = ledger.findByMerchantAbove("office", new BigDecimal("100.00"));
        assertEquals(2, result.size());
        assertEquals(t1, result.get(0));
        assertEquals(t2, result.get(1));
    }

    @Test
    void findByMerchantAbove_noMatches_returnsEmptyList() {
        List<Transaction> result = ledger.findByMerchantAbove("amazon", new BigDecimal("10.00"));
        assertTrue(result.isEmpty());
    }

    @Test
    void constructor_nullCollection_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new TransactionLedger(null));
    }
}
