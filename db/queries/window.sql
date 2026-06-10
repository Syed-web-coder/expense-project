SET search_path TO expense, public;

SELECT
    p.id                                                          AS merchant_id,
    p.mcc_code,
    c.amount,
    RANK()  OVER (PARTITION BY p.id ORDER BY c.amount DESC)      AS amount_rank,
    SUM(c.amount) OVER (PARTITION BY p.id)                       AS parent_total
FROM expense.merchant p
JOIN expense.transaction c ON c.merchant_id = p.id
ORDER BY p.id, amount_rank;
