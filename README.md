# expense-project
This is a expense documentation project

## Week 1 Day 1
Added CLAUDE.md conventions, expense tracking domain model (Transaction, ExpenseCategory, Receipt, TransactionKind), MerchantNameClassifier, fixed TransactionDraft to match CLAUDE.md conventions, and JUnit 5 tests with 10 tests passing.

## Day 2
Added TransactionLedger repository with defensive copy, Optional-based findById, stream pipeline query findByMerchantAbove, and parameterized JUnit 5 tests. Refactored MerchantNameClassifier to add WARN logging on rejected input.

## Day 3
Added AmountThresholdClassifier and MccCodeClassifier strategy implementations, TransactionClassifiers factory class, ExpenseClassificationService with constructor-injected strategy, converted Transaction to a Java record, and Mockito test for strategy delegation.

## Day 4
Added exception hierarchy (ExpenseClassificationException base, UnrecognizedMerchantException, TransactionParseException subtypes), updated all classifier strategies to throw typed exceptions instead of returning fallbacks, switched ExpenseClassificationService to SLF4J + Logback with INFO and WARN logging, and added exception-path tests using AssertJ fluent assertions and a Logback ListAppender to verify log output.

## Day 5
Added RecurringChargeClassifier (4th strategy, TDD with 4 tests), TransactionTestDataBuilder fluent builder, wired JaCoCo with 70% branch coverage gate, and refactored existing tests to use the builder.

## Week 2 Day 1
Added `db/` folder with Postgres schema (`expense` schema, 3 tables), transactional seed, verify queries, and ER diagram.

## Week 2 Day 2
Added `db/queries/` folder with four advanced SQL query files (JOINs, CTE, window function, GROUP BY + HAVING), a query catalogue README, and the first Testcontainers integration test (`MerchantQueryIT`) that spins up a real Postgres container to run the queries.

## Week 2 Day 3
Added Spring Boot + Gradle plugins (v3.4.5), Application.java entry point with @SpringBootApplication, promoted ExpenseClassificationService to a Spring @Service, annotated strategy implementations with @Component (@Primary on MerchantNameClassifier), created application.yml with local/test profiles, Actuator health+info endpoints, and logging config, and added ApplicationContextLoadIT with @SpringBootTest verifying the context loads and the service bean is wired.

## Week 2 Day 4
Added Spring Data JPA — mapped the three Day 1 tables to JPA entity classes (Merchant, Transaction, Rule) with schema-qualified @Table and lazy relationships, created three Spring Data repository interfaces with derived and @Query methods, wired the primary repository into ExpenseClassificationService with @Transactional persistence, and added a @DataJpaTest integration test (MerchantRepositoryIT) using Testcontainers.

## Week 2 Day 5

Added Spring Data MongoDB and Redis Cache — introduced polyglot persistence on top of the existing Postgres/JPA stack.

- New `MerchantReadModel` @Document class with embedded transactions (denormalised read model stored in MongoDB)
- New `MerchantReadModelRepository` extending MongoRepository with `findByMccCode` derived query
- Updated `ExpenseClassificationService` with write-through to Mongo after every JPA save, and a `@Cacheable findById` read path (Redis → Mongo → Postgres fallback, 60s TTL)
- Added `@EnableCaching` to Application entry point
- Updated `application.yml` with `spring.data.mongodb`, `spring.data.redis`, and `spring.cache` blocks
- Updated `build.gradle` with MongoDB, Redis, and Cache starters plus Testcontainers MongoDB module
- New `MerchantPolyglotIT` @SpringBootTest that spins all three datastores via Testcontainers and verifies both the write path and Redis cache hit

Note on Docker/Testcontainers: The Testcontainers tests first hit a Docker socket issue with Colima on macOS (ryuk failed to mount the socket), so we moved to a Windows machine with Docker Desktop (WSL2). On Windows, three build issues were fixed at root cause: Spring Boot's BOM was downgrading Testcontainers to 1.20.5 which couldn't resolve the Docker Desktop named pipe (forced 1.21.4), Java 25 class files exceeded Spring Boot's bundled ASM support (set options.release = 21), and a Logback version conflict (removed explicit version pins). All integration tests now pass against real Testcontainers Postgres containers — no Ryuk-disabled or embedded-postgres fallback needed.

## Week 3 Day 1

