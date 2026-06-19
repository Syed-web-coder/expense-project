SET search_path TO expense, public;

CREATE TABLE expense.outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  TEXT NOT NULL,                              -- "Transaction"
    aggregate_id    TEXT NOT NULL,                              -- the transaction's id; also used as the Kafka key
    event_type      TEXT NOT NULL,                              -- "TransactionPlaced"
    payload         JSONB NOT NULL,                              -- the serialised event body
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),         -- insertion order = publish order per aggregate
    published_at    TIMESTAMPTZ                                 -- NULL until the poller ships it to Kafka
);

-- Partial index: the poller only ever queries unpublished rows.
-- Keeps that query fast even as published rows accumulate.
CREATE INDEX idx_outbox_unpublished
    ON expense.outbox_event (created_at)
    WHERE published_at IS NULL;
