ALTER TABLE bills.b_reminders
  ADD COLUMN IF NOT EXISTS notification_url VARCHAR(1000);
