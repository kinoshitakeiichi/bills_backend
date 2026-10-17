\set ON_ERROR_STOP on

-- V2.0 Category Master reset + import (R55 approved classification)
-- Run ONLY after account/transaction cleanup.
-- Explicit confirmation is required.
--
-- psql -h database -p 5432 -U myuser -d mydb \
--   -v confirm_category_reset=1 \
--   -f b-application/src/main/resources/db/admin/import-category-master-r55.sql

\if :{?confirm_category_reset}
\else
  \set confirm_category_reset 0
\endif

\if :confirm_category_reset
  \echo 'CATEGORY MASTER RESET/IMPORT CONFIRMED'
\else
  \echo 'ABORTED: re-run with -v confirm_category_reset=1'
  \quit
\endif

BEGIN;
LOCK TABLE bills.b_categories IN ACCESS EXCLUSIVE MODE;
LOCK TABLE bills.b_transactions IN SHARE ROW EXCLUSIVE MODE;

-- Category reset is only safe after the prior transaction cleanup.
DO $$
DECLARE
  referenced_transactions BIGINT;
BEGIN
  SELECT COUNT(*) INTO referenced_transactions
  FROM bills.b_transactions
  WHERE category_id IS NOT NULL;

  IF referenced_transactions <> 0 THEN
    RAISE EXCEPTION
      'Category import aborted: % transaction(s) still reference categories. Finish transaction/account cleanup first.',
      referenced_transactions;
  END IF;
END
$$;

SELECT 'old_categories' AS object_name, COUNT(*) AS row_count FROM bills.b_categories
UNION ALL
SELECT 'category_target_budgets', COUNT(DISTINCT item.budget_id)
FROM bills.b_budget_targets target
JOIN bills.b_budget_items item ON item.budget_item_id = target.budget_item_id
WHERE UPPER(TRIM(target.target_type_code)) = 'CATEGORY'
ORDER BY object_name;

