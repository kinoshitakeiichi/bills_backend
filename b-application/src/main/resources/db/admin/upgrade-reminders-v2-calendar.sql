ALTER TABLE bills.b_reminders
  ADD COLUMN IF NOT EXISTS date_rule_source_code VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
  ADD COLUMN IF NOT EXISTS invalid_day_rule_code VARCHAR(30) NOT NULL DEFAULT 'LAST_DAY',
  ADD COLUMN IF NOT EXISTS calendar_code VARCHAR(50),
  ADD COLUMN IF NOT EXISTS business_day_adjustment_code VARCHAR(30) NOT NULL DEFAULT 'NONE';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_reminders_date_source'
      AND conrelid = 'bills.b_reminders'::regclass
  ) THEN
    ALTER TABLE bills.b_reminders
      ADD CONSTRAINT ck_reminders_date_source
      CHECK (date_rule_source_code IN ('CUSTOM', 'ACCOUNT'));
  END IF;
END
$$;
