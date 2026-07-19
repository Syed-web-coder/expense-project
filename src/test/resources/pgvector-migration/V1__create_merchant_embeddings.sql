-- src/main/resources/db/migration/V1__create_merchant_embeddings.sql
--
-- pgvector extension + merchant_embeddings table for the
-- merchant-similarity feature (categorize-expense). This is the
-- first-ever Flyway migration in this project -- the existing
-- db/V1-V4 scripts at the repo root are a separate, hand-applied
-- schema (via db/init.sh) unrelated to this Spring Boot app's
-- runtime; ddl-auto is set to "validate", not "update"/"create",
-- so this table is genuinely new and standalone.
--
-- Index choice and operator are pinned; mismatching index op-class
-- to query operator forces a seq scan and ruins latency on any
-- table over a few thousand rows.

-- Enable once per database. Idempotent.
CREATE EXTENSION IF NOT EXISTS vector;

-- The embeddings table. vector(1024) matches the
-- amazon.titan-embed-text-v2:0 output dimensionality; switching models
-- is a data migration, not a property change.
CREATE TABLE IF NOT EXISTS merchant_embeddings (
    id              UUID         PRIMARY KEY,
    tenant_id       TEXT         NOT NULL,
    embedding       vector(1024) NOT NULL,
    inserted_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- HNSW chosen because:
--   * inserts are interleaved with queries (continuous ingest)
--   * recall-95 latency must stay under ~50ms p99 on db.r6g.xlarge
--   * memory footprint is acceptable at projected row counts
-- IVFFlat would require a representative pre-load we don't have yet.
--
-- vector_cosine_ops matches the <=> operator the JDBC query uses;
-- mixing op-class with operator forces a sequential scan.
CREATE INDEX IF NOT EXISTS merchant_embeddings_hnsw
    ON merchant_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- B-tree on tenant_id so the planner can intersect the HNSW
-- order-by with the WHERE tenant_id = $1 filter for tenant
-- isolation without a full table scan.
CREATE INDEX IF NOT EXISTS merchant_embeddings_tenant
    ON merchant_embeddings (tenant_id);
