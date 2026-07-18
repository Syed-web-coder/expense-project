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

## Week 4 Day 4

Replaced the W4D3 one-shot "Summarize" GraphQL mutation with a full streaming chat UI. A thin Hono server (server/) acts as a proxy between the Vite frontend and the W3D4 Spring AI backend, forwarding requests through Vercel AI SDK's streamText and returning a text/event-stream response the browser renders token-by-token. Two server-side tools (lookupMerchant, classifyDeduction) are wired into streamText with zod schemas. The UI (MerchantChatPanel) uses useChat from @ai-sdk/react with Stop, Regenerate, a loading spinner, and an error pane. Completed assistant messages are persisted to localStorage via a Zustand store (useMerchantChatStore) with the persist middleware, written only in onFinish — never on every token. Covered by 21 new Vitest tests (41 total) using a hand-rolled MSW ReadableStream SSE handler.
Key technical challenge: The installed SDK versions (ai@7.0.4, @ai-sdk/react@4.0.5) were several major versions ahead of the lesson's reference code. Every API surface the reference assumed had changed: useChat no longer manages input state, toDataStreamResponse was renamed to toUIMessageStreamResponse, convertToModelMessages is async (missing await caused a TypeError: messages.some is not a function at runtime), parameters became inputSchema on tool definitions, maxSteps was replaced by stopWhen: stepCountIs(n), and tool calls render as typed message.parts entries rather than message.toolInvocations. Each divergence was caught via pnpm typecheck or runtime debugging, then verified against the official AI SDK migration docs before fixing.

Docker limitation (consistent with prior weeks): Live tool-call execution against a real model was not verifiable without Docker/the W3D4 Spring AI backend. Verified with a local canned-stream Node server instead. The non-tool-call streaming path, Stop, Regenerate, error handling, and Zustand persistence were all fully confirmed in-browser.

## Week 4 Day 5

Added RTL+Vitest harness, MSW integration tests, Playwright E2E, ESLint 9 flat config, and jest-axe accessibility checks. 60 Vitest tests across 15 files, 1 Playwright E2E test. New scripts: pnpm test, pnpm lint, pnpm e2e, pnpm check. Branch: week04/day5/Syed-web-coder.

## Week 5 Day 1 — Docker, Multi-Stage Build, Distroless & CI Scan Gate

## What I built
Containerised the existing Spring Boot expense-tracking service as a hardened Docker image with a full CI gate.

## Files added
| File | Purpose |
|------|---------|
| `Dockerfile` | Three-stage build (builder → extractor → runtime), digest-pinned base images, layered JAR layout, non-root user, HEALTHCHECK, OCI labels |
| `.dockerignore` | Excludes `.git`, `build/`, `.env*`, `*.pem`, `*.key`, `secrets/`, `credentials.json` |
| `.hadolint.yaml` | Dockerfile linting config with `failure-threshold: warning` and trusted registries |
| `docker/SECURITY.md` | Base image choices, pinned digests, three operator commands, scan cadence, tagging policy, Trivy waivers |
| `docker/SIZE.md` | Before/after image size comparison (single-stage ~700 MB → three-stage 221 MB, ~68% reduction) |
| `.github/workflows/docker.yml` | CI gate: hadolint → build → Trivy (HIGH/CRITICAL) → 60s smoke test |

## Files modified
| File | Change |
|------|--------|
| `build.gradle` | Bumped Spring Boot 3.4.3 → 3.4.6 to resolve CRITICAL CVE-2025-41232 in Spring Security |
| `SecurityConfig.java` | Added `/actuator/health/**` to public permit list so the smoke test health probe isn't blocked by JWT auth |

## Key decisions
- **HEALTHCHECK path B**: used `eclipse-temurin:21-jre-jammy` as the runtime base instead of distroless, enabling `curl`-based health checks without shipping a custom Go probe binary. Trade-off documented in `docker/SECURITY.md`.
- **Digest pinning**: all three base images pinned by `sha256` digest, not mutable tags.
- **Layer cache**: `COPY` order is least-to-most-changing (wrapper → gradle dir → build files → dependency pre-warm → src), so a code-only change doesn't re-download dependencies.
- **Spring Boot bump**: upgraded to 3.4.6 which resolved the Spring Security CRITICAL and several Tomcat HIGH findings. Remaining CVEs in Netty, Kafka, and Spring AI 1.1.2 are documented as dated waivers pending upstream fixes.

