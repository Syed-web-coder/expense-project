package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.uptimecrew.expense.events.OutboxPoller;
import com.uptimecrew.expense.graphql.MerchantSummary;
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
import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import com.uptimecrew.expense.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeAll;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

// W3 D5 Task 4: asserts trace continuity programmatically via InMemorySpanExporter
// rather than eyeballing Jaeger. We reuse the W3 D3 four containers and add a fifth
// (Jaeger all-in-one) purely for realism -- the assertions read finished spans
// straight out of the in-process OTel SDK, not over HTTP from Jaeger.
//
// NOTE: there is no org.testcontainers:jaegertracing module -- Jaeger runs here as
// a plain GenericContainer on the all-in-one image (note the corrected 1.62.0 tag;
// the bare "1.62" tag does not exist on Docker Hub).
//
// outbox.poll-ms is set to a very high value so the scheduler does not publish
// outbox events autonomously during the Kafka trace test; we call publishBatch()
// explicitly within a root span so all child spans share one traceId.
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
        // Override the OpenTelemetry bean with an SDK whose only exporter is
        // InMemorySpanExporter, using SimpleSpanProcessor (not the default
        // BatchSpanProcessor) so finished spans are visible immediately after
        // each request returns instead of after a ~500ms batching delay.
        //
        // We also register this SDK as GlobalOpenTelemetry so that the
        // opentelemetry-spring-kafka-2.7 producer-side instrumentation (which
        // reads GlobalOpenTelemetry for W3C traceparent header injection) uses
        // the same in-memory exporter. Without this, the producer sends messages
        // with NO traceparent header and the consumer starts an unrelated trace.
        @Bean @Primary
        OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            // W3CTraceContextPropagator is required so that OutboxPoller.inject()
            // and TransactionPlacedListener.extract() actually carry the traceparent
            // header across the Kafka message. Without it, getPropagators() returns
            // a no-op and the consumer always starts an unrelated root trace.
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

        // Bypass real JWT validation in integration tests by accepting any Bearer token
        // and constructing a Jwt with the claims required by MerchantController's
        // @PreAuthorize: SCOPE_merchants.read + ROLE_MERCHANT_READER.
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
    static void setupSchemaAndSeed(@Autowired MerchantRepository pgRepo,
                                   @Autowired MerchantReadModelRepository mongoRepo) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
            stmt.execute(Files.readString(Path.of("db/V3__outbox.sql")));
            // V4 adds the idempotency_key column to expense.transaction, which
            // TransactionEntity maps; without it, recordTransaction() will fail.
            stmt.execute(Files.readString(Path.of("db/V4__transaction_idempotency_key.sql")));
        }
        pgRepo.save(new MerchantEntity("seeded-id-1", "Test Merchant", "5943", Instant.now()));
        mongoRepo.save(new MerchantReadModel("seeded-id-1", "5943", Instant.now(), List.of()));
    }

    @BeforeEach
    void resetExporter() {
        spanExporter.reset();
    }

    // GET /api/v1/merchants/{id} reads from MongoDB (CQRS read model) — there is no
    // JDBC/Postgres call on this path. The endpoint also requires SCOPE_merchants.read +
    // ROLE_MERCHANT_READER, so we send a Bearer token (decoded by the stub JwtDecoder above).
    @Test
    void httpRequest_emits_serverSpan_and_mongoChildSpan() {
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

        boolean hasMongoChild = spans.stream()
                .anyMatch(s -> s.getTraceId().equals(server.getTraceId())
                        && (s.getInstrumentationScopeInfo().getName().toLowerCase().contains("mongo")
                            || s.getName().toLowerCase().contains("find")));

        assertThat(hasMongoChild)
                .as("expected at least one MongoDB child span sharing the HTTP server traceId")
                .isTrue();
    }

    // The Kafka write-through path goes:
    //   TransactionService.recordTransaction() → JPA (Postgres + outbox)
    //   → OutboxPoller.publishBatch()          → Kafka send (traceparent injected)
    //   → TransactionPlacedListener.onMessage() → child span extracted from traceparent
    //
    // We wrap both service calls in a manually-started root span so that all JPA
    // child spans inherit the same traceId. The scheduler is disabled
    // (outbox.poll-ms=9999999) to prevent it from publishing the event under a
    // different trace before we call publishBatch() explicitly.
    //
    // OutboxPoller now injects the current W3C traceparent into the ProducerRecord
    // headers, and TransactionPlacedListener extracts it to start its span as a
    // child — so all spans across JPA + Kafka send + Kafka receive share one traceId.
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
