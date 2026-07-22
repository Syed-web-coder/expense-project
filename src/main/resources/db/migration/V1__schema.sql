CREATE SCHEMA IF NOT EXISTS expense;
SET search_path TO expense, public;

CREATE TABLE expense.merchant (
    id          TEXT PRIMARY KEY,                           -- TEXT ids: stable across environments, matches Java domain model
    name        TEXT NOT NULL,                              -- every merchant must have a name
    mcc_code    TEXT,                                       -- nullable: not every merchant has an MCC code
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()          -- TIMESTAMPTZ: always store timezone-aware timestamps
);

CREATE TABLE expense.rule (
    id                TEXT PRIMARY KEY,
    merchant_id       TEXT NOT NULL
                      REFERENCES expense.merchant(id) ON DELETE RESTRICT,  -- RESTRICT: orphaned rules are a data error, fail loudly
    category          TEXT NOT NULL
                      CHECK (category IN ('FOOD','TRANSPORT','UTILITIES','ENTERTAINMENT','OTHER')),  -- enum-as-CHECK: altering a native ENUM requires a migration
    amount_threshold  NUMERIC(12,2) CHECK (amount_threshold >= 0),          -- NUMERIC not FLOAT: exact decimal arithmetic for money
    mcc_code          TEXT,
    name              TEXT NOT NULL UNIQUE                  -- UNIQUE: two rules cannot share the same name
);

CREATE TABLE expense.transaction (
    id            TEXT PRIMARY KEY,
    merchant_id   TEXT NOT NULL
                  REFERENCES expense.merchant(id) ON DELETE CASCADE,  -- CASCADE: a transaction without a merchant has no meaning
    merchant_name TEXT NOT NULL,                            -- denormalised for read performance; must never be blank
    amount        NUMERIC(12,2) NOT NULL
                  CHECK (amount >= 0),                      -- amount can never be negative; refunds are separate transactions
    occurred_at   TIMESTAMPTZ NOT NULL,                     -- TIMESTAMPTZ: preserves the timezone the transaction happened in
    kind          TEXT NOT NULL
                  CHECK (kind IN ('DEBIT','CREDIT'))        -- enum-as-CHECK: controls the two legal transaction directions
); 