## Verification
```bash
# 0 hadolint warnings
hadolint Dockerfile --config .hadolint.yaml

# Image size under 250 MB (content size: 221 MB)
docker images uptimecrew/expense-api

# Health probe returns UP
curl http://localhost:8080/actuator/health/readiness
# {"status":"UP"}

# Non-root user
docker exec expense-api id
# uid=1000 gid=0(root) groups=0(root)

# Image pushed to GHCR
# ghcr.io/nishis0205/expense-api@sha256:f300fe8c58cda18ca13d68e4d1309b86f86654f6d6779efe4e7a90017c7d1aff
```

## CI note
`build-scan-smoke` failed on PR #27 due to `trivy-action@0.28.0` being unavailable on the GitHub Actions marketplace. Fixed in commit `79ab877` by updating to `v0.30.0`. The `hadolint` job passed on every run.

## AI-tool review note
Claude suggested using `FROM eclipse-temurin:21` (unpinned, full JDK) for the runtime stage for simplicity — rejected because it includes the full JDK (~340 MB extra) and is a mutable tag. Claude's suggestion to use path B (Temurin JRE with curl) for the HEALTHCHECK instead of a custom Go probe binary was accepted, with the trade-off documented in `SECURITY.md`.

## Week 5 Day 2 — Docker Compose Polyglot Stack

### What I built
Wired the `uptimecrew/expense-api:0.1.0` image into a full local-dev stack using Docker Compose v2.

### Files added
| File | Purpose |
|------|---------|
| `compose.yaml` | Four-service stack (expense-api, postgres:16, redis:7, kafka:3.7.1, mongo:7) with healthchecks, named volumes, bridge network, depends_on with service_healthy |
| `compose.override.yaml` | Local-dev tweaks: JDWP debug port 5005, dev profile live-reload service (expense-api-dev on port 8081) |
| `compose.profiles.yaml` | Profile-gated sidecars: expense-web (e2e), seed-fixtures (test), otelcol + jaeger (observability/e2e) |
| `Makefile` | Convenience targets: up, down, smoke, logs, ps |
| `scripts/smoke.sh` | Isolated smoke test using per-invocation project name to avoid parallel conflicts |
| `scripts/dev.md` | Live-reload loop documentation |
| `envs/expense.env` | Environment variables (gitignored) |

