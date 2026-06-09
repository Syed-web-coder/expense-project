-- Seed runs as ONE transaction. A failure on any INSERT rolls back every previous one.

BEGIN;

INSERT INTO expense.merchant (id, name, mcc_code) VALUES
    ('merch-2026-0001', 'tenant-a-grocery', '5411'),
    ('merch-2026-0002', 'tenant-a-transport', '4111'),
    ('merch-2026-0003', 'doc-2026-0001-util', '4911'),
    ('merch-2026-0004', 'expense.example.internal', '5812'),
    ('merch-2026-0005', 'tenant-a-streaming', '7922');

INSERT INTO expense.rule (id, name, priority, merchant_id, category, amount_threshold, mcc_code) VALUES
    ('rule-001', 'grocery-rule', 100, 'merch-2026-0001', 'FOOD', 0.00, '5411'),
    ('rule-002', 'transport-rule', 90, 'merch-2026-0002', 'TRANSPORT', 0.00, '4111'),
    ('rule-003', 'utilities-rule', 80, 'merch-2026-0003', 'UTILITIES', 0.00, '4911'),
    ('rule-004', 'entertainment-rule', 70, 'merch-2026-0004', 'ENTERTAINMENT', 0.00, '5812'),
    ('rule-005', 'streaming-rule', 60, 'merch-2026-0005', 'ENTERTAINMENT', 0.00, '7922');

INSERT INTO expense.transaction (id, merchant_id, merchant_name, amount, occurred_at, kind) VALUES
    ('txn-2026-0001', 'merch-2026-0001', 'tenant-a-grocery', 45.99, '2026-01-01T10:00:00Z', 'DEBIT'),
    ('txn-2026-0002', 'merch-2026-0002', 'tenant-a-transport', 12.50, '2026-01-02T09:00:00Z', 'DEBIT'),
    ('txn-2026-0003', 'merch-2026-0003', 'doc-2026-0001-util', 120.00, '2026-01-03T08:00:00Z', 'DEBIT'),
    ('txn-2026-0004', 'merch-2026-0004', 'expense.example.internal', 9.99, '2026-01-04T07:00:00Z', 'DEBIT'),
    ('txn-2026-0005', 'merch-2026-0005', 'tenant-a-streaming', 15.99, '2026-01-05T06:00:00Z', 'DEBIT');

COMMIT;

-- Intentional failure test (outside the transaction) — proves CHECK constraint is real.
-- Expected error: new row for relation "transaction" violates check constraint "transaction_amount_check"
BEGIN;
INSERT INTO expense.transaction (id, merchant_id, merchant_name, amount, occurred_at, kind)
    VALUES ('txn-bad-001', 'merch-2026-0001', 'tenant-a-grocery', -99.99, NOW(), 'DEBIT');
ROLLBACK; 
