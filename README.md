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
