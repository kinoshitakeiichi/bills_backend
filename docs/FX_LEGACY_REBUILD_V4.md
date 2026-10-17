# FX 历史持仓重建（V4 后补丁）

本补丁不会通过 Flyway 自动修改历史 FX 数据。修复必须显式执行：先 Preview，再 Apply。

## 修复规则

- EXCHANGE 买入：使用实际成交的来源金额/手续费作为成本。
- EXCHANGE 卖出：按历史重放时的移动平均成本结转，并重算 realized gain/loss movement。
- 普通外币 INCOME（例如利息）：调用 `ExchangeRateService.effective(交易日, 外币, FX Position 成本币种, null)` 建立取得成本。
- 指定为 Opening Balance 的历史 INCOME：先查交易日有效汇率；若没有，可在 `openingRateLookAheadDays` 范围内使用最近的后续有效历史快照，并在 Preview 中标为 `OPENING_PROXY:*`。
- 普通外币 EXPENSE：按当时移动平均成本减少 Position；估值汇率只用于 realized FX gain/loss，不影响持仓数量/成本结转。
- 若发现历史 TRANSFER / REFUND / LOAN_* / Cross-FX，本工具会停止，不会猜成本。
- 重建后的 `FX Position.quantity` 必须精确等于 `Account.totalAmount`，否则 Apply 拒绝执行。

## 本次三个账户 Preview

```bash
curl -sS -X POST 'http://localhost:8080/api/v1/fx/admin/legacy-rebuild/preview' \
  -H 'Content-Type: application/json' \
  -d '{
    "accountIds": [
      "b03ed61f-61e7-4f1d-8893-da89361f0ea9",
      "d7862726-0e66-4728-a9b8-bbd1c9cac8f4",
      "a9f99fed-4aae-4f30-a7d1-da93efc0c5b4"
    ],
    "openingBalanceTransactionIds": [
      "2785ab80-b2ec-452a-87e8-03e3efc6c631"
    ],
    "openingRateLookAheadDays": 7
  }' | jq
```

已确认：`2785ab80-b2ec-452a-87e8-03e3efc6c631` 的 `+7.86 CNY` 是微信余额期初余额；其余小额 INCOME 是利息。

预期关键结果：

- SMBC(THB)：quantity = `300.0000 THB`，totalCost = `1449.0000 JPY`，averageCostRate = `4.8300000000`。
- 微信余额：quantity = `8029.0800 CNY`；按当前历史汇率数据，totalCost 约 `193254.5420 JPY`，averageCostRate 约 `24.0693257509 JPY/CNY`。
- SMBC(USD)：quantity = `19376.1100 USD`；按当前历史汇率数据，totalCost 约 `3070078.4200 JPY`，averageCostRate 约 `158.4465829312 JPY/USD`。

## Apply

先做数据库备份并确认 Preview 后，再执行：

```bash
curl -sS -X POST 'http://localhost:8080/api/v1/fx/admin/legacy-rebuild/apply' \
  -H 'Content-Type: application/json' \
  -d '{
    "accountIds": [
      "b03ed61f-61e7-4f1d-8893-da89361f0ea9",
      "d7862726-0e66-4728-a9b8-bbd1c9cac8f4",
      "a9f99fed-4aae-4f30-a7d1-da93efc0c5b4"
    ],
    "openingBalanceTransactionIds": [
      "2785ab80-b2ec-452a-87e8-03e3efc6c631"
    ],
    "openingRateLookAheadDays": 7
  }' | jq
```

Apply 会重建指定账户的 `b_fx_positions` 和 `b_fx_position_movements`，不会修改原始 `Transaction` / `TransactionEntry`。

## 验证

执行：

```bash
psql -h database -p 5432 -U myuser -d mydb \
  -f b-application/src/main/resources/db/admin/financial-integrity-audit-v4.sql
```

第二组 “FX tracked quantity > account balance” 应从 2 行变为 0 行。


## V4.1：FX 与账户层级解耦

- `parentAccountId` 只表示账户组织/分组关系，不决定账户是否可以参与 FX。
- 独立资产账户（`parentAccountId = null`）可以直接作为 FX 来源或目标账户。
- FX Position 的 `costCurrency` 是持仓自己的会计属性；首次 FX BUY 直接使用来源币种建立成本币种。
- 同币种 Transfer 搬移原持仓成本，不要求两个账户属于同一个父账户/账户组。
- Legacy Rebuild 优先使用现有 Position 的 `costCurrency`；若 Position 不存在，则只在历史 EXCHANGE 能唯一推导成本币种时自动继续。
- 普通 INCOME 不会仅根据 parent 猜测首次成本币种；首次成本建立应来自 FX BUY、Opening Balance 或显式历史重建。
