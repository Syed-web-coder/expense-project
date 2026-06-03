package com.uptimecrew.expense.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptTest {

    private static final Instant CAPTURED = Instant.parse("2026-06-03T14:00:00Z");

    private Receipt receipt;

    @BeforeEach
    void setUp() {
        receipt = new Receipt("rec_001", "txn_001", "s3://receipts/rec_001.jpg", CAPTURED);
    }

    // --- happy path ---

    @Test
    void gettersReturnConstructorValues() {
        assertEquals("rec_001", receipt.getId());
        assertEquals("txn_001", receipt.getTransactionId());
        assertEquals("s3://receipts/rec_001.jpg", receipt.getImageRef());
        assertEquals(CAPTURED, receipt.getCapturedAt());
    }

    // --- null rejection ---

    @Test
    void nullIdThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Receipt(null, "txn_001", "s3://receipts/rec_001.jpg", CAPTURED));
    }

    @Test
    void nullTransactionIdThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Receipt("rec_001", null, "s3://receipts/rec_001.jpg", CAPTURED));
    }

    @Test
    void nullImageRefThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Receipt("rec_001", "txn_001", null, CAPTURED));
    }

    @Test
    void nullCapturedAtThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Receipt("rec_001", "txn_001", "s3://receipts/rec_001.jpg", null));
    }

    // --- blank rejection ---

    @Test
    void blankIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Receipt("", "txn_001", "s3://receipts/rec_001.jpg", CAPTURED));
    }

    @Test
    void blankTransactionIdThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Receipt("rec_001", "   ", "s3://receipts/rec_001.jpg", CAPTURED));
    }

    @Test
    void blankImageRefThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Receipt("rec_001", "txn_001", " ", CAPTURED));
    }
}
