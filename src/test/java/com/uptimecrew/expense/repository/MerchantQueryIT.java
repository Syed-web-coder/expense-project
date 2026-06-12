package com.uptimecrew.expense.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MerchantQueryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    void setUpSchema() throws IOException, SQLException {
        String schema = Files.readString(Path.of("db/V1__schema.sql"));
        String fullSeed = Files.readString(Path.of("db/V2__seed.sql"));
        // Stop before the intentional-failure block — it violates a CHECK constraint by design.
        String seed = fullSeed.substring(0, fullSeed.indexOf("-- Intentional failure test"));

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(schema);
            stmt.execute(seed);
        }
    }

    @Test
    void joinsQuery_innerJoinTransactionsWithMerchant_returnsRows() throws IOException, SQLException {
        List<String> stmts = sqlStatements(Path.of("db/queries/joins.sql"));
        String query = stmts.get(0); // INNER JOIN: transactions enriched with merchant info

        List<String> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                rows.add(rs.getString("id"));
            }
        }

        assertThat(rows).isNotEmpty();
    }

    @Test
    void cteQuery_merchantsAboveThreshold_returnsRows() throws IOException, SQLException {
        List<String> stmts = sqlStatements(Path.of("db/queries/cte.sql"));
        String query = stmts.get(0); // WITH totals … WHERE total > 100.00

        List<String> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                rows.add(rs.getString("id"));
            }
        }

        assertThat(rows).isNotEmpty();
    }

    private List<String> sqlStatements(Path path) throws IOException {
        return Arrays.stream(Files.readString(path).split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.toUpperCase().startsWith("SET "))
                .collect(Collectors.toList());
    }
}
