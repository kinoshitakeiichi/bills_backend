-- F5-PR002 local test accounts
--
-- Purpose:
--   1) JPY main bank account
--   2) USD child bank account
--   3) JPY credit-card account
--
-- Loaded AFTER db/init/master-data.sql via application.yml.
-- This file is intentionally test-only and uses deterministic IDs.
--
-- Prerequisites provided by master-data.sql:
--   owner person: ME = 20000000-0000-0000-0000-000000000001
--   BANK -> ASSET mapping
--   CREDIT_CARD -> LIABILITY mapping
--
-- IMPORTANT:
-- Do not use this file in production.

INSERT INTO bills.b_accounts
(
  account_id,
  account_name,
  account_code,
  issuer,
  account_type_code,
  account_class_code,
  total_amount,
  currency,
  owner_person_id,
  parent_account_id,
  auto_debit_account_id,
  is_base_account,
  enabled,
  created_at,
  updated_at
)
VALUES
(
  '50000000-0000-0000-0000-000000000001',
  '测试日元银行账户',
  'TEST_BANK_JPY',
  'Test Bank',
  'BANK',
  'ASSET',
  500000.0000,
  'JPY',
  '20000000-0000-0000-0000-000000000001',
  NULL,
  NULL,
  TRUE,
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (account_code, currency) DO UPDATE
SET
  account_name = EXCLUDED.account_name,
  issuer = EXCLUDED.issuer,
  account_type_code = EXCLUDED.account_type_code,
  account_class_code = EXCLUDED.account_class_code,
  owner_person_id = EXCLUDED.owner_person_id,
  parent_account_id = EXCLUDED.parent_account_id,
  auto_debit_account_id = EXCLUDED.auto_debit_account_id,
  is_base_account = EXCLUDED.is_base_account,
  enabled = EXCLUDED.enabled,
  updated_at = CURRENT_TIMESTAMP;


INSERT INTO bills.b_accounts
(
  account_id,
  account_name,
  account_code,
  issuer,
  account_type_code,
  account_class_code,
  total_amount,
  currency,
  owner_person_id,
  parent_account_id,
  auto_debit_account_id,
  is_base_account,
  enabled,
  created_at,
  updated_at
)
VALUES
(
  '50000000-0000-0000-0000-000000000002',
  '测试美元银行账户',
  'TEST_BANK_USD',
  'Test Bank',
  'BANK',
  'ASSET',
  1000.0000,
  'USD',
  '20000000-0000-0000-0000-000000000001',
  '50000000-0000-0000-0000-000000000001',
  NULL,
  FALSE,
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (account_code, currency) DO UPDATE
SET
  account_name = EXCLUDED.account_name,
  issuer = EXCLUDED.issuer,
  account_type_code = EXCLUDED.account_type_code,
  account_class_code = EXCLUDED.account_class_code,
  owner_person_id = EXCLUDED.owner_person_id,
  parent_account_id = EXCLUDED.parent_account_id,
  auto_debit_account_id = EXCLUDED.auto_debit_account_id,
  is_base_account = EXCLUDED.is_base_account,
  enabled = EXCLUDED.enabled,
  updated_at = CURRENT_TIMESTAMP;


INSERT INTO bills.b_accounts
(
  account_id,
  account_name,
  account_code,
  issuer,
  account_type_code,
  account_class_code,
  total_amount,
  currency,
  owner_person_id,
  parent_account_id,
  auto_debit_account_id,
  is_base_account,
  enabled,
  created_at,
  updated_at
)
VALUES
(
  '50000000-0000-0000-0000-000000000003',
  '测试日元信用卡',
  'TEST_CREDIT_JPY',
  'Test Card',
  'CREDIT_CARD',
  'LIABILITY',
  0.0000,
  'JPY',
  '20000000-0000-0000-0000-000000000001',
  NULL,
  '50000000-0000-0000-0000-000000000001',
  FALSE,
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (account_code, currency) DO UPDATE
SET
  account_name = EXCLUDED.account_name,
  issuer = EXCLUDED.issuer,
  account_type_code = EXCLUDED.account_type_code,
  account_class_code = EXCLUDED.account_class_code,
  owner_person_id = EXCLUDED.owner_person_id,
  parent_account_id = EXCLUDED.parent_account_id,
  auto_debit_account_id = EXCLUDED.auto_debit_account_id,
  is_base_account = EXCLUDED.is_base_account,
  enabled = EXCLUDED.enabled,
  updated_at = CURRENT_TIMESTAMP;
