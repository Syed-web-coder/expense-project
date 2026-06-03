# CLAUDE.md

Coding conventions for this project. Follow these exactly — they exist to prevent subtle bugs and keep the codebase consistent.

## Java Version

Target **JDK 17+**. Use records, sealed classes, text blocks, and pattern matching where they clarify intent.

## Package Root

All production code lives under `com.uptimecrew.taxdocs`. Mirror this structure under `src/main/java/` and `src/test/java/`.

## Money Fields — `BigDecimal` Only

Use `BigDecimal` with scale 2 and `RoundingMode.HALF_UP` for every monetary value. Never use `double` or `float` — floating-point arithmetic silently accumulates rounding errors that compound across tax calculations.

```java
BigDecimal amount = new BigDecimal("19.99").setScale(2, RoundingMode.HALF_UP);
```

Always construct from `String`, not from a `double` literal (`new BigDecimal(0.1)` does not equal `0.1`).

## Identifiers — `String` (UUID or Prefixed Synthetic)

Use `String` for all entity IDs. Generate with `UUID.randomUUID().toString()` or a prefixed synthetic (e.g. `"doc_" + UUID.randomUUID()`). Never use `int` or `long` — numeric IDs leak record counts, are hard to shard, and create merge conflicts in distributed writes.

## Dates and Timestamps

| Use case | Type |
|---|---|
| Calendar date (no time) | `LocalDate` |
| Point in time | `Instant` |

Never use `java.util.Date`, `java.sql.Date`, or `Calendar` — they are mutable, timezone-ambiguous, and effectively deprecated. Convert at persistence boundaries only (e.g. `Timestamp.from(instant)`).

## Class and Field Defaults

- Fields are `private final` unless mutation is explicitly required.
- Classes are `final` unless designed for extension.
- Do **not** use Lombok `@Data` — it generates mutable `equals`/`hashCode` based on all fields, which breaks collections when objects change, and hides what the class actually exposes.

Write explicit constructors, getters, and `equals`/`hashCode`, or use Java records for pure data holders.

```java
public record ExpenseEntry(String id, LocalDate date, BigDecimal amount, String description) {}
```

## Tests — JUnit 5

Use JUnit 5 (`org.junit.jupiter.api`). Fixtures go in `@BeforeEach`. Assertions use `assertEquals`, `assertTrue`, and `assertThrows` — no JUnit 4 imports.

```java
@BeforeEach
void setUp() { ... }

@Test
void rejectsNegativeAmount() {
    assertThrows(IllegalArgumentException.class, () -> new ExpenseEntry(...));
}
```

Test class names end in `Test`. One logical behavior per test method.