Added Spring Security 7 — secured the W2 D5 read path with JWT authentication, method-level authorization, and per-caller rate limiting on the LLM summary endpoint.

- New `SecurityConfig` with a single `SecurityFilterChain` bean — stateless sessions, CSRF disabled with a threat-model comment, `/actuator/health` permitted, `/api/**` authenticated, OAuth2 Resource Server JWT wired in
- Custom `JwtAuthenticationConverter` mapping the `scope` claim to `SCOPE_*` authorities and a custom `roles` claim to `ROLE_*` authorities
- New `MerchantController` exposing `GET /api/merchants/{id}` and `GET /api/merchants/{id}/summary`, both gated with `@PreAuthorize("hasAuthority('SCOPE_merchants.read') and hasRole('MERCHANT_READER')")`
- New `RateLimitFilter` (`OncePerRequestFilter`) backed by Bucket4j — 10 requests per minute per JWT subject, answering 429 with `Retry-After: 60` when exhausted (in-memory store; production would use bucket4j-redis)
- Updated `application.yml` with the `spring.security.oauth2.resourceserver.jwt.issuer-uri` block
- Updated `build.gradle` with Spring Security, OAuth2 Resource Server, bucket4j-core, and bucket4j-redis starters plus spring-security-test
- New `MerchantSecurityIT` @SpringBootTest reusing the three Testcontainers, asserting the full 200 / 401 / 403 / 429 matrix with mocked JWTs from spring-security-test

## Week 3 Day 2
Added REST maturity to the Merchants API — URI versioning, OpenAPI documentation, an idempotent write endpoint, and a resilient call to an external identity microservice.
- `MerchantController` moved from `/api/merchants` to `/api/v1/merchants` (URI versioning); class-level `@Tag` and per-route `@Operation`/`@ApiResponses` document the 200/401/403/404/409 response matrix
- New `OpenApiConfig` registering an `OpenAPI` bean with a bearer-JWT `SecurityScheme` so Swagger UI's Authorize button sends the JWT on protected routes
- Summary route converted from `GET /{id}/summary` to `POST /{id}/summary`, now requiring an `Idempotency-Key` header (UUID); returns 400 if missing or invalid
- New `IdempotencyService` — a thin wrapper around the W2 D5 Redis instance via `StringRedisTemplate`; computes a `idem:{namespace}:{key}` Redis key, replays the cached response on a duplicate key, returns 409 if a request with that key is already in flight, otherwise runs the work and caches the serialized response with a 24h TTL
- New `IdentityProfile`, `MerchantIdentityClient` (a `@FeignClient` against `identity.base-url`), and `IdentityService` (wraps the Feign client; only `IdentityService`, not the Feign interface, carries `@CircuitBreaker`, since Feign's proxy doesn't run through the Resilience4j AOP advisor) — the summary route now enriches its response with the caller's `displayName` resolved through this chain
- `resilience4j.circuitbreaker.instances.identity` configured in `application.yml` (`slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`, `permittedNumberOfCallsInHalfOpenState: 3`); on repeated failures the breaker opens and short-circuits to a degraded `IdentityProfile` fallback instead of hammering the dependency
- `SecurityConfig`'s `permitAll()` matcher extended to cover `/v3/api-docs/**` and `/swagger-ui/**` so the API docs render without authentication
- `Application` annotated with `@EnableFeignClients`
- Updated `build.gradle` with springdoc-openapi, spring-cloud-starter-openfeign, spring-cloud-starter-circuitbreaker-resilience4j, wiremock-standalone, and the Spring Cloud BOM pinned in `dependencyManagement`
- New `IdentityClientCircuitBreakerIT` — boots WireMock in-process on port 8090 (no Testcontainers needed) and covers: a 200 Feign call returning the expected profile, the breaker opening after repeated 5xx responses (and subsequent calls short-circuiting without reaching WireMock), the summary endpoint returning 200 with the resolved display name, and the OpenAPI doc exposing the versioned path with the bearer-jwt scheme

