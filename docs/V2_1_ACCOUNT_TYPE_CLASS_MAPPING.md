# Database V2.1 Amendment — Account Type / Account Class Mapping

V2.0 核心账务模型保持不变。V2.1 增加结构化映射表：

```text
b_account_type_class_mappings
  account_type_item_id  PK/FK -> b_dictionary_items
  account_class_item_id FK    -> b_dictionary_items
  created_at
  updated_at
```

原因：Account Type → Account Class 是核心领域关系，需要可配置，但不能依赖 Java switch，也不能藏在自由 `metadata_json`。

业务 Account 仍保存稳定的 `account_type_code` 与 `account_class_code`；创建/更新时后端从映射表推导 class。
