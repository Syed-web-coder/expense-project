ALTER TABLE doc_chunks
    ADD COLUMN IF NOT EXISTS chunk_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS content_hash    text,
    ADD COLUMN IF NOT EXISTS chunk_tsv       tsvector
        GENERATED ALWAYS AS (to_tsvector('english', chunk_text)) STORED;

CREATE INDEX CONCURRENTLY IF NOT EXISTS doc_chunks_metadata_gin
    ON doc_chunks USING gin (chunk_metadata jsonb_path_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS doc_chunks_tenant_a_hnsw
    ON doc_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 24, ef_construction = 128)
    WHERE tenant_id = 'tenant-a';

CREATE INDEX CONCURRENTLY IF NOT EXISTS doc_chunks_tenant_b_hnsw
    ON doc_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 24, ef_construction = 128)
    WHERE tenant_id = 'tenant-b';

CREATE INDEX CONCURRENTLY IF NOT EXISTS doc_chunks_tenant_c_hnsw
    ON doc_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 24, ef_construction = 128)
    WHERE tenant_id = 'tenant-c';

CREATE INDEX CONCURRENTLY IF NOT EXISTS doc_chunks_tsv_gin
    ON doc_chunks USING gin (chunk_tsv);
