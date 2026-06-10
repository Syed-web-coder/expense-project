SET search_path TO expense, public;

SELECT
    p.id,
    p.mcc_code,
    COUNT(c.id)     AS child_count,
    AVG(c.amount)   AS avg_amount
FROM expense.merchant p
JOIN expense.transaction c ON c.merchant_id = p.id
GROUP BY p.id, p.mcc_code
HAVING COUNT(c.id) >= 3
ORDER BY avg_amount DESC;
