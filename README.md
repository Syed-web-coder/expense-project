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
Added Spring Boot + Gradle plugins (v3.4.5), `Application.java` entry point with `@SpringBootApplication`, promoted `ExpenseClassificationService` to a Spring `@Service`, annotated strategy implementations with `@Component` (`@Primary` on `MerchantNameClassifier`), created `application.yml` with local/test profiles, Actuator health+info endpoints, and logging config, and added `ApplicationContextLoadIT` with `@SpringBootTest` verifying the context loads and the service bean is wired.
