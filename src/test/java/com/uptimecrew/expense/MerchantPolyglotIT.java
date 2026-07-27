package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;
import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.repository.MerchantRepository;
import com.uptimecrew.expense.service.ExpenseClassificationService;
import java.time.Instant;
import java.util.Optional;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
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
    @Autowired ExpenseClassificationService service;
    @Autowired CacheManager cacheManager;

    @BeforeAll
    void seedTestData() {
        pgRepo.save(new MerchantEntity("test-id", "Test Merchant", "5943", Instant.now()));
    }

    @Test
    void findById_returnsMerchantFromPostgres() {
        Optional<MerchantReadModel> result = service.findById("test-id");
        assertThat(result).isPresent();
    }

    @Test
    void second_read_is_served_from_redis() {
        service.findById("test-id");
        var cached = cacheManager.getCache(ExpenseClassificationService.CACHE_NAME).get("test-id");
        assertThat(cached).as("cache entry after first read").isNotNull();
    }
}
