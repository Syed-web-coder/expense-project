## Query catalogue

**joins.sql** answers "which transactions belong to which merchant, and how many transactions does each merchant have?" It touches `expense.merchant` and `expense.transaction`. The file demonstrates two JOIN idioms back-to-back: an INNER JOIN to retrieve each transaction alongside its merchant's MCC code and amount, and a LEFT JOIN with GROUP BY to count transactions per merchant — preserving merchants that have no transactions yet.

**cte.sql** answers "which merchants have a total transaction spend above $100, and how much did each spend in total?" It touches `expense.transaction` and `expense.merchant`. A CTE named `totals` aggregates `SUM(amount)` per `merchant_id` first; the outer SELECT then joins the result back to `merchant` for display and filters with `WHERE t.total > 100.00`, keeping the aggregation logic separate from the filtering and join steps.

**window.sql** answers "within each merchant, how does each individual transaction rank by amount, and what is the merchant's total spend?" It touches `expense.merchant` and `expense.transaction`. The query uses two window functions in a single pass: `RANK() OVER (PARTITION BY p.id ORDER BY c.amount DESC)` to rank each transaction within its merchant, and `SUM(c.amount) OVER (PARTITION BY p.id)` to compute the merchant-level total alongside every row without collapsing them.

**group_by_having.sql** answers "which merchants have at least three transactions, and what is their average transaction amount?" It touches `expense.merchant` and `expense.transaction`. The SQL idiom is `GROUP BY … HAVING COUNT(c.id) >= 3`, which filters aggregated groups after grouping rather than before — the only way to express a condition on an aggregate without a subquery or CTE.

## Running locally

```sh
# 1. Start a local Postgres instance (adjust host/port/credentials as needed)
# 2. Apply schema and seed data
psql -h localhost -U postgres -d postgres -f db/V1__schema.sql
psql -h localhost -U postgres -d postgres -f db/V2__seed.sql

# 3. Run any query file
psql -h localhost -U postgres -d postgres -f db/queries/joins.sql
psql -h localhost -U postgres -d postgres -f db/queries/cte.sql
psql -h localhost -U postgres -d postgres -f db/queries/window.sql
psql -h localhost -U postgres -d postgres -f db/queries/group_by_having.sql
```

## Running in tests

```sh
./gradlew test --tests "*QueryIT"
```

Testcontainers automatically starts and stops a PostgreSQL Docker container for the duration of the test run — no local database setup is required.

## Trade-offs

**CTE over a subquery in cte.sql.** The CTE makes the aggregation step (`totals`) a named, reusable unit that is defined once at the top of the query. A correlated subquery would force the database to re-execute the aggregation for each row in the outer SELECT, which is both slower on large datasets and harder to read. The CTE also makes it straightforward to add a second consumer of `totals` (e.g., a second join or an additional filter clause) without duplicating the aggregation logic — a structural advantage that a subquery cannot offer without copy-pasting.

**Window function vs GROUP BY in window.sql.** A `GROUP BY` collapses multiple rows into one output row per group; it cannot return individual transaction rows alongside a group-level aggregate in the same result set. The window functions in `window.sql` compute `RANK()` and `SUM()` across a partition while keeping every transaction row intact, which is the precise requirement of the query. Replacing them with `GROUP BY` would require a self-join or subquery to reattach the per-row rank back to the detail rows, adding complexity and a second pass over the data.
