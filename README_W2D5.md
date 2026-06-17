# Week 2 Day 5 — Polyglot Persistence

## What We Built

### `MerchantReadModel`
A MongoDB document class (`@Document(collection = "merchants")`) that stores a denormalised view of a merchant alongside an embedded list of transaction lines. It implements `Serializable` so Spring's Redis cache serialiser can handle it. Fields: `id`, `mccCode`, `capturedAt`, `transactions` (list of `EmbeddedLine`).

### `MerchantReadModelRepository`
A Spring Data `MongoRepository<MerchantReadModel, String>` with one derived query: `findByMccCode(String)`. No boilerplate needed beyond the interface declaration.

### `@Cacheable findById` in `ExpenseClassificationService`
`findById(String id)` is annotated with `@Cacheable(value = "expense.byId")`. On the first call Spring checks Redis; on a miss it reads from MongoDB (falling back to Postgres if the document isn't there yet) and writes the result into the cache. Subsequent calls within the 60-second TTL window skip both databases entirely.

### `MerchantPolyglotIT`
An integration test that spins up all three stores via Testcontainers, applies the Flyway schema manually with `@BeforeAll`, seeds both Postgres and MongoDB, then verifies:
1. A read through `findById` returns the MongoDB document.
2. A second read finds a live entry in the Redis `CacheManager`, confirming the cache was populated.

---

## Three-Store Architecture

```
Write path
  classify(transaction)
      │
      ├─► Postgres  (expense.merchant table)   ← source of truth, ACID
      └─► MongoDB   (merchants collection)     ← denormalised read model, embedded lines

Read path
  findById(id)
      │
      ├─► Redis  (expense.byId cache, 60 s TTL)  ← hot cache, in-memory
      │     hit → return immediately
      └─► MongoDB  (miss path)
            └─► Postgres  (fallback if not in Mongo yet)
```

| Store    | Role                    | Technology                         |
|----------|-------------------------|------------------------------------|
| Postgres | Source of truth         | Spring Data JPA, `expense` schema  |
| MongoDB  | Denormalised read model | Spring Data MongoDB                |
| Redis    | Hot cache (60 s TTL)    | Spring Cache (`@Cacheable`)        |

---

## Running Locally

### 1. Start the three containers

```bash
# Postgres
docker run -d --name expense-pg \
  -e POSTGRES_DB=expense \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine

# MongoDB
docker run -d --name expense-mongo \
  -p 27017:27017 \
  mongo:7

# Redis
docker run -d --name expense-redis \
  -p 6379:6379 \
  redis:7-alpine
```

### 2. Apply the schema

```bash
psql -h localhost -U postgres -d expense -f db/V1__schema.sql
```

### 3. Boot the application

```bash
./gradlew bootRun
```

The app connects to all three stores using the defaults in `src/main/resources/application.yml`:
- Postgres: `localhost:5432/expense`
- MongoDB: `localhost:27017/expense`
- Redis: `localhost:6379`

---

## Running the Integration Test

Testcontainers manages the containers automatically — no manual `docker run` needed.

```bash
./gradlew test --tests "*PolyglotIT"
```

The test class `MerchantPolyglotIT` starts fresh containers for every test run, applies `db/V1__schema.sql` inside `@BeforeAll`, seeds both Postgres and MongoDB, then asserts the write path and cache path both work.

---

## Fixes Applied Today

| Problem | Fix |
|---|---|
| Duplicate `@SpringBootApplication` | Removed the stray annotation from `expense-project/` subfolder; only `Application.java` in the root source tree retains it. |
| Missing schema at test startup | Added a `@BeforeAll` block in `MerchantPolyglotIT` that applies `db/V1__schema.sql` via plain JDBC before any repository call. |
| Datasource config mismatch | Confirmed `application-test.yml` sets `ddl-auto: none` (Testcontainers owns the container; we own the schema file) and `cache.type: simple` so the Redis container is not required for unit tests. |
