-- FX Domain V3 phase V3-3A: guarded cutover snapshot metadata.
--
-- The actual FX cost buckets already exist from V5. This table records the one-time historical
-- cutover so Apply is auditable and cannot silently run twice, including the edge case where a
-- future database has zero FX-cost buckets at cutover.

CREATE TABLE IF NOT EXISTS bills.b_currency_inventory_cutover_runs (
    cutover_id VARCHAR(36) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    replay_transaction_count INTEGER NOT NULL,
    replay_account_count INTEGER NOT NULL,
    bucket_count INTEGER NOT NULL,
    movement_count INTEGER NOT NULL,
    CONSTRAINT uk_currency_inventory_cutover_fingerprint UNIQUE (fingerprint),
    CONSTRAINT ck_currency_inventory_cutover_counts CHECK (
      replay_transaction_count >= 0
      AND replay_account_count >= 0
      AND bucket_count >= 0
      AND movement_count >= 0
    )
);
