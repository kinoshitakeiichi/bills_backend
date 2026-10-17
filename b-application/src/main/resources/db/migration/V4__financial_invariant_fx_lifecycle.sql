-- Financial invariant and FX lifecycle hardening.
-- Adds an auditable movement ledger for non-exchange FX position changes and an executed-rate
-- fallback source for historical valuation consumers such as Budget.

INSERT INTO bills.b_dictionary_items
(dictionary_item_id, dictionary_type_id, item_code, item_name, description, sort_order, enabled, system_item, metadata_json, created_at, updated_at)
VALUES
('10000000-0000-0000-0000-000000000152','00000000-0000-0000-0000-000000000013','FX_EXECUTED','FX 实际成交','由已入账 FX 交易沉淀的实际成交汇率；用于历史汇率缺失时兜底',30,TRUE,TRUE,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (dictionary_type_id, item_code) DO UPDATE
SET item_name = EXCLUDED.item_name,
    description = EXCLUDED.description,
    enabled = TRUE,
    system_item = TRUE,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS bills.b_fx_position_movements (
    movement_id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    movement_order INTEGER NOT NULL,
    movement_type_code VARCHAR(30) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    quantity_delta NUMERIC(19,4) NOT NULL,
    cost_delta NUMERIC(19,4) NOT NULL,
    cost_currency VARCHAR(10) NOT NULL,
    valuation_rate NUMERIC(24,10),
    realized_gain_loss NUMERIC(19,4),
    gain_loss_currency VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_fx_position_movements_transaction
      FOREIGN KEY (transaction_id) REFERENCES bills.b_transactions(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_fx_position_movements_account
      FOREIGN KEY (account_id) REFERENCES bills.b_accounts(account_id) ON DELETE RESTRICT,
    CONSTRAINT uk_fx_position_movement_order UNIQUE (transaction_id, movement_order),
    CONSTRAINT ck_fx_position_movement_quantity CHECK (quantity_delta <> 0)
);

CREATE INDEX IF NOT EXISTS idx_fx_position_movements_transaction
  ON bills.b_fx_position_movements(transaction_id, movement_order);
CREATE INDEX IF NOT EXISTS idx_fx_position_movements_account
  ON bills.b_fx_position_movements(account_id, created_at);

-- Preserve exact payment-schedule reductions made by CREDIT_REFUND so a mistaken refund can be
-- reversed without guessing historical installment dates or amounts.
CREATE TABLE IF NOT EXISTS bills.b_credit_refund_schedule_changes (
    change_id VARCHAR(36) PRIMARY KEY,
    refund_transaction_id VARCHAR(36) NOT NULL,
    purchase_transaction_id VARCHAR(36) NOT NULL,
    schedule_id VARCHAR(36) NOT NULL,
    change_order INTEGER NOT NULL,
    principal_reduction NUMERIC(19,4) NOT NULL,
    schedule_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    installment_plan_id VARCHAR(36) NOT NULL,
    liability_account_id VARCHAR(36) NOT NULL,
    installment_no INTEGER NOT NULL,
    installment_count INTEGER NOT NULL,
    principal_before NUMERIC(19,4) NOT NULL,
    fee_before NUMERIC(19,4) NOT NULL,
    adjustment_before NUMERIC(19,4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    payment_type_code VARCHAR(30) NOT NULL,
    scheduled_billing_date DATE NOT NULL,
    scheduled_payment_date DATE NOT NULL,
    calculated_payment_date DATE NOT NULL,
    manual_payment_date DATE,
    status_code VARCHAR(30) NOT NULL,
    schedule_created_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_credit_refund_change_refund
      FOREIGN KEY (refund_transaction_id) REFERENCES bills.b_transactions(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_refund_change_purchase
      FOREIGN KEY (purchase_transaction_id) REFERENCES bills.b_transactions(transaction_id) ON DELETE RESTRICT,
    CONSTRAINT uk_credit_refund_change_order UNIQUE (refund_transaction_id, change_order)
);

CREATE INDEX IF NOT EXISTS idx_credit_refund_changes_refund
  ON bills.b_credit_refund_schedule_changes(refund_transaction_id, change_order);
