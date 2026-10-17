# Flyway migrations

Flyway is the authoritative schema migration path for production and E2E profiles.
Hibernate runs with `ddl-auto=validate` there; do not rely on Hibernate to create or mutate
financial tables.

## Current chain

- `V1__baseline_live_schema.sql` — reviewed live-schema baseline.
- `V2__master_data.sql` — master-data normalization/bootstrap.
- `V3__...` — subsequent application migrations already shipped in this baseline.
- `V4__financial_invariant_fx_lifecycle.sql` — FX movement audit ledger, executed-rate fallback,
  and reversible credit-refund schedule audit.
- `V5__currency_inventory_preview.sql` — additive FX Domain V3 inventory bucket/movement schema.
- `V6__currency_inventory_cutover_guard.sql` — one-time cutover audit marker used by the guarded
  V3-3A Apply path; V3-3B switches post-cutover transaction writes to FX Cost Inventory.
- `V7__retire_legacy_fx_position_writes.sql` — after the cutover marker exists, makes legacy
  `b_fx_positions` / `b_fx_position_movements` immutable historical tables.

`db/init/*.sql` contains bootstrap/reference data helpers and is **not** a replacement for the full
Flyway schema chain. `db/admin/*.sql` contains explicit operator/audit tools and must not be run as
an automatic application migration unless promoted into a reviewed `V*` migration.

## Rules

1. Production/E2E schema changes must be versioned Flyway migrations.
2. Never rename an already-applied migration.
3. Destructive/history-changing repairs belong in a separately reviewed admin script first.
4. Financial history repair must be dry-run/audited before mutation; do not silently rebuild costs
   from incomplete historical data.
