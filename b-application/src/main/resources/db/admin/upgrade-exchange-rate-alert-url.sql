ALTER TABLE bills.b_exchange_rate_alert_settings
  ADD COLUMN IF NOT EXISTS global_notification_url VARCHAR(1000);

ALTER TABLE bills.b_exchange_rate_alert_rules
  ADD COLUMN IF NOT EXISTS notification_url VARCHAR(1000);
