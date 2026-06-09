-- Pattern reference for Task 2. Adapted to expense-project domain.
-- TEXT ids, NUMERIC money, TIMESTAMPTZ timestamps.

CREATE SCHEMA IF NOT EXISTS expense;
SET search_path TO expense, public;

CREATE TABLE expense.merchant (
    id          TEXT PRIMARY KEY,                          -- (1) TEXT ids, not SERIAL
    name        TEXT NOT NULL,
    mcc_code    TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()         -- (2) TIMESTAMPTZ, not TIMESTAMP
);

CREATE TABLE expense.rule (
    id          TEXT PRIMARY KEY,
    merchant_id TEXT NOT NULL
                REFERENCES expense.merchant(id) ON DELETE RESTRICT,  -- (3) FK
    category    TEXT NOT NULL
                CHECK (category IN ('FOOD','TRANSPORT','UTILITIES','ENTERTAINMENT','OTHER')),  -- (4) enum-as-CHECK
    amount_threshold  NUMERIC(12,2) CHECK (amount_threshold >= 0),   -- (5) NUMERIC not FLOAT
    mcc_code    TEXT,
    name        TEXT NOT NULL UNIQUE                       -- (6) UNIQUE on natural key
);

CREATE TABLE expense.transaction (
    id           TEXT PRIMARY KEY,
    merchant_id  TEXT NOT NULL
                 REFERENCES expense.merchant(id) ON DELETE CASCADE,  -- (7) FK + cascade
    merchant_name TEXT NOT NULL,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount >= 0),         -- (8) NUMERIC not FLOAT
    occurred_at  TIMESTAMPTZ NOT NULL,                               -- (9) TIMESTAMPTZ
    kind         TEXT NOT NULL
                 CHECK (kind IN ('DEBIT','CREDIT'))                  -- (10) enum-as-CHECK
);
