package com.uptimecrew.expense.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uptimecrew.expense.exception.TransactionParseException;
import com.uptimecrew.expense.exception.UnrecognizedMerchantException;
import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.repository.MerchantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseClassificationServiceExceptionPathTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 5);
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");

    private Transaction tx;
    private ListAppender<ILoggingEvent> appender;
    private MerchantRepository repo;
    private com.uptimecrew.expense.readmodel.MerchantReadModelRepository readModelRepo;

    @BeforeEach
    void setUp() {
        tx = new Transaction(UUID.randomUUID().toString(), "acc_1", AMOUNT, "Unknown Corp", DATE);
        repo = Mockito.mock(MerchantRepository.class);
        readModelRepo = Mockito.mock(com.uptimecrew.expense.readmodel.MerchantReadModelRepository.class);

        Logger logger = (Logger) LoggerFactory.getLogger(ExpenseClassificationService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExpenseClassificationService.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void typedExceptionThrownWithCorrectMessage() {
        String expected = "unknown merchant: Unknown Corp";
        ExpenseClassificationService service = new ExpenseClassificationService(
                t -> { throw new UnrecognizedMerchantException(expected); }, repo, readModelRepo);

        UnrecognizedMerchantException ex = assertThrows(
                UnrecognizedMerchantException.class, () -> service.classify(tx));

        assertEquals(expected, ex.getMessage());
    }

    @Test
    void causePreservedInTransactionParseException() {
        IOException ioCause = new IOException("disk error");
        ExpenseClassificationService service = new ExpenseClassificationService(
                t -> { throw new TransactionParseException("parse failed", ioCause); }, repo, readModelRepo);

        TransactionParseException ex = assertThrows(
                TransactionParseException.class, () -> service.classify(tx));

        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void warnLogEmittedOnStrategyFailure() {
        String exceptionMessage = "unknown merchant: Unknown Corp";
        ExpenseClassificationService service = new ExpenseClassificationService(
                t -> { throw new UnrecognizedMerchantException(exceptionMessage); }, repo, readModelRepo);

        assertThrows(UnrecognizedMerchantException.class, () -> service.classify(tx));

        List<ILoggingEvent> warnEvents = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertEquals(1, warnEvents.size());
        assertTrue(warnEvents.get(0).getFormattedMessage().contains(exceptionMessage));
    }
}