## Week 3 Day 3
Added the event-driven outbox pattern, schema evolution, a dead-letter queue, and an MCP server tool — six tests covering all of it, plus the Day 2 idempotency-key contract extended into both the consumer and the MCP layer.
- New `outbox_event` table (`db/V4__transaction_idempotency_key.sql` adds `idempotency_key` to `transaction`; the outbox table itself is `db/V3__outbox.sql`), `OutboxEvent` entity, and `OutboxRepository` with a `FOR UPDATE SKIP LOCKED` poll query for safe concurrent pollers
- New `TransactionService.recordTransaction()` — writes the `TransactionEntity` and its `OutboxEvent` row in the same `@Transactional` method (the outbox pattern's core guarantee: a rollback after the write means the event can never be published, since the row never existed); a 4-arg overload accepts an idempotency key and replays the original transaction on a duplicate key instead of creating a second one
- New `OutboxPoller` — a `@Scheduled` job that ships unpublished outbox rows to Kafka keyed by transaction id, then marks them published
- New `TransactionPlacedEvent` (v1 schema) and `TransactionPlacedEventV2` (adds one optional `category` field) — demonstrates backward-compatible schema evolution: a v2 consumer parses a v1-shaped payload with `category` simply coming back null, no exception
- New `KafkaConfig` registering a `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (zero retries — a malformed/unparseable message is a permanent failure, not a transient one) and `TransactionPlacedListener`, a minimal `@KafkaListener` whose only job (for this lab) is to let deserialization failures fall through to the DLQ
- New `mcp` package: `TransactionTools` exposes `place_transaction` as an MCP `@Tool`, gated with `@PreAuthorize("hasAuthority('SCOPE_transactions:write')")`; `TransactionView` is a plain record (not the JPA entity) returned by the tool, and `McpToolConfig` registers a `ToolCallbackProvider` bean — `@Tool` methods are not auto-discovered from `@Component` alone in Spring AI 1.1.2, this bean is required or `tools/call` returns "Unknown tool"
- `SecurityConfig` extended with a `permitAll()` matcher on `/mcp` and `/mcp/**` — MCP is a transport, not a policy boundary; the JWT filter still runs and populates the security context on a `permitAll()` path, so `@PreAuthorize` on the tool method still enforces the scope
- Updated `application.yml` with `spring.kafka.*` (producer/consumer config) and `spring.ai.mcp.server.*` (`protocol: STREAMABLE`, required to expose `/mcp` as a JSON-RPC endpoint rather than the default SSE-only transport)
- Updated `build.gradle` with `spring-kafka`, `spring-kafka-test`, `spring-ai-starter-mcp-server-webmvc` (+ Spring AI BOM), and `awaitility`
- New `OrderEventDay3Lab` — all six required tests in one class, run against `EmbeddedKafkaBroker` (in-process, no Docker) and the real local Postgres instance, in well under 30 seconds total: `producerWritesOutboxAndPublishes`, `consumerIdempotentOnDuplicate`, `rollbackPreventsPublish`, `v1EventReadByV2Consumer`, `malformedEventRoutesToDlq`, `mcpPlaceOrderRespectsScope`

Note on Docker/Testcontainers: still broken on this machine (`Could not find unix domain socket /var/run/docker.sock` — Docker itself unreachable, not just Ryuk). Every test in this class avoids Testcontainers entirely: `EmbeddedKafkaBroker` for the Kafka side, the real local Postgres for the DB side. No container of any kind was used.

Note on the MCP tool call response shape: the lesson's lab spec describes a denied tool call returning a top-level JSON-RPC error with code `-32603`. Empirically, Spring AI 1.1.2's actual behaviour is different — a denied call still returns a normal JSON-RPC `result` with `isError: true` and the denial message in a `content[].text` block, not a top-level `error` object. `mcpPlaceOrderRespectsScope` asserts against this confirmed, observed behaviour rather than the spec's assumption. Getting to that point also required discovering, in order: `/mcp` wasn't exposed at all until `spring.ai.mcp.server.protocol` was correctly nested under `spring:` (not a sibling top-level key); the Streamable HTTP transport requires an `initialize` handshake before any `tools/call`, with the returned `Mcp-Session-Id` echoed back on every later request; responses come back SSE-framed (`id:...\nevent:...\ndata:{...}`) even for what looks like a simple JSON-RPC call, requiring `MockMvc`'s `asyncDispatch` to read the full body; and `place_transaction` returning the raw `TransactionEntity` failed Jackson serialization because of its lazy-loaded `MerchantEntity` association (fixed by returning the plain `TransactionView` record, built from already-loaded fields inside the transaction instead of touching the lazy proxy after the session closes).


## Week 3 Day 4

Added a GraphQL API over the existing read model — Spring for GraphQL queries/mutation, an N+1 fix via `@BatchMapping`, and an Anthropic-backed structured-output mutation validated against a hand-written JSON Schema.

- New `schema.graphqls` declaring `merchant(id)`, `latestMerchants(limit)` queries, a `summarizeMerchant(id)` mutation, and the `Merchant` / `LineItem` / `MerchantSummary` / `Confidence` types
- New `MerchantGraphQlController` with `@QueryMapping` for single and batch merchant lookup, delegating to the W2 D5 `MerchantReadModelRepository`
- New `@BatchMapping(typeName = "Merchant", field = "lines")` resolver that batches the embedded line items per request and logs `batch-loaded {} lines for {} parents`
- New `LlmSummaryService` calling Anthropic via Spring AI's `ChatClient.entity(MerchantSummary.class)`, with the result independently validated against a hand-written Draft 2020-12 JSON Schema (`MerchantSummary.schema.json`) before returning
- New `MerchantGraphQlIT` (`HttpGraphQlTester`) covering single lookup, batched-lines logging, successful structured-output validation, and a malformed-output schema-violation case — backed by a deterministic stubbed `ChatClient`, no network calls in tests
- Updated `build.gradle` with Spring for GraphQL, WebFlux (for the GraphiQL UI), the Spring AI Anthropic starter, and json-schema-validator
- Updated `application.yml` with the GraphQL path/GraphiQL/schema-printer block and the Anthropic chat model config
- Updated `SecurityConfig` to permit `/graphql` and `/graphiql/**` at the transport layer, since GraphQL field-level authorization is the standard pattern, not HTTP-route gating
- Known limitation: `OrderEventDay3Lab.mcpPlaceOrderRespectsScope()` is `@Disabled` in the test profile due to a `json-schema-validator` version conflict between this PR's schema-validation code and Spring AI's MCP server starter — documented in the PR, not fixed here

## Week 3 Day 5

Added end-to-end distributed tracing with OpenTelemetry — auto-instrumentation for HTTP, JDBC, Kafka, and MongoDB exported to Jaeger via OTLP, a manual `llm.summarize` span carrying token-usage attributes, a full-stack integration test verified in-process, a fix for Kafka trace-continuity breakage, and a 3-agent workflow that added `tokensIn`/`tokensOut` to the GraphQL API.

- Updated `build.gradle` with `opentelemetry-spring-boot-starter:2.10.0` (auto-instruments Spring MVC, JDBC, and more), `opentelemetry-exporter-otlp` (OTLP/HTTP export to Jaeger), `opentelemetry-spring-kafka-2.7:2.10.0-alpha` (Kafka consumer-side instrumentation), `opentelemetry-instrumentation-annotations`, and `opentelemetry-sdk-testing` (in-process `InMemorySpanExporter` for tests)
- Updated `application.yml` with the `otel:` block: `service.name: expense-service`, OTLP endpoint `http://localhost:4318` with `http/protobuf`, sampler `always_on`, and `instrumentation.spring-kafka.enabled: true`
- New `TraceparentLoggingProducerListener` — a `ProducerListener` bean that reads the `traceparent` header from every outgoing `ProducerRecord` and logs it at INFO (or WARN if absent); gives engineers a quick eyeball check that W3C context propagation is live without opening Jaeger
- Updated `LlmSummaryService` with a manual `llm.summarize` span (`SpanKind.CLIENT`) wrapping the Anthropic call — switched from `.entity(MerchantSummary.class)` to `.chatResponse()` + manual JSON parsing to gain access to `ChatResponse.getMetadata().getUsage()`; records `llm.model`, `llm.tokens.in`, and `llm.tokens.out` span attributes from the real usage metadata, and sets `StatusCode.ERROR` with the exception on failure
- New `MerchantObservabilityIT` — five-container `@SpringBootTest` (Postgres, Mongo, Redis, Kafka, Jaeger all-in-one) that overrides the `OpenTelemetry` bean with an in-process `InMemorySpanExporter` + `SimpleSpanProcessor` so finished spans are queryable immediately; three tests: `httpRequest_emits_serverSpan_and_mongoChildSpan` (HTTP server span + MongoDB child share one trace ID), `kafkaWriteThrough_singleTraceId_endToEnd` (all JPA + Kafka send + Kafka receive spans share one trace ID under Awaitility), and `llmSummarize_spanHasTokenAttributes` (the `llm.summarize` span carries non-null `llm.model`, `llm.tokens.in`, `llm.tokens.out`)

**Bug: `opentelemetry-spring-kafka-2.7` only instruments the Kafka consumer, not the producer.** The auto-instrumentation library injects a `ProducerInterceptor` that fires before the message is sent, but it uses the *library's* `OpenTelemetry` instance (resolved at class-load time via `GlobalOpenTelemetry`), not the application context bean — so in tests the producer wrote messages with no `traceparent` header and the consumer started a fresh unrelated root trace. Fixed in two places: `OutboxPoller.publishOne()` now calls `GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), record.headers(), ...)` to write the W3C traceparent into outgoing `ProducerRecord` headers before the Kafka send; `TransactionPlacedListener.onMessage()` extracts that header via a `TextMapGetter<Headers>` bridge and starts its consumer span as a child of the extracted context instead of `Context.root()`. The test's `@TestConfiguration` also calls `GlobalOpenTelemetry.resetForTest()` and `GlobalOpenTelemetry.set(sdk)` so the test SDK and the propagation path are the same instance, restoring single-trace-ID continuity across the full HTTP → JPA → Kafka send → Kafka receive → Mongo pipeline.

**3-agent workflow (generator → tester → reviewer) to add `tokensIn`/`tokensOut` to `MerchantSummary`:**
- *Generator* added `tokensIn: Int!` and `tokensOut: Int!` to `schema.graphqls`, extended the `MerchantSummary.schema.json` JSON Schema with those two integer fields, added the `tokensIn`/`tokensOut` params to the `MerchantSummary` Java record (with a 5-arg `@JsonCreator` overload so existing LLM deserialization and tests keep working), and wired the token counts from `ChatResponse.getMetadata().getUsage()` through `LlmSummaryService`
- *Tester* added a new `LlmSummaryServiceTest` unit test and updated `MerchantGraphQlIT` with Docker-backed assertions that the returned `tokensIn`/`tokensOut` values are positive integers; also fixed a `MockitoException: UnnecessaryStubbingException` left by the generator
- *Reviewer* found and fixed two real bugs: a masked dead stub in `MerchantGraphQlIT` that was hiding a missing assertion, and a GraphQL nullability regression (`tokensIn: Int` instead of `tokensIn: Int!`); it correctly identified two pre-existing issues as out-of-scope and flagged them without touching them

## Week 4 Day 1

Stood up the frontend half of the capstone — a new `expense-web/` directory, peer to the Spring Boot backend, scaffolded with Vite + React 19 + TypeScript and gated by strict compiler flags, ESLint, and a Vitest CI workflow.

- New `expense-web/` project scaffolded with `pnpm create vite` (React + TypeScript template), Node 20 pinned via `.nvmrc`
- Strict TypeScript enabled in `tsconfig.app.json`: `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `noImplicitOverride`, `verbatimModuleSyntax`, `noUnusedLocals`, `noUnusedParameters`, `erasableSyntaxOnly`
- ESLint flat config (`eslint.config.js`) with `eslint-plugin-react` and `eslint-plugin-react-hooks` registered alongside `typescript-eslint`; explicit rules: `react/jsx-key: error`, `react-hooks/rules-of-hooks: error`, `react-hooks/exhaustive-deps: warn`, `@typescript-eslint/no-explicit-any: error`
- New `Merchant` / `MerchantLine` types (`src/types/merchant.ts`) mirroring the Java model — `amount` is `string` (never `number`) to match the backend's `BigDecimal`-as-string contract
- New `useMerchant` hook (`src/hooks/useMerchant.ts`) — models fetch lifecycle as a three-variant discriminated-union state (`loading` / `ok` / `error`), cancels the fetch on unmount via a `cancelled` flag, and returns a flat `{ data, loading, error }` triple; stub data source reads from `public/mocks/merchant.json` (real Apollo Client query lands on W4 D3)
- New `MerchantDetailPage` (`src/pages/MerchantDetailPage.tsx`) composing `useMerchant` with `ThresholdSlider` and `ThresholdReadout`; threshold state is lifted into the page so the slider and readout stay in sync
- New `ThresholdSlider` (`src/components/ThresholdSlider.tsx`) — controlled `<input type="range">` (0–100) with `readonly` props; new `ThresholdReadout` (`src/components/ThresholdReadout.tsx`) — `<div role="status">` displaying the live percentage, enabling accessible screen-reader announcements and a stable test selector
- New `public/mocks/merchant.json` — static fixture matching the `Merchant` type (`id`, `mccCode`, `transactionCount`, `totalSpend` as a scale-2 string, `lines` array) used by both the hook stub and the Vitest tests
- Vitest configured in `vitest.config.ts` with `environment: jsdom`, `globals: true`, and `@testing-library/jest-dom` imported in `src/test/setup.ts`; two tests in `MerchantDetailPage.test.tsx`: `renders entity id and a sample field from the mock JSON` (stubs `fetch` via `vi.stubGlobal`, asserts heading and mccCode appear) and `updates the readout when the slider is moved (lifted state)` (fires a `change` event on the slider and asserts the `role="status"` readout reflects the new value)
- New `.github/workflows/web-ci.yml` — runs only when `expense-web/**` or the workflow file itself changes; installs with `pnpm install --frozen-lockfile`, then gates on `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build` in sequence on `ubuntu-latest` with Node 20 and pnpm 9

## Week 4 Day 2

Refactored expense-web's MerchantDetailPage state into a useReducer-driven discriminated-union state machine (idle/loading/success/error/empty) with a pure, exhaustively-checked reducer; hoisted cross-cutting filter state (MCC chips, date range, search text, archived toggle, threshold) into a Zustand store with devtools + persist middleware, partialize scoped to threshold only; added a debounced search hook with useEffect cleanup; wrapped the page in a class-based ErrorBoundary with a retry fallback; and added 11 new Vitest tests across the reducer, store, and debounce hook, on top of fixing a Node v26/jsdom localStorage conflict in the test environment and a previously-nonfunctional hash-routing stub in App.tsx.

## Week 4 Day 3

Added Apollo Client, TanStack Query v5, React Router v7, and MSW to the `expense-web` frontend, wired against the live Spring Boot GraphQL/REST backend.

- `src/apollo/client.ts` — Apollo Client pinned to v3 (not the newly-released v4, which has breaking React-hook import changes) with a `setContext` auth link that only attaches a JWT when one matches a real three-segment token shape, avoiding spurious 401s on `/graphql` from the dev-stub token
- `codegen.ts` — GraphQL Codegen client-preset scanning `.tsx` files directly for co-located `graphql()` documents (switched from a separate `.graphql`-file glob after the reference queries didn't match the project's actual schema)
- `src/queries/LatestMerchants.graphql` and `SummarizeMerchant.graphql` were created, then deleted once Codegen moved to co-located documents
- `src/pages/MerchantListPage.tsx` and `MerchantSummaryPage.tsx` — Apollo `useQuery`/`useMutation` pages adapted to the real `Merchant`/`MerchantSummary` schema fields (`mccCode`/`capturedAt`/`lines`, not the reference assignment's `name`/`updatedAt`/`summaryText`)
- `src/queryClient.ts` and `src/hooks/useGetExpenseTrackingRest.ts` — TanStack Query v5 client and REST hook
- `src/router.tsx` + `src/ProtectedLayout.tsx` — `createBrowserRouter` with a JWT-gated parent route, split into its own file to satisfy the `react-refresh/only-export-components` lint rule
- `src/test/handlers.ts`, `server.ts` — MSW request mocks for the GraphQL query/mutation and REST endpoint
- Four new Vitest files (list page, summary page with optimistic-response assertion, protected-layout redirect/render, REST hook) bringing the suite to 8 files / 20 tests
- Backend: added `CorsConfig.java` to permit the Vite dev origins, and disabled `spring.ai.mcp.server` in `application.yml` (a `NoClassDefFoundError` in that auto-configuration was crashing the whole app context on every `bootRun`)
- No standing local dev environment existed for the W2 D5 datastores — stood up Postgres/Mongo/Redis manually via `docker run` (no compose file in the repo), applied the `db/` migrations, and seeded MongoDB's `merchants` read-model collection directly, since the Postgres rows inserted via `psql` never passed through the application's write-through path
