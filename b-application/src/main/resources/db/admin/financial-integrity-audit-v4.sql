-- V4 financial integrity audit (read-only)
-- Run against the target database after backup. Every section should return zero rows unless noted.

-- 1) Account balance cache must equal immutable entry facts.
SELECT a.account_id,
       a.account_name,
       a.currency,
       a.total_amount AS cached_balance,
       COALESCE(SUM(e.amount), 0) AS rebuilt_balance,
       a.total_amount - COALESCE(SUM(e.amount), 0) AS difference
FROM bills.b_accounts a
LEFT JOIN bills.b_transaction_entries e ON e.account_id = a.account_id
GROUP BY a.account_id, a.account_name, a.currency, a.total_amount
HAVING a.total_amount <> COALESCE(SUM(e.amount), 0)
ORDER BY ABS(a.total_amount - COALESCE(SUM(e.amount), 0)) DESC;

-- 2) Tracked FX quantity must never exceed the actual asset-account balance.
-- A positive untracked balance may exist for legacy data, but Position > Account means stale cost state.
SELECT a.account_id,
       a.account_name,
       a.currency,
       a.total_amount AS account_balance,
       p.quantity AS tracked_quantity,
       p.quantity - a.total_amount AS excess_tracked_quantity,
       p.total_cost,
       p.cost_currency,
       p.average_cost_rate
FROM bills.b_accounts a
JOIN bills.b_fx_positions p ON p.account_id = a.account_id
WHERE p.quantity > a.total_amount
ORDER BY p.quantity - a.total_amount DESC;

-- 3) FX position accounting currency is independent from account hierarchy.
--    A position is invalid only when its cost currency is blank/equal to the held currency.
SELECT a.account_id,
       a.account_name,
       a.currency,
       a.parent_account_id,
       p.cost_currency
FROM bills.b_fx_positions p
JOIN bills.b_accounts a ON a.account_id = p.account_id
WHERE p.cost_currency IS NULL
   OR BTRIM(p.cost_currency) = ''
   OR UPPER(p.cost_currency) = UPPER(a.currency);

-- 4) Every child must point to an enabled base account.
SELECT child.account_id,
       child.account_name,
       child.parent_account_id,
       parent.account_name AS parent_name,
       parent.is_base_account,
       parent.enabled
FROM bills.b_accounts child
LEFT JOIN bills.b_accounts parent ON parent.account_id = child.parent_account_id
WHERE child.parent_account_id IS NOT NULL
  AND (parent.account_id IS NULL
       OR parent.is_base_account IS DISTINCT FROM TRUE);

-- 5) Auto-debit account must be an ASSET in the same currency as the liability/configured account.
SELECT target.account_id,
       target.account_name,
       target.currency AS target_currency,
       debit.account_id AS debit_account_id,
       debit.account_name AS debit_account_name,
       debit.currency AS debit_currency,
       debit.account_class_code AS debit_class
FROM bills.b_accounts target
JOIN bills.b_accounts debit ON debit.account_id = target.auto_debit_account_id
WHERE debit.account_class_code <> 'ASSET'
   OR UPPER(debit.currency) <> UPPER(target.currency);

-- 6) Historical transaction/category type mismatch.
SELECT t.transaction_id,
       t.transaction_type_code,
       t.transaction_time,
       c.category_id,
       c.category_name,
       c.category_type_code
FROM bills.b_transactions t
JOIN bills.b_categories c ON c.category_id = t.category_id
WHERE (t.transaction_type_code = 'INCOME' AND c.category_type_code <> 'INCOME')
   OR (t.transaction_type_code IN ('EXPENSE','REFUND','CREDIT_PURCHASE','CREDIT_REFUND')
       AND c.category_type_code <> 'EXPENSE')
ORDER BY t.transaction_time DESC;

-- 7) Statement paid cache must equal payment-allocation facts.
SELECT s.statement_id,
       s.statement_number,
       s.paid_amount AS cached_paid,
       COALESCE(SUM(a.allocated_amount), 0) AS allocated_paid,
       s.paid_amount - COALESCE(SUM(a.allocated_amount), 0) AS difference
FROM bills.b_credit_card_statements s
LEFT JOIN bills.b_statement_payment_allocations a ON a.statement_id = s.statement_id
GROUP BY s.statement_id, s.statement_number, s.paid_amount
HAVING s.paid_amount <> COALESCE(SUM(a.allocated_amount), 0);

-- 8) New V4 FX movement audit: generic transactions that move a tracked foreign account should have
-- movement facts. This query intentionally reports pre-V4 legacy rows for manual review.
SELECT t.transaction_id,
       t.transaction_type_code,
       t.transaction_time,
       e.account_id,
       a.account_name,
       e.amount,
       e.currency
FROM bills.b_transactions t
JOIN bills.b_transaction_entries e ON e.transaction_id = t.transaction_id
JOIN bills.b_accounts a ON a.account_id = e.account_id
JOIN bills.b_fx_positions p ON p.account_id = a.account_id
LEFT JOIN bills.b_fx_position_movements m
  ON m.transaction_id = t.transaction_id AND m.account_id = a.account_id
WHERE t.transaction_type_code IN ('INCOME','REFUND','EXPENSE','TRANSFER','CREDIT_PAYMENT')
  AND a.account_class_code = 'ASSET'
  AND m.movement_id IS NULL
ORDER BY t.transaction_time;