-- Polymorphic budget targets have no FK to Category. Deleting only a target can
-- broaden a mixed budget, so remove the whole budget when it contains CATEGORY.
CREATE TEMP TABLE tmp_category_target_budgets (
  budget_id VARCHAR(36) PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO tmp_category_target_budgets (budget_id)
SELECT DISTINCT item.budget_id
FROM bills.b_budget_targets target
JOIN bills.b_budget_items item ON item.budget_item_id = target.budget_item_id
WHERE UPPER(TRIM(target.target_type_code)) = 'CATEGORY';

DELETE FROM bills.b_budget_targets target
USING bills.b_budget_items item, tmp_category_target_budgets affected
WHERE target.budget_item_id = item.budget_item_id
  AND item.budget_id = affected.budget_id;

DELETE FROM bills.b_budget_items item
USING tmp_category_target_budgets affected
WHERE item.budget_id = affected.budget_id;

DELETE FROM bills.b_budgets budget
USING tmp_category_target_budgets affected
WHERE budget.budget_id = affected.budget_id;

-- Old category tree can now be removed safely.
DELETE FROM bills.b_categories;

CREATE TEMP TABLE tmp_category_seed (
  category_code VARCHAR(50) PRIMARY KEY,
  category_name VARCHAR(100) NOT NULL,
  category_type_code VARCHAR(20) NOT NULL,
  parent_code VARCHAR(50),
  sort_order INTEGER NOT NULL,
  enabled BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_category_seed
(category_code, category_name, category_type_code, parent_code, sort_order, enabled)
VALUES
('INCOME_ROOT','收入','INCOME',NULL,10,TRUE),
('EXPENSE_ROOT','支出','EXPENSE',NULL,20,TRUE),
('FOOD','餐饮','EXPENSE','EXPENSE_ROOT',10,TRUE),
('FOOD_CONVENIENCE','便利店','EXPENSE','FOOD',11,TRUE),
('FOOD_GROCERY','超市','EXPENSE','FOOD',12,TRUE),
('FOOD_DINING','外食','EXPENSE','FOOD',13,TRUE),
('FOOD_DELIVERY','外卖','EXPENSE','FOOD',14,TRUE),
('HOUSING','居住','EXPENSE','EXPENSE_ROOT',20,TRUE),
('HOUSING_RENT','房租','EXPENSE','HOUSING',21,TRUE),
('HOUSING_MANAGEMENT','管理费','EXPENSE','HOUSING',22,TRUE),
('HOUSING_UTILITIES','水电燃气','EXPENSE','HOUSING',23,TRUE),
('HOUSING_COMMUNICATION','通信','EXPENSE','HOUSING',24,TRUE),
('HOUSING_MAINTENANCE','住宅维护','EXPENSE','HOUSING',25,TRUE),
('HOUSING_HOUSEHOLD','生活用品','EXPENSE','HOUSING',26,TRUE),
('HOUSING_INSURANCE','住宅保险','EXPENSE','HOUSING',27,TRUE),
('HOUSING_PURCHASE','购房相关','EXPENSE','HOUSING',28,TRUE),
('TRANSPORT','交通','EXPENSE','EXPENSE_ROOT',30,TRUE),
('TRANSPORT_PUBLIC','公共交通','EXPENSE','TRANSPORT',31,TRUE),
('TRANSPORT_LICENSE','驾照与行政','EXPENSE','TRANSPORT',32,TRUE),
('HEALTH','医疗健康','EXPENSE','EXPENSE_ROOT',40,TRUE),
('HEALTH_MEDICAL','医疗费','EXPENSE','HEALTH',41,TRUE),
('HEALTH_DENTAL','牙科','EXPENSE','HEALTH',42,TRUE),
('HEALTH_ORTHOPEDIC','整骨/康复','EXPENSE','HEALTH',43,TRUE),
('HEALTH_RX','处方药','EXPENSE','HEALTH',44,TRUE),
('HEALTH_OTC','非处方药','EXPENSE','HEALTH',45,TRUE),
('EDUCATION','教育','EXPENSE','EXPENSE_ROOT',50,TRUE),
('EDUCATION_LANGUAGE','语言学习','EXPENSE','EDUCATION',51,TRUE),
('PERSONAL','衣物与个人护理','EXPENSE','EXPENSE_ROOT',60,TRUE),
('PERSONAL_CLOTHING','服饰','EXPENSE','PERSONAL',61,TRUE),
('PERSONAL_GROOMING','理发','EXPENSE','PERSONAL',62,TRUE),
('ENTERTAINMENT','娱乐','EXPENSE','EXPENSE_ROOT',70,TRUE),
('ENTERTAINMENT_GENERAL','普通娱乐','EXPENSE','ENTERTAINMENT',71,TRUE),
('ENTERTAINMENT_OUTDOOR','户外/露营','EXPENSE','ENTERTAINMENT',72,TRUE),
('TRAVEL','旅行','EXPENSE','EXPENSE_ROOT',80,TRUE),
('TRAVEL_TRANSPORT','旅行交通','EXPENSE','TRAVEL',81,TRUE),
('TRAVEL_LODGING','旅行住宿','EXPENSE','TRAVEL',82,TRUE),
('TRAVEL_FOOD','旅行餐饮','EXPENSE','TRAVEL',83,TRUE),
('TRAVEL_SHOPPING','旅行购物','EXPENSE','TRAVEL',84,TRUE),
('TRAVEL_OTHER','旅行其他','EXPENSE','TRAVEL',85,TRUE),
('DIGITAL','数字服务与订阅','EXPENSE','EXPENSE_ROOT',90,TRUE),
('DIGITAL_SOFTWARE','软件订阅','EXPENSE','DIGITAL',91,TRUE),
('DIGITAL_CONTENT','内容订阅','EXPENSE','DIGITAL',92,TRUE),
('DIGITAL_MEMBERSHIP','会员费','EXPENSE','DIGITAL',93,TRUE),
('SHOPPING','购物与耐用品','EXPENSE','EXPENSE_ROOT',100,TRUE),
('SHOPPING_APPLIANCE','家电','EXPENSE','SHOPPING',101,TRUE),
('SHOPPING_CAMERA','摄影器材','EXPENSE','SHOPPING',102,TRUE),
('SHOPPING_LUXURY','奢侈品','EXPENSE','SHOPPING',103,TRUE),
('SHOPPING_OUTDOOR','户外装备','EXPENSE','SHOPPING',104,TRUE),
('TAX_INSURANCE','税金与社会保险','EXPENSE','EXPENSE_ROOT',110,TRUE),
('TAX_RESIDENT','住民税','EXPENSE','TAX_INSURANCE',111,TRUE),
('SOCIAL_PENSION','国民年金','EXPENSE','TAX_INSURANCE',112,TRUE),
('SOCIAL_HEALTH_INSURANCE','医疗保险','EXPENSE','TAX_INSURANCE',113,TRUE),
('FEES','手续费与服务费','EXPENSE','EXPENSE_ROOT',120,TRUE),
('FEES_ATM','ATM手续费','EXPENSE','FEES',121,TRUE),
('FEES_FINANCIAL','金融手续费','EXPENSE','FEES',122,TRUE),
('FEES_POSTAGE','邮寄快递','EXPENSE','FEES',123,TRUE),
('OTHER_EXPENSE','其他支出','EXPENSE','EXPENSE_ROOT',130,TRUE),
('OTHER_TOPUP','充值消费','EXPENSE','OTHER_EXPENSE',131,TRUE),
('EMPLOYMENT_INCOME','劳动收入','INCOME','INCOME_ROOT',200,TRUE),
('SALARY','工资','INCOME','EMPLOYMENT_INCOME',201,TRUE),
('BONUS','奖金','INCOME','EMPLOYMENT_INCOME',202,TRUE),
('REIMBURSEMENT','报销与补偿','INCOME','INCOME_ROOT',210,TRUE),
('REIMBURSEMENT_EXPENSE','报销','INCOME','REIMBURSEMENT',211,TRUE),
('OTHER_INCOME','其他收入','INCOME','INCOME_ROOT',220,TRUE),
('INTEREST_INCOME','利息收入','INCOME','OTHER_INCOME',221,TRUE),
('OTHER_INCOME_MISC','特别/其他收入','INCOME','OTHER_INCOME',222,TRUE),
('HISTORICAL_UNRESOLVED','历史未整理','EXPENSE','EXPENSE_ROOT',900,FALSE),
('HIST_MISSING_CREDIT_PURCHASE','历史信用卡消费缺失','EXPENSE','HISTORICAL_UNRESOLVED',901,FALSE);

-- Validate the seed before writing the real table.
DO $$
DECLARE
  bad_parent_count BIGINT;
  bad_type_count BIGINT;
BEGIN
  SELECT COUNT(*) INTO bad_parent_count
  FROM tmp_category_seed child
  LEFT JOIN tmp_category_seed parent ON parent.category_code = child.parent_code
  WHERE child.parent_code IS NOT NULL
    AND parent.category_code IS NULL;

  SELECT COUNT(*) INTO bad_type_count
  FROM tmp_category_seed child
  JOIN tmp_category_seed parent ON parent.category_code = child.parent_code
  WHERE child.category_type_code <> parent.category_type_code;

  IF bad_parent_count <> 0 OR bad_type_count <> 0 THEN
    RAISE EXCEPTION 'Invalid category seed: missing_parent=%, type_mismatch=%',
      bad_parent_count, bad_type_count;
  END IF;
END
$$;

CREATE TEMP TABLE tmp_category_ids (
  category_code VARCHAR(50) PRIMARY KEY,
  category_id VARCHAR(36) NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO tmp_category_ids (category_code, category_id)
SELECT category_code,
  CASE category_code
      WHEN 'INCOME_ROOT' THEN '30000000-0000-0000-0000-000000000001'
      WHEN 'EXPENSE_ROOT' THEN '30000000-0000-0000-0000-000000000002'
      WHEN 'FOOD' THEN '30000000-0000-0000-0000-000000000020'
      WHEN 'FOOD_CONVENIENCE' THEN '0f495b0e-117d-f5cf-b919-cf3daf5d8a5a'
      WHEN 'FOOD_GROCERY' THEN '4bf5f804-1120-f029-6519-f8037f76fb07'
      WHEN 'FOOD_DINING' THEN 'da422a71-7348-106d-cae5-e2903f9d7076'
      WHEN 'FOOD_DELIVERY' THEN '08f52628-10e5-99b5-b052-6d9726421386'
      WHEN 'HOUSING' THEN '30000000-0000-0000-0000-000000000023'
      WHEN 'HOUSING_RENT' THEN '0d0ea5f2-14a6-6892-479c-499cea9c1215'
      WHEN 'HOUSING_MANAGEMENT' THEN '27e9fb67-c47f-2609-9619-6b6cfc1019c3'
      WHEN 'HOUSING_UTILITIES' THEN '87ea5432-9d50-23e5-398b-4204ee60b77b'
      WHEN 'HOUSING_COMMUNICATION' THEN '0d5f6022-3f44-ebe4-6a87-ebaf7060ae3a'
      WHEN 'HOUSING_MAINTENANCE' THEN '82fbd9ce-1551-48eb-5de7-f848113c24cc'
      WHEN 'HOUSING_HOUSEHOLD' THEN 'a58f8b86-70bf-4321-e2e3-616c4a9662f0'
      WHEN 'HOUSING_INSURANCE' THEN '5a5286ff-05bc-5b13-5d7a-eacfb57586db'
      WHEN 'HOUSING_PURCHASE' THEN '3b1df105-6797-ea71-9773-f72cf35aea35'
      WHEN 'TRANSPORT' THEN '30000000-0000-0000-0000-000000000021'
      WHEN 'TRANSPORT_PUBLIC' THEN '5a70145f-1a6e-0a8c-4821-eafd4c64b766'
      WHEN 'TRANSPORT_LICENSE' THEN 'b14e6bd7-249f-f976-b3bd-face60a2ca48'
      WHEN 'HEALTH' THEN 'a6da0073-3a8f-32ce-eb7e-7b51fbc5a407'
      WHEN 'HEALTH_MEDICAL' THEN '18cb4d04-b3a5-b264-c81d-dba323d21348'
      WHEN 'HEALTH_DENTAL' THEN 'edac09d9-5d9d-58d1-3fb4-6783d424e8d0'
      WHEN 'HEALTH_ORTHOPEDIC' THEN '0435049e-68bd-0a25-03ea-c04ce3fc0c57'
      WHEN 'HEALTH_RX' THEN 'ba142690-402f-e507-6718-5c283d664e35'
      WHEN 'HEALTH_OTC' THEN 'cbcb6498-acfc-5f15-f281-21f87cb57afd'
      WHEN 'EDUCATION' THEN 'a8c0583e-2004-3294-6e57-58abbbdf284c'
      WHEN 'EDUCATION_LANGUAGE' THEN '90b1592e-6534-d6b7-cee7-49f03e0982b5'
      WHEN 'PERSONAL' THEN '1b1f011a-911d-fa2d-a0b8-620568614e1e'
      WHEN 'PERSONAL_CLOTHING' THEN '052f8bf2-e969-b4be-8458-3da738e6e8cd'
      WHEN 'PERSONAL_GROOMING' THEN 'b054b4c0-ac74-11cf-a1c4-a7395febf174'
      WHEN 'ENTERTAINMENT' THEN '43d42cce-2447-3fee-0bef-e88f78651a4d'
      WHEN 'ENTERTAINMENT_GENERAL' THEN '0027ffe9-7646-fecd-cac5-c935f2272926'
      WHEN 'ENTERTAINMENT_OUTDOOR' THEN '2db3efe7-ecb7-f555-777e-eed050f727cd'
      WHEN 'TRAVEL' THEN '4e1a9d40-4688-dc1a-0a20-1358e005ea7d'
      WHEN 'TRAVEL_TRANSPORT' THEN 'e96210da-34cd-d0e7-d7ad-211cf09cb428'
      WHEN 'TRAVEL_LODGING' THEN '000c9b1b-3820-385c-e63a-6e1c9dd512a5'
      WHEN 'TRAVEL_FOOD' THEN 'b65a3875-d352-d3a0-05ee-760b73ebb2f4'
      WHEN 'TRAVEL_SHOPPING' THEN '53cfb0a1-3a1b-bbac-9f97-8a497ed1c19f'
      WHEN 'TRAVEL_OTHER' THEN 'c141d4b9-8d72-c1cb-40fe-115fcb3c7701'
      WHEN 'DIGITAL' THEN '2b003cfa-22de-49e9-e27c-f23d782f9f73'
      WHEN 'DIGITAL_SOFTWARE' THEN '6c7e7db1-de4b-c5cd-2e0c-dabbe04e0968'
      WHEN 'DIGITAL_CONTENT' THEN 'ddb104d5-bcd5-a97c-51df-4f3996fe0d0b'
      WHEN 'DIGITAL_MEMBERSHIP' THEN 'fe1bf752-ebf3-99ff-0c5a-e5ddf89a24c3'
      WHEN 'SHOPPING' THEN '30000000-0000-0000-0000-000000000022'
      WHEN 'SHOPPING_APPLIANCE' THEN 'afab76a8-8c01-cf1d-8fea-8432c1bf5bd9'
      WHEN 'SHOPPING_CAMERA' THEN 'bb688e94-fd5c-704d-38c4-c2a469d7d944'
      WHEN 'SHOPPING_LUXURY' THEN '0f9626f8-14a4-ef3a-88ec-961f5a9e9661'
      WHEN 'SHOPPING_OUTDOOR' THEN '8a443cbe-71cf-db8a-ccd6-12cfd96e29e7'
      WHEN 'TAX_INSURANCE' THEN '07cab93f-8396-c8fa-bea8-87af000a76b1'
      WHEN 'TAX_RESIDENT' THEN 'ca720956-766f-7abb-55ac-c3b9b92bea34'
      WHEN 'SOCIAL_PENSION' THEN 'fd5d3448-7ed7-1dae-ffdf-ba7a4c782277'
      WHEN 'SOCIAL_HEALTH_INSURANCE' THEN 'b40f40f3-c825-0eb2-77c6-491470b97b31'
      WHEN 'FEES' THEN 'd103ff4a-a980-9936-7a2d-07290f383923'
      WHEN 'FEES_ATM' THEN '823988ab-a19d-1e85-06e7-839aebd6d7f8'
      WHEN 'FEES_FINANCIAL' THEN '8d8e3646-85f7-68df-74b5-fe8fab2e15f6'
      WHEN 'FEES_POSTAGE' THEN '1eb14933-a70d-d4ba-fda1-99b37403914d'
      WHEN 'OTHER_EXPENSE' THEN '55b677dc-c633-9711-5462-ae12e98143f4'
      WHEN 'OTHER_TOPUP' THEN 'ffcfb554-512b-ecce-a012-043770448ce7'
      WHEN 'EMPLOYMENT_INCOME' THEN '32f50e41-e1f5-f9d3-bcd9-7bd437156a7c'
      WHEN 'SALARY' THEN '30000000-0000-0000-0000-000000000010'
      WHEN 'BONUS' THEN '30000000-0000-0000-0000-000000000011'
      WHEN 'REIMBURSEMENT' THEN 'af1d4cd1-cea5-acde-9288-cc33188b46fa'
      WHEN 'REIMBURSEMENT_EXPENSE' THEN '1225aba8-a79f-4c39-f4f1-03462afab786'
      WHEN 'OTHER_INCOME' THEN 'a68e86da-0f0b-1972-fbd5-292e4e482d9b'
      WHEN 'INTEREST_INCOME' THEN '30000000-0000-0000-0000-000000000012'
      WHEN 'OTHER_INCOME_MISC' THEN '7a07ecec-4541-b616-ccb5-9036ac2e1227'
      WHEN 'HISTORICAL_UNRESOLVED' THEN '0697c03e-ee4a-fc43-318e-092fab75d227'
      WHEN 'HIST_MISSING_CREDIT_PURCHASE' THEN 'abd89b9d-6349-66d2-e1d2-64dd8fce309b'
    ELSE NULL
  END
FROM tmp_category_seed;

INSERT INTO bills.b_categories
(category_id, category_code, category_name, category_type_code,
 parent_id, parent_key, sort_order, enabled, created_at, updated_at)
SELECT
  ids.category_id,
  seed.category_code,
  seed.category_name,
  seed.category_type_code,
  parent_ids.category_id,
  COALESCE(parent_ids.category_id, 'ROOT'),
  seed.sort_order,
  seed.enabled,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM tmp_category_seed seed
JOIN tmp_category_ids ids ON ids.category_code = seed.category_code
LEFT JOIN tmp_category_ids parent_ids ON parent_ids.category_code = seed.parent_code;

DO $$
DECLARE
  imported BIGINT;
  roots BIGINT;
  bad_parent BIGINT;
  invalid_type BIGINT;
BEGIN
  SELECT COUNT(*) INTO imported FROM bills.b_categories;
  SELECT COUNT(*) INTO roots FROM bills.b_categories WHERE parent_id IS NULL;
  SELECT COUNT(*) INTO bad_parent
  FROM bills.b_categories child
  LEFT JOIN bills.b_categories parent ON parent.category_id = child.parent_id
  WHERE child.parent_id IS NOT NULL AND parent.category_id IS NULL;
  SELECT COUNT(*) INTO invalid_type
  FROM bills.b_categories
  WHERE category_type_code NOT IN ('INCOME','EXPENSE');

  IF imported <> 68 OR roots <> 2 OR bad_parent <> 0 OR invalid_type <> 0 THEN
    RAISE EXCEPTION
      'Category import verification failed: imported=%, expected=68, roots=%, missing_parent=%, invalid_type=%',
      imported, roots, bad_parent, invalid_type;
  END IF;
END
$$;

COMMIT;

SELECT
  c.category_type_code,
  p.category_name AS parent_name,
  c.category_code,
  c.category_name,
  c.enabled,
  c.sort_order
FROM bills.b_categories c
LEFT JOIN bills.b_categories p ON p.category_id = c.parent_id
ORDER BY c.category_type_code, COALESCE(p.sort_order, c.sort_order), c.sort_order, c.category_name;
