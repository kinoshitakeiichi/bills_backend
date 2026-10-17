-- FX Domain V3 phase V3-3C: retire legacy FxPosition writes after the guarded V3 cutover.
--
-- Before cutover, V2 compatibility writes remain allowed. Once b_currency_inventory_cutover_runs
-- contains a marker, b_fx_positions and b_fx_position_movements become immutable historical facts.
-- This DB-level boundary protects against any forgotten legacy code path silently diverging from V3.

CREATE OR REPLACE FUNCTION bills.reject_legacy_fx_position_write_after_cutover()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM bills.b_currency_inventory_cutover_runs) THEN
    RAISE EXCEPTION 'Legacy FX Position is read-only after FX Cost Inventory V3 cutover'
      USING ERRCODE = '55000';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_legacy_fx_position_write_after_cutover
  ON bills.b_fx_positions;
CREATE TRIGGER trg_reject_legacy_fx_position_write_after_cutover
BEFORE INSERT OR UPDATE OR DELETE ON bills.b_fx_positions
FOR EACH ROW EXECUTE FUNCTION bills.reject_legacy_fx_position_write_after_cutover();

DROP TRIGGER IF EXISTS trg_reject_legacy_fx_position_movement_write_after_cutover
  ON bills.b_fx_position_movements;
CREATE TRIGGER trg_reject_legacy_fx_position_movement_write_after_cutover
BEFORE INSERT OR UPDATE OR DELETE ON bills.b_fx_position_movements
FOR EACH ROW EXECUTE FUNCTION bills.reject_legacy_fx_position_write_after_cutover();
