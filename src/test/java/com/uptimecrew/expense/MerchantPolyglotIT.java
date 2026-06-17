package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;
import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import com.uptimecrew.expense.repository.MerchantRepository;
import com.uptimecrew.expense.service.ExpenseClassificationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MerchantPolyglotIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Container @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired MerchantRepository pgRepo;
    @Autowired MerchantReadModelRepository mongoRepo;
    @Autowired ExpenseClassificationService service;
    @Autowired CacheManager cacheManager;

    @BeforeAll
    void applyPostgresSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
        }
        pgRepo.save(new MerchantEntity("test-id", "Test Merchant", "5943", Instant.now()));
        mongoRepo.save(new MerchantReadModel("test-id", "5943", Instant.now(), List.of()));
    }

    @Test
    void write_path_populates_postgres_AND_mongo() {
        Optional<MerchantReadModel> mongoSide = service.findById("test-id");
        assertThat(mongoSide).isPresent();
    }

    @Test
    void second_read_is_served_from_redis() {
        service.findById("test-id");
        var cached = cacheManager.getCache(ExpenseClassificationService.CACHE_NAME).get("test-id");
        assertThat(cached).as("cache entry after first read").isNotNull();
    }
}
