-- FX Domain V3 phase V3-1: additive currency inventory schema.
--
-- This migration intentionally does NOT change any existing FX write path and does NOT migrate
-- historical data. The new tables are empty state/audit targets for the V3 rebuild preview/apply
-- phases. Legacy b_fx_positions / b_fx_position_movements remain authoritative until a later
-- reviewed migration switches the write path.

CREATE TABLE IF NOT EXISTS bills.b_currency_inventory_buckets (
    bucket_id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    origin_type_code VARCHAR(30) NOT NULL,
    source_currency VARCHAR(10),
    quantity NUMERIC(19,4) NOT NULL,
    source_cost_amount NUMERIC(19,4),
    average_cost_rate NUMERIC(24,10),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_currency_inventory_bucket_account
      FOREIGN KEY (account_id) REFERENCES bills.b_accounts(account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_currency_inventory_bucket_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_currency_inventory_bucket_semantics CHECK (
      (
        origin_type_code = 'FX_EXCHANGE'
        AND source_currency IS NOT NULL
        AND source_currency <> currency
        AND source_cost_amount IS NOT NULL
        AND source_cost_amount >= 0
        AND average_cost_rate IS NOT NULL
        AND average_cost_rate >= 0
      )
      OR
      (
        origin_type_code <> 'FX_EXCHANGE'
        AND source_currency IS NULL
        AND source_cost_amount IS NULL
        AND average_cost_rate IS NULL
      )
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_currency_inventory_fx_bucket
  ON bills.b_currency_inventory_buckets(account_id, source_currency)
  WHERE origin_type_code = 'FX_EXCHANGE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_currency_inventory_non_fx_bucket
  ON bills.b_currency_inventory_buckets(account_id, origin_type_code)
  WHERE origin_type_code <> 'FX_EXCHANGE';

CREATE INDEX IF NOT EXISTS idx_currency_inventory_account
  ON bills.b_currency_inventory_buckets(account_id);

CREATE TABLE IF NOT EXISTS bills.b_currency_inventory_movements (
    movement_id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    bucket_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    movement_order INTEGER NOT NULL,
    movement_type_code VARCHAR(30) NOT NULL,
    origin_type_code VARCHAR(30) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    source_currency VARCHAR(10),
    quantity_delta NUMERIC(19,4) NOT NULL,
    source_cost_delta NUMERIC(19,4),
    allocation_ratio NUMERIC(18,12),
    valuation_rate NUMERIC(24,10),
    equivalent_cost_account_currency NUMERIC(19,4),
    realized_gain_loss_account_currency NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_currency_inventory_movement_tx
      FOREIGN KEY (transaction_id) REFERENCES bills.b_transactions(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_currency_inventory_movement_bucket
      FOREIGN KEY (bucket_id) REFERENCES bills.b_currency_inventory_buckets(bucket_id) ON DELETE RESTRICT,
    CONSTRAINT fk_currency_inventory_movement_account
      FOREIGN KEY (account_id) REFERENCES bills.b_accounts(account_id) ON DELETE RESTRICT,
    CONSTRAINT uk_currency_inventory_movement_order UNIQUE (transaction_id, movement_order),
    CONSTRAINT ck_currency_inventory_movement_qty CHECK (quantity_delta <> 0),
    CONSTRAINT ck_currency_inventory_movement_ratio CHECK (
      allocation_ratio IS NULL OR (allocation_ratio >= 0 AND allocation_ratio <= 1)
    ),
    CONSTRAINT ck_currency_inventory_movement_semantics CHECK (
      (
        origin_type_code = 'FX_EXCHANGE'
        AND source_currency IS NOT NULL
        AND source_cost_delta IS NOT NULL
      )
      OR
      (
        origin_type_code <> 'FX_EXCHANGE'
        AND source_currency IS NULL
        AND source_cost_delta IS NULL
      )
    )
);

CREATE INDEX IF NOT EXISTS idx_currency_inventory_movement_account_time
  ON bills.b_currency_inventory_movements(account_id, created_at);

CREATE INDEX IF NOT EXISTS idx_currency_inventory_movement_bucket_time
  ON bills.b_currency_inventory_movements(bucket_id, created_at);
