SET search_path TO expense, public;

-- Supports idempotent transaction recording: the same idempotency_key
-- submitted twice (e.g. an MCP agent retrying a slow/ambiguous tool call)
-- must replay the original result, not create a second transaction.
-- NULL is allowed for the column itself (so existing rows aren't broken),
-- but the partial unique index only enforces uniqueness where a key was
-- actually supplied.
ALTER TABLE expense.transaction
    ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX idx_transaction_idempotency_key
    ON expense.transaction (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
