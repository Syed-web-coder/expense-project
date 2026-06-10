SET search_path TO expense, public;

-- INNER JOIN: transactions with their merchant info
SELECT
    p.id,
    p.mcc_code,
    c.amount,
    c.merchant_name
FROM expense.merchant p
INNER JOIN expense.transaction c ON c.merchant_id = p.id
ORDER BY p.id, c.amount DESC;

-- LEFT JOIN: count transactions per merchant
SELECT
    p.id,
    COUNT(c.id) AS child_count
FROM expense.merchant p
LEFT JOIN expense.transaction c ON c.merchant_id = p.id
GROUP BY p.id
ORDER BY child_count DESC;
