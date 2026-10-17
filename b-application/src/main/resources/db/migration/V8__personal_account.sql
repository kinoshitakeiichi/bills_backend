-- Open-ended personal current account. Negative balances are intentionally allowed.
INSERT INTO bills.b_dictionary_items
(dictionary_item_id,dictionary_type_id,item_code,item_name,description,sort_order,enabled,system_item,metadata_json,created_at,updated_at)
VALUES
('10000000-0000-0000-0000-000000000019','00000000-0000-0000-0000-000000000002','PERSONAL_ACCOUNT','个人往来','允许余额为正、零或负；借款和还款使用普通 TRANSFER',95,TRUE,TRUE,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT(dictionary_type_id,item_code) DO UPDATE SET
  item_name=EXCLUDED.item_name, description=EXCLUDED.description, sort_order=EXCLUDED.sort_order,
  enabled=TRUE, system_item=TRUE, updated_at=CURRENT_TIMESTAMP;

INSERT INTO bills.b_account_type_class_mappings
(account_type_item_id,account_class_item_id,created_at,updated_at)
VALUES
('10000000-0000-0000-0000-000000000019','10000000-0000-0000-0000-000000000001',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT(account_type_item_id) DO UPDATE SET
  account_class_item_id=EXCLUDED.account_class_item_id, updated_at=CURRENT_TIMESTAMP;
