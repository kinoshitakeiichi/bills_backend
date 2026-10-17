CREATE SCHEMA IF NOT EXISTS bills;

-- Account-scoped quick transaction templates (V5.7 PR006)
CREATE TABLE IF NOT EXISTS bills.b_account_transaction_templates (
  template_id VARCHAR(36) PRIMARY KEY,
  account_id VARCHAR(36) NOT NULL,
  template_name VARCHAR(100) NOT NULL,
  preset_json JSONB NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_account_transaction_templates_account
    FOREIGN KEY (account_id) REFERENCES bills.b_accounts(account_id) ON DELETE CASCADE,
  CONSTRAINT uk_account_transaction_templates_name UNIQUE (account_id, template_name)
);
CREATE INDEX IF NOT EXISTS idx_account_transaction_templates_account_sort
  ON bills.b_account_transaction_templates(account_id, enabled, sort_order, created_at);
-- Custom reminders (V2.0 settings)
CREATE TABLE IF NOT EXISTS bills.b_reminders (
  reminder_id VARCHAR(36) PRIMARY KEY,
  reminder_name VARCHAR(100) NOT NULL,
  reminder_type_code VARCHAR(50) NOT NULL,
  account_id VARCHAR(36),
  schedule_mode_code VARCHAR(30) NOT NULL,
  remind_date DATE,
  day_of_month INTEGER,
  remind_time TIME NOT NULL,
  timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Tokyo',
  date_rule_source_code VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
  invalid_day_rule_code VARCHAR(30) NOT NULL DEFAULT 'LAST_DAY',
  calendar_code VARCHAR(50),
  business_day_adjustment_code VARCHAR(30) NOT NULL DEFAULT 'NONE',
  notification_url VARCHAR(1000),
  notification_title VARCHAR(200),
  notification_body VARCHAR(1000),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_triggered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_reminders_account
    FOREIGN KEY (account_id) REFERENCES bills.b_accounts(account_id) ON DELETE SET NULL,
  CONSTRAINT ck_reminders_mode
    CHECK (schedule_mode_code IN ('ONCE', 'MONTHLY')),
  CONSTRAINT ck_reminders_date_source
    CHECK (date_rule_source_code IN ('CUSTOM', 'ACCOUNT')),
  CONSTRAINT ck_reminders_day
    CHECK (day_of_month IS NULL OR (day_of_month BETWEEN 1 AND 31))
);
CREATE INDEX IF NOT EXISTS idx_reminders_enabled
  ON bills.b_reminders(enabled);
CREATE INDEX IF NOT EXISTS idx_reminders_account
  ON bills.b_reminders(account_id);

