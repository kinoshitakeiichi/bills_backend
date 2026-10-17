-- Optional F4.1 PR-002 test master data.
-- Safe to execute repeatedly. Do not run in production.

INSERT INTO bills.b_persons
(person_id, person_code, person_name, enabled, sort_order, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000002', 'TEST_ENABLED', '测试人员', TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('20000000-0000-0000-0000-000000000003', 'TEST_DISABLED', '停用人员', FALSE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (person_code) DO UPDATE SET
  person_name = EXCLUDED.person_name,
  enabled = EXCLUDED.enabled,
  sort_order = EXCLUDED.sort_order,
  updated_at = CURRENT_TIMESTAMP;

INSERT INTO bills.b_categories
(category_id, category_code, category_name, category_type_code, parent_id, parent_key, sort_order, enabled, created_at, updated_at)
VALUES
('30000000-0000-0000-0000-000000009001', 'TEST_DISABLED_EXPENSE', '停用测试分类', 'EXPENSE',
 '30000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', 9990, FALSE,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (category_code) DO UPDATE SET
  category_name = EXCLUDED.category_name,
  enabled = EXCLUDED.enabled,
  updated_at = CURRENT_TIMESTAMP;

INSERT INTO bills.b_accounts
(account_id, account_name, account_code, issuer, account_class_code, account_type_code,
 currency, total_amount, owner_person_id, parent_account_id, is_base_account,
 auto_debit_account_id, enabled, created_at, updated_at)
VALUES
('40000000-0000-0000-0000-000000009001', '停用测试银行账户', 'TEST_DISABLED_BANK', 'TEST',
 'ASSET', 'BANK', 'JPY', 0, '20000000-0000-0000-0000-000000000001', NULL, TRUE, NULL, FALSE,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (account_code, currency) DO UPDATE SET
  account_name = EXCLUDED.account_name,
  enabled = EXCLUDED.enabled,
  updated_at = CURRENT_TIMESTAMP;
