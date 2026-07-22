-- 1. Sanity-check counts per table.
SELECT 'merchant' AS t, COUNT(*) FROM expense.merchant
UNION ALL
SELECT 'transaction', COUNT(*) FROM expense.transaction
UNION ALL
SELECT 'rule', COUNT(*) FROM expense.rule;

-- 2. JOIN: every transaction and its parent merchant.
SELECT t.id, m.name, t.amount, t.kind, t.occurred_at
FROM   expense.merchant m
JOIN   expense.transaction t ON t.merchant_id = m.id
ORDER  BY m.id, t.id;

-- 3. Aggregate: total amount per merchant.
SELECT m.id, m.name, SUM(t.amount) AS total
FROM   expense.merchant m
JOIN   expense.transaction t ON t.merchant_id = m.id
GROUP  BY m.id, m.name
ORDER  BY total DESC;

