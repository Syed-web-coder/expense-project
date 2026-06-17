package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;

import com.uptimecrew.expense.model.Transaction;
import com.uptimecrew.expense.service.ExpenseClassificationService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApplicationContextLoadIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @BeforeAll
    static void applySchema() throws IOException, SQLException {
        String schema = Files.readString(Path.of("db/V1__schema.sql"));
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(schema);
        }
    }

    @Autowired
    ExpenseClassificationService service;

    @Test
    void context_loads_and_service_bean_is_wired() {
        assertThat(service)
            .as("Spring-managed ExpenseClassificationService should be wired by the context")
            .isNotNull();
    }

    @Test
    void service_delegates_to_primary_strategy() {
        Transaction input = new Transaction("txn-it-01", "acc-001", new BigDecimal("5.00"), "Starbucks", LocalDate.now());
        var result = service.classify(input);
        assertThat(result).isNotNull();
    }
}