### Key decisions
- Used plain `SPRING_DATASOURCE_PASSWORD` env var instead of Docker secrets `_FILE` pattern — Spring Boot doesn't natively read `_FILE`-suffixed vars without extra config
- Kafka image corrected from `apache/kafka:3.7` (doesn't exist) to `apache/kafka:3.7.1`
- Added mongo:7 service because the app has MerchantReadModelRepository backed by MongoDB
- Overrode Dockerfile HEALTHCHECK to use `/actuator/health` instead of `/actuator/health/readiness` (readiness probe not exposed without `management.endpoint.health.probes.enabled=true`)
- `envs/` added to `.gitignore` so credentials never reach the repo

### Verification
```bash
# Config validates cleanly
docker compose config --quiet

# All 5 services healthy
docker compose ps

# dev profile lists expense-api-dev
docker compose --profile dev config --services

# test profile lists seed-fixtures
docker compose -f compose.yaml -f compose.profiles.yaml --profile test config --services
```

### Branch
`week05/day2/Syed-web-coder`

## Week 5 Day 3

Added a full Kubernetes deployment for the expense-tracking service — a local k3d cluster running the W5D1 hardened image as a Deployment + Service + ConfigMap + Secret + HPA + Ingress, with a rolling update and rollback proven through `kubectl rollout`, gated by a CI workflow.

- `manifests/00-namespace.yaml` — `expense-dev` Namespace with a ResourceQuota (4 CPU/8Gi requests, 8 CPU/16Gi limits, 20 pods) and a LimitRange supplying container defaults
- `manifests/10-expense-api.deployment.yaml` — 3 replicas, zero-downtime `RollingUpdate` (`maxUnavailable: 0`/`maxSurge: 1`), `runAsNonRoot` UID 65532, `envFrom` ConfigMap + Secret, three probes on distinct liveness/readiness paths, resources (requests + memory limit, no CPU limit per the requests-only CPU pattern)
- `manifests/20-expense-api.service.yaml` — ClusterIP with a named `http` targetPort
- `manifests/30-expense-api.configmap.yaml` / `40-expense-api.secret.yaml` — non-secret env in the ConfigMap; `stringData` (never hand-base64) in the Secret
- `manifests/50-expense-api.hpa.yaml` — `autoscaling/v2` HPA, CPU target 70%, min 2/max 5, fast-scale-up + 5-minute-cooldown scale-down behavior — verified scaling 2→5 replicas under a `hey` load test
- `manifests/60-expense-api.ingress.yaml` — `networking.k8s.io/v1` Ingress, `ingressClassName: nginx`
- `scripts/k8s-up.sh` / `scripts/k8s-smoke.sh` — one-shot cluster bring-up and a 3-check Ingress smoke test
- `.github/workflows/k8s-ci.yml` — kubeconform, image build, disposable k3d cluster, throwaway Postgres/Mongo (schema seeded from `db/V1`–`V4`), dry-run + apply + rollout-status + smoke, diagnostics artifact on failure

**Notable deviations from the lesson doc:**
- **k3d's actual default ingress controller is Traefik, not NGINX.** The cluster is created with `--k3s-arg "--disable=traefik@server:0"`, and the real NGINX ingress controller is installed separately; its Service needed patching from `NodePort` to `LoadBalancer` for k3d's built-in `servicelb` to route traffic to it.
- **`application.yml` has no k8s-aware profile for its Postgres/Mongo/Kafka hosts** — they're hardcoded to `localhost`, which only resolves correctly under Compose (host networking or published ports), not inside a k3d pod. Added a local-only, gitignored `manifests/95-postgres.local.yaml` (Postgres + Mongo) purely so the app can boot locally; CI stands up the same throwaway pair inline in the workflow and seeds the schema from the W2D1 migration files.
- `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false` added to the ConfigMap — the W3D3 `@KafkaListener` otherwise blocks the whole app from starting when no broker is reachable, which is out of scope for this lab.
- CI's Secret must be seeded **after** `kubectl apply -f manifests/`, not before — the committed placeholder Secret in `manifests/` would otherwise overwrite the real CI-seeded password on every apply, causing a silent Postgres auth failure that took several CI runs to trace back to `pod-logs.txt`.
- Smoke test accepts `200`, `401`, or `404` from `/api/v1/merchants/{id}` (not just `200`/`404`) since that route requires a JWT per W3D1's security setup.

**Note on local environment friction (Colima/k3d):** A mid-session Colima restart (triggered while chasing an unrelated stale-port issue) left the k3d nodes' 

# Week 5 Day 4 — MerchantLookupHandler (Serverless)

## 1. Overview

Day 4 re-ships the merchant read-side as a standalone AWS Lambda function, eliminating the Spring Boot container for this path entirely.

**Route:** `GET /merchants/{merchantId}`  
**Stack name:** `expense-lambda-dev` (sandbox: `expense-lambda-sandbox`)  
**Live endpoint (dev):** `https://k8y4yu5epb.execute-api.us-east-1.amazonaws.com/dev`

The function fetches a merchant record by ID from DynamoDB and returns it as JSON with the `x-correlation-id` header echoed back. It emits EMF metrics (`MerchantLookupSuccess`, `MerchantNotFound`) directly to stdout — no extra SDK dependency.

---

## 2. Architecture

### SAM resources (`template.yaml`)

| Resource | Type | Notes |
|---|---|---|
| `ExpenseHttpApi` | `AWS::Serverless::HttpApi` | Stage name parameterised (`dev`, `sandbox`, …) |
| `MerchantsTable` | `AWS::DynamoDB::Table` | `merchants-${StageName}`, PAY_PER_REQUEST, partition key `id` (String) |
| `MerchantLookupFunction` | `AWS::Serverless::Function` | Java 21, 1024 MB, X-Ray active, JSON log format, SnapStart on published versions, `live` alias auto-published |
| `MerchantLookupFunctionLogGroup` | `AWS::Logs::LogGroup` | `/aws/lambda/expense-merchant-lookup-${StageName}`, 30-day retention — must be declared explicitly or CloudFormation leaves it with no retention |
| `MerchantLookupFunctionP99Alarm` | `AWS::CloudWatch::Alarm` | p99 Duration > 1500 ms over 5 × 60 s periods, scoped to the `:live` alias resource dimension |

IAM is generated by the `DynamoDBReadPolicy` SAM policy template — read-only verbs (`GetItem`, `Query`, `Scan`, `BatchGetItem`, `DescribeTable`) scoped to the exact table ARN.

### Init-vs-handler pattern

All expensive one-time work lives at class level (static initialisation), executed **once** per execution environment and captured in the SnapStart snapshot:

```
static init (captured in snapshot)
  └─ DynamoDbClient.create()       ← SDK client + connection pool
  └─ ObjectMapper + JavaTimeModule ← configured once
  └─ TABLE env-var read            ← see SnapStart gotcha §7

handleRequest (called per invocation)
  └─ extract correlationId / merchantId
  └─ DYNAMO.getItem(...)
  └─ serialize → 200, or 404/400/500
  └─ emitEmf(...)                  ← stdout EMF JSON, zero latency
```

---

## 3. Local Development

### Prerequisites

| Tool | Minimum version | Install check |
|---|---|---|
| AWS CLI v2 | 2.x | `aws --version` |
| SAM CLI | 1.120+ | `sam --version` |
| Maven | 3.9+ | `mvn --version` |
| Docker Desktop | running | `docker info` |
| Java 21 | JDK 21 | `java -version` |

### Build and validate

```bash
# 1. Compile and run unit tests
mvn test

# 2. Package the fat JAR (needed before sam build — SkipBuild: True means SAM
#    copies the JAR rather than compiling, so the JAR must already exist)
mvn package -DskipTests

# 3. Validate template syntax + cfn-lint rules
sam validate --lint

# 4. Build the SAM deployment package (Docker pulls the Lambda build image)
sam build
```

### Local invoke

```bash
sam local invoke MerchantLookupFunction \
  --event events/get-merchant.json \
  --parameter-overrides LambdaArchitecture=x86_64 FunctionTimeout=60
```

**Why those overrides on Windows/x86:**
- `LambdaArchitecture=x86_64` — the template defaults to `arm64` (Graviton), but `sam local invoke` runs a Docker container matched to the *host* CPU. On a Windows x86-64 machine the arm64 image either crashes or runs under emulation and times out. Overriding to x86_64 picks the correct `public.ecr.aws/lambda/java:21` image.
- `FunctionTimeout=60` — gives the DynamoDB SDK client enough time to surface a `ConnectException` (no local DynamoDB is running) rather than the Lambda platform cutting the invocation at the 10 s default before the SDK error propagates.

The invocation will always **fail** locally (no DynamoDB endpoint), but you will see `START RequestId: …` in the output which proves the handler class loaded and SnapStart init ran cleanly.

---

## 4. Deploy

### First-time guided deploy

```bash
sam deploy --guided
# Accept prompted defaults; set StageName=dev, LambdaArchitecture=arm64
# SAM writes samconfig.toml (git-ignored)
```

### Subsequent deploys (scripted)

```bash
# Uses STACK env var (default: expense-lambda-sandbox) and --resolve-s3
STACK=expense-lambda-dev ./scripts/sam-deploy.sh

# Smoke test against the deployed stack
STACK=expense-lambda-dev ./scripts/sam-smoke.sh
```

`sam-deploy.sh` derives the SAM stage name from the stack name (`expense-lambda-<stage>`) and calls `sam deploy --resolve-s3 --no-confirm-changeset --no-fail-on-empty-changeset`.

`sam-smoke.sh` resolves `HttpApiUrl` and `FunctionName` from CloudFormation stack outputs, then:
1. `GET /merchants/mer_synth_001` — accepts 200 or 404 (item may or may not exist)
2. `GET /merchants/` — asserts 404 (no route)
3. Polls `/aws/lambda/$FUNCTION_NAME` CloudWatch logs for a recent `REPORT` line (up to 4 × 5 s retries)

---

## 5. Measured Results (2026-07-08, `expense-lambda-dev`, us-east-1)

Data from `task2-3-verification.md` — 5 invocations against a freshly deployed SnapStart function:

| Invocation | Handler duration | Billed | Notes |
|---|---|---|---|
| 1 (resume) | 3 689.6 ms | 3 843 ms | SnapStart restore: **564.9 ms**, billed restore: 153 ms |
| 2 | **17.6 ms** | 18 ms | Warm |
| 3 | **8.8 ms** | 9 ms | Warm |
| 4 | **7.9 ms** | 8 ms | Warm |
| 5 | **7.9 ms** | 8 ms | Warm |

**Key numbers:**
- SnapStart restore latency: ~556–565 ms (vs. ~3–5 s true cold start for a 1024 MB Java 21 function)
- Warm p99: well under the 1500 ms alarm threshold
- Billed restore duration: ~119–153 ms (only the JVM re-init after snapshot load is billed, not the full restore)
- Max memory: 184 MB / 1024 MB provisioned

EMF metric `MerchantLookupSuccess` confirmed in namespace `ExpenseDev` after 5 successful `HTTP 200` responses.

---

## 6. CI/CD (`serverless.yml`)

The workflow triggers on changes to `template.yaml`, `src/**`, `pom.xml`, `events/**`, `scripts/**`, or the workflow file itself.

### PR gate (`validate` job)

```
sam validate --lint          → template syntax + cfn-lint
mvn test                     → unit tests
mvn package -DskipTests      → produces target/*.jar for sam build
sam build --use-container    → Lambda build image, reproducible
sam local invoke             → --parameter-overrides LambdaArchitecture=x86_64 FunctionTimeout=60
                               exit code ignored; grep output for 'START RequestId'
```

### Merge to main (`deploy-sandbox` job)

Uses OIDC — **no long-lived AWS keys anywhere in the repo or secrets**:

```yaml
- uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}   # GitHub repo variable, not a secret
    aws-region: ${{ vars.AWS_REGION }}
```

The job runs `scripts/sam-deploy.sh` then `scripts/sam-smoke.sh` with `STACK=expense-lambda-sandbox`. On failure, a `sam-diagnostics` artifact is uploaded containing `describe-stack-events` output and a 30-minute `logs tail` of the Lambda log group.

Required GitHub repo variables: `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`.

---

## 7. Lessons Learned

### SnapStart stale-snapshot gotcha — `MERCHANTS_TABLE` frozen at snapshot time

Static fields are initialised once and captured in the snapshot. `System.getenv("MERCHANTS_TABLE")` runs at class-load time, so the table name is baked into the snapshot. If the environment variable changes between deployments (e.g., renaming the table), you must publish a **new version** and flip the alias — otherwise the snapshot continues pointing at the old table name. The fix is already in place: `AutoPublishAlias: live` publishes a fresh version (and therefore a fresh snapshot) on every `sam deploy`, so the alias flip atomically picks up the new env-var value.

### `Metadata: SkipBuild: True` required for pre-built JARs alongside a root `build.gradle`

SAM's default behaviour is to delegate JAR compilation to its own build container. With a multi-module repo that has a root `build.gradle`, SAM misidentifies the build system and tries to run Gradle instead of reading the pre-built JAR at `CodeUri`. Adding `Metadata: SkipBuild: True` tells SAM to copy `target/expense-merchant-lookup-1.0.0.jar` verbatim, which is what we want since Maven already produced it.

### LogGroup name mismatch with `AutoPublishAlias`

With SnapStart + `AutoPublishAlias: live`, Lambda publishes numbered versions (`expense-merchant-lookup-dev:1`, `:2`, …). If you query the log group by function name + qualifier (e.g., `aws logs tail … --filter-pattern REPORT`) and use the alias ARN instead of the bare function name, you may get no results — logs always flow to `/aws/lambda/<function-name>` regardless of alias or version. Declaring `MerchantLookupFunctionLogGroup` explicitly in the template (keyed on the bare `${StageName}` suffix) guarantees the group exists with correct retention before the first invocation and gives `sam-smoke.sh` a stable name to query.

---

## 8. Teardown

```bash
# Delete the dev stack (retains the S3 artifact bucket — delete manually if desired)
sam delete --stack-name expense-lambda-dev --region us-east-1 --no-prompts

# Delete the sandbox stack
sam delete --stack-name expense-lambda-sandbox --region us-east-1 --no-prompts
```

`sam delete` removes the CloudFormation stack, Lambda function, API Gateway, DynamoDB table, log group, and alarm. The S3 bucket created by `--resolve-s3` is **not** deleted automatically; remove it via the console or `aws s3 rb s3://<bucket> --force` if needed.

# Week 5 Day 5 — Observability Layer (PLG-T Stack)

## Overview

Built a full observability layer on the k3d `expense-api` deployment covering all three pillars:

- **Metrics** — Micrometer → Prometheus (kube-prometheus-stack) with a pre-built RED dashboard in Grafana
- **Logs** — structured JSON (Logback) → Grafana Alloy → Loki 6.6.4
- **Traces** — OTel Java agent → OTel Collector → Tempo 1.10.1, with `@WithSpan` on `MerchantLookupService.findById`
- **Alerting** — multi-window SLO burn-rate alert generated by Sloth, deployed as a `PrometheusRule` CR

## Architecture

| Component | Role |
|---|---|
| kube-prometheus-stack | Prometheus Operator, Grafana, Alertmanager |
| Loki 6.6.4 (SingleBinary, filesystem) | Log aggregation |
| Grafana Alloy 0.5.0 | Log scraping from k8s pods → Loki |
| Tempo 1.10.1 | Distributed trace storage |
| OTel Collector 0.97.0 | OTLP receiver → Tempo exporter |
| OTel Java agent | Auto-instrumentation + `@WithSpan` manual spans |

## Files Added

```
manifests/observability/          Kubernetes manifests (ServiceMonitor, OTel agent patch,
│                                  Loki, Alloy, Tempo, OTel Collector, AlertmanagerConfig)
manifests/observability/LABELS.md Loki label discipline doc (max 4 labels policy)
.grafana/dashboards/              Grafana dashboard JSON (RED dashboard)
slo/                              Sloth SLO spec + generated PrometheusRule
scripts/observability-apply.sh    One-shot apply of the full observability stack
scripts/observability-smoke.sh    Smoke-test script (metrics, logs, traces, alerts)
.github/workflows/observability.yml CI drift gate — diffs Sloth output against committed rule
src/main/resources/logback-spring.xml Structured JSON log config (prod profile)
```

## Key Decisions

- **Pre-built meters** (`Counter`/`Timer` as `private final` fields) — not per-call builder pattern, avoids registry lookup on every request.
- **`parentbased_traceidratio=0.1` sampling** (not `traceidratio`) so that cross-service traces stay joinable: if the caller sampled, this service follows.
- **Max 4 Loki labels** (`app`, `env`, `level`, `pod`) — high-cardinality fields (`correlationId`, `merchantId`, `userId`) live in the log body only to avoid Loki index explosion.
- **`allowUiUpdates: false`** on Grafana dashboard provisioning so dashboards survive pod restarts without being reverted.
- **Sloth-generated `PrometheusRule`** — the SLO burn-rate rule is never hand-edited; the CI gate catches drift between the spec and the committed rule.

## Verification

- `GET /actuator/prometheus` returns the `http_server_requests_seconds` histogram with `app="expense-api"` label present.
- App running `1/1` in k3d with **0 restarts** after the final fix.
- Grafana RED dashboard shows request rate, error rate, and latency percentiles from live scrape data.
- Traces visible in Tempo with `@WithSpan` span appearing as a child of the HTTP server span.

## Gotchas

- **`MerchantLookupService` must not be `final`** — Spring CGLIB proxying for `@Transactional` requires a subclassable type; `final` causes context startup failure.
- **`SecurityConfig` must permit `/actuator/**` without JWT** — Prometheus scrapes unauthenticated; blocking the endpoint returns 401 and breaks the `ServiceMonitor`.
- **Postgres schema `expense` must exist before app starts** — `CREATE SCHEMA IF NOT EXISTS expense;` must run as part of DB init or the JPA DDL step fails.
- **Loki 6.6.4 SingleBinary mode quirks** — requires `--set loki.useTestSchema=true` and explicit replica counts; omitting either causes the Helm chart to reject the values or the pod to crash-loop.

## Week 6 Day 3
AWS substrate provisioned via CloudFormation (4 stacks: VPC, RDS, S3, IAM bootstrap). See [expense-config/cfn/](https://github.com/NishiS0205/expense-config/tree/main/cfn) and [INFRA.md](expense-api/INFRA.md).

## Week 7 Day 1
Python AI sidecar [`expense-ai/`](expense-ai/) bootstrapped with uv (Python 3.12, `src/` layout, hatchling build); Pydantic v2 boundary models (`Merchant` with ISO-2 country validator, `DeductionClassifyRequest`, `DeductionClassifyResult` with model-validator), frozen slotted dataclasses for value types, pydantic-settings config with `SecretStr` API key, and typed embedding utilities (`cosine_similarity`, `top_k`); 44 tests at 100 % coverage running under `mypy --strict` and ruff (E, F, I, UP, B); embeddings notebook (`all-mpnet-base-v2`: PCA scatter, cosine heatmap, top-3 retrieval) executable headless via `MPLBACKEND=Agg`; paths-filtered `python-ci` workflow with uv caching and HuggingFace model cache. See [`expense-ai/README.md`](expense-ai/README.md) for setup and full details.

