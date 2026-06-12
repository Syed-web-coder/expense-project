package com.uptimecrew.expense.repository;

import com.uptimecrew.expense.entity.MerchantEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class MerchantRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MerchantRepository merchantRepository;

    @BeforeAll
    static void applySchema() throws IOException, SQLException {
        String schema = Files.readString(Path.of("db/V1__schema.sql"));
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(schema);
        }
    }

    @Test
    void save_and_find_round_trip() {
        MerchantEntity merchant = new MerchantEntity(
                "m-it-001", "Test Merchant", "5411",
                Instant.parse("2026-01-01T00:00:00Z"));

        merchantRepository.save(merchant);

        Optional<MerchantEntity> found = merchantRepository.findById("m-it-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Merchant");
        assertThat(found.get().getMccCode()).isEqualTo("5411");
    }

    @Test
    void derived_finder_returns_only_matching_rows() {
        MerchantEntity groceries = new MerchantEntity(
                "m-it-002", "Grocery Store", "5411",
                Instant.parse("2026-01-01T00:00:00Z"));
        MerchantEntity airline = new MerchantEntity(
                "m-it-003", "Airlines Co", "4511",
                Instant.parse("2026-01-01T00:00:00Z"));

        merchantRepository.save(groceries);
        merchantRepository.save(airline);

        List<MerchantEntity> results = merchantRepository.findByMccCode("5411");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMccCode()).isEqualTo("5411");
        assertThat(results.get(0).getName()).isEqualTo("Grocery Store");
    }
}
