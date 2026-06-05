# CLAUDE.md — Expense Tracking Project Conventions

## Java Baseline
- JDK 17 or later.

## Money
- Always use BigDecimal with scale 2 and RoundingMode.HALF_UP.
- Never use double or float for currency values.

## Identifiers
- Always use String (UUID v4 or prefixed synthetic e.g. txn-2026-0001).
- Never use int or long for IDs.

## Dates and Times
- Calendar dates: LocalDate
- Timestamps: Instant
- Never use java.util.Date, java.sql.Date, or Calendar.

## Classes and Fields
- All fields must be private final.
- All classes must be final.
- No Lombok @Data or setters of any kind.

## Testing
- JUnit 5 only.
- Use: @Test, @BeforeEach, assertEquals, assertTrue, assertThrows.
- Test method names: methodName_condition_expectation.

## Package Root
- All production code under com.uptimecrew.expense
- Models under com.uptimecrew.expense.model
- Services under com.uptimecrew.expense.service
