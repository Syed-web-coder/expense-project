package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.events.OutboxPoller;
import com.uptimecrew.expense.graphql.MerchantSummary;
import com.uptimecrew.expense.repository.MerchantRepository;
import com.uptimecrew.expense.service.TransactionService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "outbox.poll-ms=9999999")
@Testcontainers
@ActiveProfiles("test")
class MerchantObservabilityIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container @ServiceConnection
    static MongoDBContainer mongo =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static GenericContainer<?> jaeger =
            new GenericContainer<>(DockerImageName.parse("jaegertracing/all-in-one:1.62.0"))
                    .withEnv("COLLECTOR_OTLP_ENABLED", "true")
                    .withExposedPorts(16686, 4317, 4318);

    @Autowired InMemorySpanExporter spanExporter;
    @Autowired TestRestTemplate http;
    @Autowired HttpGraphQlTester graphQlTester;
    @Autowired OpenTelemetry openTelemetry;
    @Autowired TransactionService transactionService;
    @Autowired OutboxPoller outboxPoller;
    @Value("${local.server.port}") int port;

    @TestConfiguration
    static class TestOtelConfig {
        @Bean @Primary
        OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
            GlobalOpenTelemetry.resetForTest();
            GlobalOpenTelemetry.set(sdk);
            return sdk;
        }

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean @Primary
        ChatClient.Builder stubChatClientBuilder() {
            return StubChatClientFactory.builderReturning(new MerchantSummary(
                    "STUB", new BigDecimal("100.00"), 3, "STUB",
                    MerchantSummary.Confidence.HIGH));
        }

        @Bean @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .claim("scope", "merchants.read")
                    .claim("roles", List.of("MERCHANT_READER"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    @BeforeAll
    static void seed(@Autowired MerchantRepository pgRepo) {
        pgRepo.save(new MerchantEntity("seeded-id-1", "Test Merchant", "5943", Instant.now()));
    }

    @BeforeEach
    void resetExporter() {
        spanExporter.reset();
    }

    // GET /api/v1/merchants/{id} reads from Postgres (JPA) — the endpoint requires
    // SCOPE_merchants.read + ROLE_MERCHANT_READER, so we send a Bearer token
    // (decoded by the stub JwtDecoder above).
    @Test
    void httpRequest_emits_serverSpan_and_dbChildSpan() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer test-token");
        http.exchange(
                "http://localhost:" + port + "/api/v1/merchants/seeded-id-1",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).isNotEmpty();

        SpanData server = spans.stream()
                .filter(s -> s.getName().contains("/api/v1/merchants"))
                .findFirst()
                .orElseThrow();

        boolean hasDbChild = spans.stream()
                .anyMatch(s -> s.getTraceId().equals(server.getTraceId())
                        && s.getAttributes().get(AttributeKey.stringKey("db.system")) != null);

        assertThat(hasDbChild)
                .as("expected at least one DB child span sharing the HTTP server traceId")
                .isTrue();
    }

    @Test
    void kafkaWriteThrough_singleTraceId_endToEnd() {
        Tracer tracer = openTelemetry.getTracer("com.uptimecrew.expense.test");
        Span root = tracer.spanBuilder("kafka-write-through-test").startSpan();
        try (Scope ignored = root.makeCurrent()) {
            transactionService.recordTransaction("seeded-id-1", new BigDecimal("9.99"), "DEBIT");
            outboxPoller.publishBatch();
        } finally {
            root.end();
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<SpanData> all = spanExporter.getFinishedSpanItems();
            Set<String> traceIds = all.stream().map(SpanData::getTraceId).collect(Collectors.toSet());
            assertThat(traceIds)
                    .as("expected exactly one trace id across JPA writes + Kafka send + Kafka receive")
                    .hasSize(1);
            assertThat(all)
                    .as("expected >= 5 spans (root + JPA saves + Kafka send + Kafka receive)")
                    .hasSizeGreaterThanOrEqualTo(5);
        });
    }

    @Test
    void llmSummarize_spanHasTokenAttributes() {
        graphQlTester
                .document("mutation { summarizeMerchant(id: \"seeded-id-1\") { confidence } }")
                .execute()
                .path("summarizeMerchant.confidence").entity(String.class).isEqualTo("HIGH");

        SpanData llm = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> "llm.summarize".equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no llm.summarize span emitted"));

        assertThat(llm.getAttributes().get(AttributeKey.stringKey("llm.model"))).isNotBlank();
        assertThat(llm.getAttributes().get(AttributeKey.longKey("llm.tokens.in"))).isNotNull();
        assertThat(llm.getAttributes().get(AttributeKey.longKey("llm.tokens.out"))).isNotNull();
    }
}
