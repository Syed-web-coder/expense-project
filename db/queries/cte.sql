SET search_path TO expense, public;

WITH totals AS (
    SELECT
        merchant_id AS p_id,
        SUM(amount)  AS total
    FROM expense.transaction
    GROUP BY merchant_id
)
SELECT
    p.id,
    p.mcc_code,
    t.total
FROM totals t
JOIN expense.merchant p ON p.id = t.p_id
WHERE t.total > 100.00
ORDER BY t.total DESC;
