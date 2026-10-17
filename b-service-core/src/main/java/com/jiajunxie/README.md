# JPA 核心实体第一批（V1.1）

包含：

- `Person.java`：轻量人员维度，不是登录用户。
- `Account.java`：当前项目版本，暂不破坏现有 `userId/username` 接口。
- `Category.java`
- `Transaction.java`
- `TransactionEntry.java`
- `TransactionAllocation.java`：支持使用他人账户付款、多人分摊和按人员统计。
- `docs/03-数据库设计.md`

## 放置位置

将 Java 文件放入：

`b-service-core/src/main/java/com/jiajunxie/entity/`

## 本批次的兼容策略

本批次只新增 `Person` 和 `TransactionAllocation`，未直接把现有 Account 的人员字段重命名为 `ownerPersonId`，避免立即破坏当前 Account Request、VO、MapStruct 和前端接口。

下一批迁移时统一修改：

- Account 实体人员字段
- AccountRequest / AccountResponse / AccountVo
- MapStruct 映射
- 前端账户表单字段
- 已有数据库数据迁移

## JPA 同步提醒

开发环境可使用 `ddl-auto=update` 创建两张新表。生产环境不要直接依赖自动更新。
