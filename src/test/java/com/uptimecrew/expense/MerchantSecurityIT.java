package com.uptimecrew.expense;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import com.uptimecrew.expense.repository.MerchantRepository;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantSecurityIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Container @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired MockMvc mvc;

    @BeforeAll
    static void setup(@Autowired MerchantRepository pgRepo,
                      @Autowired MerchantReadModelRepository mongoRepo) throws Exception {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db/V1__schema.sql")));
        }
        pgRepo.save(new MerchantEntity("test-id", "Test Merchant", "5943", java.time.Instant.now()));
        mongoRepo.save(new MerchantReadModel("test-id", "5943", java.time.Instant.now(), java.util.List.of()));
    }

    @Test
    void getById_returns200_whenAuthenticatedWithScopeAndRole() throws Exception {
        mvc.perform(get("/api/v1/merchants/test-id")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("SCOPE_merchants.read"),
                    new SimpleGrantedAuthority("ROLE_MERCHANT_READER"))))
           .andExpect(status().isOk());
    }

    @Test
    void getById_returns401_whenAnonymous() throws Exception {
        mvc.perform(get("/api/merchants/test-id"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returns403_whenJwtMissingRole() throws Exception {
        mvc.perform(get("/api/v1/merchants/test-id")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("SCOPE_merchants.read"))))
           .andExpect(status().isForbidden());
    }

    @Test
    void summary_returns429_after10Calls() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/merchants/test-id/summary")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .with(jwt()
                        .jwt(j -> j.subject("rate-limit-user"))
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_merchants.read"),
                            new SimpleGrantedAuthority("ROLE_MERCHANT_READER"))))
               .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/merchants/test-id/summary")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(jwt()
                    .jwt(j -> j.subject("rate-limit-user"))
                    .authorities(
                        new SimpleGrantedAuthority("SCOPE_merchants.read"),
                        new SimpleGrantedAuthority("ROLE_MERCHANT_READER"))))
           .andExpect(status().isTooManyRequests())
           .andExpect(header().string("Retry-After", "60"));
    }
}
