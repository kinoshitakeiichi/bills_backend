-- V44.3.2 Flyway baseline generated from the verified live PostgreSQL schema.
-- Existing non-empty databases are baselined at version 1; this file is executed for new databases.

--
-- PostgreSQL database dump
--



SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: bills; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS "bills";


SET default_tablespace = '';

SET default_table_access_method = "heap";

--
-- Name: b_account_transaction_templates; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_account_transaction_templates" (
    "template_id" character varying(36) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "enabled" boolean NOT NULL,
    "preset_json" "jsonb" NOT NULL,
    "sort_order" integer NOT NULL,
    "template_name" character varying(100) NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL
);


--
-- Name: b_account_type_class_mappings; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_account_type_class_mappings" (
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "account_class_item_id" character varying(36) NOT NULL,
    "account_type_item_id" character varying(36) NOT NULL
);


--
-- Name: b_accounts; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_accounts" (
    "enabled" boolean NOT NULL,
    "is_base_account" boolean NOT NULL,
    "total_amount" numeric(19,4) DEFAULT 0 NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "account_class_code" character varying(30) NOT NULL,
    "account_type_code" character varying(30) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "auto_debit_account_id" character varying(36),
    "owner_person_id" character varying(36) NOT NULL,
    "parent_account_id" character varying(36),
    "account_code" character varying(100) NOT NULL,
    "account_name" character varying(100) NOT NULL,
    "issuer" character varying(100)
);


--
-- Name: b_budget_items; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_budget_items" (
    "budget_amount" numeric(19,4) NOT NULL,
    "rollover_enabled" boolean NOT NULL,
    "sort_order" integer NOT NULL,
    "currency" character varying(10) NOT NULL,
    "budget_id" character varying(36) NOT NULL,
    "budget_item_id" character varying(36) NOT NULL,
    CONSTRAINT "ck_budget_items_amount" CHECK (("budget_amount" >= (0)::numeric))
);


--
-- Name: b_budget_targets; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_budget_targets" (
    "include_descendants" boolean NOT NULL,
    "target_type_code" character varying(30) NOT NULL,
    "budget_item_id" character varying(36) NOT NULL,
    "budget_target_id" character varying(36) NOT NULL,
    "target_id" character varying(36),
    "target_value" character varying(150)
);


--
-- Name: b_budgets; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_budgets" (
    "enabled" boolean NOT NULL,
    "period_end" "date" NOT NULL,
    "period_start" "date" NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "base_currency" character varying(10) NOT NULL,
    "period_type_code" character varying(30) NOT NULL,
    "budget_id" character varying(36) NOT NULL,
    "budget_name" character varying(100) NOT NULL
);


--
-- Name: b_business_calendar_days; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_business_calendar_days" (
    "business_day" boolean NOT NULL,
    "calendar_date" "date" NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "calendar_day_id" character varying(36) NOT NULL,
    "calendar_id" character varying(36) NOT NULL,
    "source" character varying(50),
    "holiday_name" character varying(100)
);


--
-- Name: b_business_calendars; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_business_calendars" (
    "enabled" boolean NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "country_code" character varying(10),
    "calendar_id" character varying(36) NOT NULL,
    "calendar_code" character varying(50) NOT NULL,
    "calendar_name" character varying(100) NOT NULL
);


--
-- Name: b_categories; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_categories" (
    "enabled" boolean NOT NULL,
    "sort_order" integer NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "category_type_code" character varying(20) NOT NULL,
    "category_id" character varying(36) NOT NULL,
    "parent_id" character varying(36),
    "parent_key" character varying(36) NOT NULL,
    "category_code" character varying(50) NOT NULL,
    "category_name" character varying(100) NOT NULL,
    "icon_code" character varying(64)
);


--
-- Name: COLUMN "b_categories"."icon_code"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_categories"."icon_code" IS 'Lucide icon stable name used by category UI; null means inherit parent/default visually';


--
-- Name: b_credit_card_profiles; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_credit_card_profiles" (
    "amount_scale" integer NOT NULL,
    "billing_end_day" integer NOT NULL,
    "billing_end_month_offset" integer NOT NULL,
    "billing_start_day" integer NOT NULL,
    "confirmation_day" integer,
    "confirmation_month_offset" integer NOT NULL,
    "credit_limit" numeric(19,4),
    "enabled" boolean NOT NULL,
    "payment_day" integer NOT NULL,
    "payment_month_offset" integer NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "business_day_adjustment_code" character varying(30) NOT NULL,
    "invalid_day_rule_code" character varying(30) NOT NULL,
    "remainder_rule_code" character varying(30) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "calendar_code" character varying(50),
    "timezone" character varying(50) NOT NULL
);


--
-- Name: b_credit_card_statement_items; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_credit_card_statement_items" (
    "adjustment_amount" numeric(19,4) NOT NULL,
    "billed_amount" numeric(19,4) NOT NULL,
    "fee_amount" numeric(19,4) NOT NULL,
    "installment_count" integer NOT NULL,
    "installment_no" integer NOT NULL,
    "principal_amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "item_status_code" character varying(30) NOT NULL,
    "schedule_id" character varying(36) NOT NULL,
    "source_transaction_id" character varying(36) NOT NULL,
    "statement_id" character varying(36) NOT NULL,
    "statement_item_id" character varying(36) NOT NULL
);


--
-- Name: b_credit_card_statements; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_credit_card_statements" (
    "adjustment_amount" numeric(19,4) NOT NULL,
    "calculated_payment_date" "date" NOT NULL,
    "confirmation_date" "date",
    "fee_amount" numeric(19,4) NOT NULL,
    "manual_payment_date" "date",
    "paid_amount" numeric(19,4) NOT NULL,
    "period_end" "date" NOT NULL,
    "period_start" "date" NOT NULL,
    "principal_amount" numeric(19,4) NOT NULL,
    "reopen_count" integer NOT NULL,
    "scheduled_payment_date" "date" NOT NULL,
    "statement_month" integer NOT NULL,
    "statement_year" integer NOT NULL,
    "total_amount" numeric(19,4) NOT NULL,
    "confirmed_at" timestamp(6) with time zone,
    "created_at" timestamp(6) with time zone NOT NULL,
    "reopened_at" timestamp(6) with time zone,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "status_code" character varying(30) NOT NULL,
    "credit_account_id" character varying(36) NOT NULL,
    "statement_id" character varying(36) NOT NULL,
    "statement_number" character varying(80) NOT NULL,
    "reopen_reason" "text"
);


--
-- Name: b_credit_payment_rules; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_credit_payment_rules" (
    "enabled" boolean NOT NULL,
    "priority" integer NOT NULL,
    "target_day" integer NOT NULL,
    "target_month" integer NOT NULL,
    "purchase_end_month_day" character varying(5) NOT NULL,
    "purchase_start_month_day" character varying(5) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "rule_type_code" character varying(30) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "rule_id" character varying(36) NOT NULL
);


--
-- Name: b_dictionary_items; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_dictionary_items" (
    "enabled" boolean NOT NULL,
    "sort_order" integer NOT NULL,
    "system_item" boolean NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "dictionary_item_id" character varying(36) NOT NULL,
    "dictionary_type_id" character varying(36) NOT NULL,
    "item_code" character varying(50) NOT NULL,
    "item_name" character varying(100) NOT NULL,
    "description" "text",
    "metadata_json" "jsonb"
);


--
-- Name: b_dictionary_types; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_dictionary_types" (
    "enabled" boolean NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "dictionary_type_id" character varying(36) NOT NULL,
    "type_code" character varying(50) NOT NULL,
    "type_name" character varying(100) NOT NULL,
    "description" "text"
);


--
-- Name: b_exchange_rate_alert_pair_states; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_exchange_rate_alert_pair_states" (
    "last_observed_rate" numeric(24,10),
    "created_at" timestamp(6) with time zone NOT NULL,
    "last_global_triggered_at" timestamp(6) with time zone,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "base_currency" character varying(10) NOT NULL,
    "quote_currency" character varying(10) NOT NULL,
    "pair_state_id" character varying(36) NOT NULL,
    "rate_source_code" character varying(50) NOT NULL
);


--
-- Name: b_exchange_rate_alert_rules; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_exchange_rate_alert_rules" (
    "bark_archive" integer,
    "bark_call" integer,
    "bark_volume" double precision,
    "cooldown_minutes" integer,
    "enabled" boolean NOT NULL,
    "last_observed_rate" numeric(24,10),
    "threshold_rate" numeric(24,10),
    "created_at" timestamp(6) with time zone NOT NULL,
    "last_triggered_at" timestamp(6) with time zone,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "base_currency" character varying(10) NOT NULL,
    "quote_currency" character varying(10) NOT NULL,
    "threshold_mode_code" character varying(30) NOT NULL,
    "alert_rule_id" character varying(36) NOT NULL,
    "bark_level" character varying(50),
    "channel_code" character varying(50),
    "operator_code" character varying(50) NOT NULL,
    "rate_source_code" character varying(50),
    "bark_sound" character varying(100),
    "rule_name" character varying(100) NOT NULL,
    "notification_title" character varying(200),
    "notification_body_template" character varying(1000),
    "notification_url" character varying(1000)
);


--
-- Name: b_exchange_rate_alert_settings; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_exchange_rate_alert_settings" (
    "bark_archive" integer,
    "bark_call" integer,
    "bark_volume" double precision,
    "default_cooldown_minutes" integer CONSTRAINT "b_exchange_rate_alert_setting_default_cooldown_minutes_not_null" NOT NULL,
    "global_notification_enabled" boolean CONSTRAINT "b_exchange_rate_alert_setti_global_notification_enable_not_null" NOT NULL,
    "master_enabled" boolean NOT NULL,
    "polling_interval_seconds" integer CONSTRAINT "b_exchange_rate_alert_setting_polling_interval_seconds_not_null" NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "settings_id" character varying(36) NOT NULL,
    "bark_level" character varying(50),
    "default_channel_code" character varying(50) NOT NULL,
    "default_rate_source_code" character varying(50) CONSTRAINT "b_exchange_rate_alert_setting_default_rate_source_code_not_null" NOT NULL,
    "bark_group_name" character varying(100),
    "bark_sound" character varying(100),
    "global_notification_title" character varying(200),
    "bark_base_url" character varying(500) NOT NULL,
    "global_notification_body_template" character varying(1000),
    "global_notification_url" character varying(1000)
);


--
-- Name: b_exchange_rates; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_exchange_rates" (
    "exchange_rate" numeric(24,10) NOT NULL,
    "rate_date" "date" NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "base_currency" character varying(10) NOT NULL,
    "quote_currency" character varying(10) NOT NULL,
    "rate_id" character varying(36) NOT NULL,
    "rate_source_code" character varying(50) NOT NULL,
    CONSTRAINT "ck_rate" CHECK (("exchange_rate" > (0)::numeric))
);


--
-- Name: b_fx_details; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_fx_details" (
    "cost_basis_amount" numeric(19,4),
    "executed_rate" numeric(24,10) NOT NULL,
    "fee_amount" numeric(19,4),
    "realized_gain_loss" numeric(19,4),
    "source_amount" numeric(19,4) NOT NULL,
    "target_amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "fee_currency" character varying(10),
    "gain_loss_currency" character varying(10),
    "source_currency" character varying(10) NOT NULL,
    "target_currency" character varying(10) NOT NULL,
    "fx_direction_code" character varying(30) NOT NULL,
    "source_account_id" character varying(36) NOT NULL,
    "target_account_id" character varying(36) NOT NULL,
    "transaction_id" character varying(36) NOT NULL
);


--
-- Name: b_fx_positions; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_fx_positions" (
    "average_cost_rate" numeric(24,10) NOT NULL,
    "quantity" numeric(19,4) NOT NULL,
    "total_cost" numeric(19,4) NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "cost_currency" character varying(10) NOT NULL,
    "currency" character varying(10) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "position_id" character varying(36) NOT NULL,
    CONSTRAINT "ck_quantity_cost" CHECK ((("quantity" >= (0)::numeric) AND ("total_cost" >= (0)::numeric)))
);


--
-- Name: b_installment_plans; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_installment_plans" (
    "fee_amount" numeric(19,4) NOT NULL,
    "first_due_date" "date",
    "installment_count" integer NOT NULL,
    "principal_amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "payment_type_code" character varying(30) NOT NULL,
    "remainder_rule_code" character varying(30) NOT NULL,
    "status_code" character varying(30) NOT NULL,
    "installment_plan_id" character varying(36) NOT NULL,
    "liability_account_id" character varying(36) NOT NULL,
    "payment_rule_id" character varying(36),
    "source_transaction_id" character varying(36) NOT NULL
);


--
-- Name: b_loan_payment_allocations; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_loan_payment_allocations" (
    "allocation_id" character varying(36) NOT NULL,
    "allocated_fee_amount" numeric(19,4) NOT NULL,
    "allocated_interest_amount" numeric(19,4) NOT NULL,
    "allocated_principal_amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "loan_payment_transaction_id" character varying(36) NOT NULL,
    "schedule_id" character varying(36) NOT NULL,
    CONSTRAINT "ck_loan_payment_allocation_fee" CHECK (("allocated_fee_amount" >= (0)::numeric)),
    CONSTRAINT "ck_loan_payment_allocation_interest" CHECK (("allocated_interest_amount" >= (0)::numeric)),
    CONSTRAINT "ck_loan_payment_allocation_nonzero" CHECK (((("allocated_principal_amount" + "allocated_interest_amount") + "allocated_fee_amount") > (0)::numeric)),
    CONSTRAINT "ck_loan_payment_allocation_principal" CHECK (("allocated_principal_amount" >= (0)::numeric))
);


--
-- Name: b_loan_payment_details; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_loan_payment_details" (
    "fee_amount" numeric(19,4) NOT NULL,
    "interest_amount" numeric(19,4) NOT NULL,
    "principal_amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "loan_account_id" character varying(36) NOT NULL,
    "transaction_id" character varying(36) NOT NULL
);


--
-- Name: b_loan_profiles; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_loan_profiles" (
    "annual_rate" numeric(12,8) NOT NULL,
    "maturity_date" "date",
    "original_principal" numeric(19,4),
    "payment_day" integer,
    "start_date" "date" NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "business_day_adjustment_code" character varying(30),
    "funding_mode_code" character varying(30) NOT NULL,
    "loan_type_code" character varying(30) NOT NULL,
    "payment_frequency_code" character varying(30) NOT NULL,
    "rate_type_code" character varying(30) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "calendar_code" character varying(50),
    CONSTRAINT "ck_principal_rate" CHECK ((("annual_rate" >= (0)::numeric) AND (((("funding_mode_code")::"text" = 'PURCHASE_FINANCING'::"text") AND ("original_principal" IS NULL)) OR ((("funding_mode_code")::"text" = ANY ((ARRAY['ACCOUNT_DISBURSEMENT'::character varying, 'DIRECT_FINANCING'::character varying])::"text"[])) AND ("original_principal" > (0)::numeric)))))
);


--
-- Name: b_loan_rate_histories; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_loan_rate_histories" (
    "annual_rate" numeric(12,8) NOT NULL,
    "effective_date" "date" NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "rate_history_id" character varying(36) NOT NULL
);


--
-- Name: b_loan_repayment_schedule_details; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_loan_repayment_schedule_details" (
    "schedule_id" character varying(36) NOT NULL,
    "adjustment_mode_code" character varying(30) NOT NULL,
    "annual_rate_snapshot" numeric(12,8) NOT NULL,
    "calculation_version" character varying(30) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "generation_no" integer NOT NULL,
    "loan_account_id" character varying(36) NOT NULL,
    "rate_effective_from" "date",
    "remainder_rule_code" character varying(30) NOT NULL,
    "remaining_principal_after" numeric(19,4) CONSTRAINT "b_loan_repayment_schedule_de_remaining_principal_after_not_null" NOT NULL,
    "repayment_type_code" character varying(30) NOT NULL,
    "scheduled_interest_amount" numeric(19,4) CONSTRAINT "b_loan_repayment_schedule_de_scheduled_interest_amount_not_null" NOT NULL,
    "amount_scale" integer NOT NULL,
    "manual_fee_amount" numeric(19,4),
    "manual_interest_amount" numeric(19,4),
    "manual_principal_amount" numeric(19,4),
    CONSTRAINT "ck_loan_schedule_detail_generation" CHECK (("generation_no" >= 1)),
    CONSTRAINT "ck_loan_schedule_detail_interest" CHECK (("scheduled_interest_amount" >= (0)::numeric)),
    CONSTRAINT "ck_loan_schedule_detail_rate" CHECK (("annual_rate_snapshot" >= (0)::numeric)),
    CONSTRAINT "ck_loan_schedule_detail_remaining_principal" CHECK (("remaining_principal_after" >= (0)::numeric))
);


--
-- Name: b_payment_schedules; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_payment_schedules" (
    "adjustment_amount" numeric(19,4) NOT NULL,
    "calculated_payment_date" "date" NOT NULL,
    "installment_count" integer NOT NULL,
    "installment_no" integer NOT NULL,
    "manual_payment_date" "date",
    "scheduled_billing_date" "date" NOT NULL,
    "scheduled_fee_amount" numeric(19,4) NOT NULL,
    "scheduled_payment_date" "date" NOT NULL,
    "scheduled_principal_amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "payment_type_code" character varying(30) NOT NULL,
    "status_code" character varying(30) NOT NULL,
    "installment_plan_id" character varying(36) NOT NULL,
    "liability_account_id" character varying(36) NOT NULL,
    "schedule_id" character varying(36) NOT NULL,
    "source_transaction_id" character varying(36) NOT NULL
);


--
-- Name: b_persons; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_persons" (
    "enabled" boolean NOT NULL,
    "sort_order" integer NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "person_code" character varying(30) NOT NULL,
    "person_id" character varying(36) NOT NULL,
    "person_name" character varying(50) NOT NULL
);


--
-- Name: COLUMN "b_persons"."enabled"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."enabled" IS '是否启用';


--
-- Name: COLUMN "b_persons"."sort_order"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."sort_order" IS '显示顺序';


--
-- Name: COLUMN "b_persons"."created_at"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."created_at" IS '创建时间';


--
-- Name: COLUMN "b_persons"."updated_at"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."updated_at" IS '更新时间';


--
-- Name: COLUMN "b_persons"."person_code"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."person_code" IS '稳定业务编码';


--
-- Name: COLUMN "b_persons"."person_id"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."person_id" IS '人员ID';


--
-- Name: COLUMN "b_persons"."person_name"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_persons"."person_name" IS '人员显示名称';


--
-- Name: b_reminders; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_reminders" (
    "reminder_id" character varying(36) NOT NULL,
    "account_id" character varying(36),
    "created_at" timestamp(6) with time zone NOT NULL,
    "day_of_month" integer,
    "enabled" boolean NOT NULL,
    "last_triggered_at" timestamp(6) with time zone,
    "notification_body" character varying(1000),
    "notification_title" character varying(200),
    "remind_date" "date",
    "remind_time" time(0) without time zone NOT NULL,
    "reminder_name" character varying(100) NOT NULL,
    "reminder_type_code" character varying(50) NOT NULL,
    "schedule_mode_code" character varying(30) NOT NULL,
    "timezone" character varying(100) NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "business_day_adjustment_code" character varying(30) NOT NULL,
    "calendar_code" character varying(50),
    "date_rule_source_code" character varying(30) NOT NULL,
    "invalid_day_rule_code" character varying(30) NOT NULL,
    "notification_url" character varying(1000),
    CONSTRAINT "ck_reminders_date_source" CHECK ((("date_rule_source_code")::"text" = ANY ((ARRAY['CUSTOM'::character varying, 'ACCOUNT'::character varying])::"text"[])))
);


--
-- Name: b_schedule_adjustments; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_schedule_adjustments" (
    "after_amount" numeric(19,4),
    "after_date" "date",
    "before_amount" numeric(19,4),
    "before_date" "date",
    "created_at" timestamp(6) with time zone NOT NULL,
    "adjustment_type_code" character varying(30) NOT NULL,
    "adjustment_id" character varying(36) NOT NULL,
    "schedule_id" character varying(36) NOT NULL,
    "reason" "text" NOT NULL
);


--
-- Name: b_statement_adjustments; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_statement_adjustments" (
    "amount" numeric(19,4),
    "new_payment_date" "date",
    "old_payment_date" "date",
    "created_at" timestamp(6) with time zone NOT NULL,
    "reversed_at" timestamp(6) with time zone,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10),
    "adjustment_type_code" character varying(30) NOT NULL,
    "status_code" character varying(30) NOT NULL,
    "linked_transaction_id" character varying(36),
    "reversal_transaction_id" character varying(36),
    "statement_adjustment_id" character varying(36) NOT NULL,
    "statement_id" character varying(36) NOT NULL,
    "reason" "text"
);


--
-- Name: b_statement_payment_allocations; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_statement_payment_allocations" (
    "allocated_amount" numeric(19,4) NOT NULL,
    "allocation_order" integer NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "payment_allocation_id" character varying(36) NOT NULL,
    "payment_transaction_id" character varying(36) NOT NULL,
    "statement_id" character varying(36) NOT NULL
);


--
-- Name: b_transaction_allocations; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_transaction_allocations" (
    "allocated_amount" numeric(19,4) NOT NULL,
    "allocation_ratio" numeric(9,6),
    "created_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "allocation_id" character varying(36) NOT NULL,
    "person_id" character varying(36) NOT NULL,
    "transaction_id" character varying(36) NOT NULL,
    CONSTRAINT "ck_allocation_amount_ratio" CHECK ((("allocated_amount" >= (0)::numeric) AND (("allocation_ratio" IS NULL) OR (("allocation_ratio" >= (0)::numeric) AND ("allocation_ratio" <= (1)::numeric)))))
);


--
-- Name: COLUMN "b_transaction_allocations"."allocated_amount"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."allocated_amount" IS '人员承担金额，使用正数';


--
-- Name: COLUMN "b_transaction_allocations"."allocation_ratio"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."allocation_ratio" IS '可选分摊比例';


--
-- Name: COLUMN "b_transaction_allocations"."created_at"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."created_at" IS '创建时间';


--
-- Name: COLUMN "b_transaction_allocations"."currency"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."currency" IS '分摊币种';


--
-- Name: COLUMN "b_transaction_allocations"."allocation_id"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."allocation_id" IS '分摊ID';


--
-- Name: COLUMN "b_transaction_allocations"."person_id"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."person_id" IS '消费或收入归属人员';


--
-- Name: COLUMN "b_transaction_allocations"."transaction_id"; Type: COMMENT; Schema: bills; Owner: -
--

COMMENT ON COLUMN "bills"."b_transaction_allocations"."transaction_id" IS '交易ID';


--
-- Name: b_transaction_entries; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_transaction_entries" (
    "amount" numeric(19,4) NOT NULL,
    "created_at" timestamp(6) with time zone NOT NULL,
    "currency" character varying(10) NOT NULL,
    "entry_role_code" character varying(30) NOT NULL,
    "account_id" character varying(36) NOT NULL,
    "entry_id" character varying(36) NOT NULL,
    "transaction_id" character varying(36) NOT NULL,
    CONSTRAINT "ck_amount" CHECK (("amount" <> (0)::numeric))
);


--
-- Name: b_transactions; Type: TABLE; Schema: bills; Owner: -
--

CREATE TABLE "bills"."b_transactions" (
    "created_at" timestamp(6) with time zone NOT NULL,
    "transaction_time" timestamp(6) with time zone NOT NULL,
    "updated_at" timestamp(6) with time zone NOT NULL,
    "origin_code" character varying(30),
    "transaction_type_code" character varying(30) NOT NULL,
    "category_id" character varying(36),
    "origin_reference_id" character varying(36),
    "transaction_id" character varying(36) NOT NULL,
    "merchant" character varying(150),
    "remark" "text"
);


--
-- Name: b_account_transaction_templates b_account_transaction_templates_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_account_transaction_templates"
    ADD CONSTRAINT "b_account_transaction_templates_pkey" PRIMARY KEY ("template_id");


--
-- Name: b_account_type_class_mappings b_account_type_class_mappings_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_account_type_class_mappings"
    ADD CONSTRAINT "b_account_type_class_mappings_pkey" PRIMARY KEY ("account_type_item_id");


--
-- Name: b_accounts b_accounts_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_accounts"
    ADD CONSTRAINT "b_accounts_pkey" PRIMARY KEY ("account_id");


--
-- Name: b_budget_items b_budget_items_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_budget_items"
    ADD CONSTRAINT "b_budget_items_pkey" PRIMARY KEY ("budget_item_id");


--
-- Name: b_budget_targets b_budget_targets_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_budget_targets"
    ADD CONSTRAINT "b_budget_targets_pkey" PRIMARY KEY ("budget_target_id");


--
-- Name: b_budgets b_budgets_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_budgets"
    ADD CONSTRAINT "b_budgets_pkey" PRIMARY KEY ("budget_id");


--
-- Name: b_business_calendar_days b_business_calendar_days_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_business_calendar_days"
    ADD CONSTRAINT "b_business_calendar_days_pkey" PRIMARY KEY ("calendar_day_id");


--
-- Name: b_business_calendars b_business_calendars_calendar_code_key; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_business_calendars"
    ADD CONSTRAINT "b_business_calendars_calendar_code_key" UNIQUE ("calendar_code");


--
-- Name: b_business_calendars b_business_calendars_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_business_calendars"
    ADD CONSTRAINT "b_business_calendars_pkey" PRIMARY KEY ("calendar_id");


--
-- Name: b_categories b_categories_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_categories"
    ADD CONSTRAINT "b_categories_pkey" PRIMARY KEY ("category_id");


--
-- Name: b_credit_card_profiles b_credit_card_profiles_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_card_profiles"
    ADD CONSTRAINT "b_credit_card_profiles_pkey" PRIMARY KEY ("account_id");


--
-- Name: b_credit_card_statement_items b_credit_card_statement_items_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_card_statement_items"
    ADD CONSTRAINT "b_credit_card_statement_items_pkey" PRIMARY KEY ("statement_item_id");


--
-- Name: b_credit_card_statements b_credit_card_statements_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_card_statements"
    ADD CONSTRAINT "b_credit_card_statements_pkey" PRIMARY KEY ("statement_id");


--
-- Name: b_credit_payment_rules b_credit_payment_rules_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_payment_rules"
    ADD CONSTRAINT "b_credit_payment_rules_pkey" PRIMARY KEY ("rule_id");


--
-- Name: b_dictionary_items b_dictionary_items_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_dictionary_items"
    ADD CONSTRAINT "b_dictionary_items_pkey" PRIMARY KEY ("dictionary_item_id");


--
-- Name: b_dictionary_types b_dictionary_types_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_dictionary_types"
    ADD CONSTRAINT "b_dictionary_types_pkey" PRIMARY KEY ("dictionary_type_id");


--
-- Name: b_exchange_rate_alert_pair_states b_exchange_rate_alert_pair_states_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_exchange_rate_alert_pair_states"
    ADD CONSTRAINT "b_exchange_rate_alert_pair_states_pkey" PRIMARY KEY ("pair_state_id");


--
-- Name: b_exchange_rate_alert_rules b_exchange_rate_alert_rules_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_exchange_rate_alert_rules"
    ADD CONSTRAINT "b_exchange_rate_alert_rules_pkey" PRIMARY KEY ("alert_rule_id");


--
-- Name: b_exchange_rate_alert_settings b_exchange_rate_alert_settings_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_exchange_rate_alert_settings"
    ADD CONSTRAINT "b_exchange_rate_alert_settings_pkey" PRIMARY KEY ("settings_id");


--
-- Name: b_exchange_rates b_exchange_rates_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_exchange_rates"
    ADD CONSTRAINT "b_exchange_rates_pkey" PRIMARY KEY ("rate_id");


--
-- Name: b_fx_details b_fx_details_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_fx_details"
    ADD CONSTRAINT "b_fx_details_pkey" PRIMARY KEY ("transaction_id");


--
-- Name: b_fx_positions b_fx_positions_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_fx_positions"
    ADD CONSTRAINT "b_fx_positions_pkey" PRIMARY KEY ("position_id");


--
-- Name: b_installment_plans b_installment_plans_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_installment_plans"
    ADD CONSTRAINT "b_installment_plans_pkey" PRIMARY KEY ("installment_plan_id");


--
-- Name: b_installment_plans b_installment_plans_source_transaction_id_key; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_installment_plans"
    ADD CONSTRAINT "b_installment_plans_source_transaction_id_key" UNIQUE ("source_transaction_id");


--
-- Name: b_loan_payment_allocations b_loan_payment_allocations_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_payment_allocations"
    ADD CONSTRAINT "b_loan_payment_allocations_pkey" PRIMARY KEY ("allocation_id");


--
-- Name: b_loan_payment_details b_loan_payment_details_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_payment_details"
    ADD CONSTRAINT "b_loan_payment_details_pkey" PRIMARY KEY ("transaction_id");


--
-- Name: b_loan_profiles b_loan_profiles_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_profiles"
    ADD CONSTRAINT "b_loan_profiles_pkey" PRIMARY KEY ("account_id");


--
-- Name: b_loan_rate_histories b_loan_rate_histories_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_rate_histories"
    ADD CONSTRAINT "b_loan_rate_histories_pkey" PRIMARY KEY ("rate_history_id");


--
-- Name: b_loan_repayment_schedule_details b_loan_repayment_schedule_details_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_repayment_schedule_details"
    ADD CONSTRAINT "b_loan_repayment_schedule_details_pkey" PRIMARY KEY ("schedule_id");


--
-- Name: b_payment_schedules b_payment_schedules_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_payment_schedules"
    ADD CONSTRAINT "b_payment_schedules_pkey" PRIMARY KEY ("schedule_id");


--
-- Name: b_persons b_persons_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_persons"
    ADD CONSTRAINT "b_persons_pkey" PRIMARY KEY ("person_id");


--
-- Name: b_reminders b_reminders_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_reminders"
    ADD CONSTRAINT "b_reminders_pkey" PRIMARY KEY ("reminder_id");


--
-- Name: b_schedule_adjustments b_schedule_adjustments_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_schedule_adjustments"
    ADD CONSTRAINT "b_schedule_adjustments_pkey" PRIMARY KEY ("adjustment_id");


--
-- Name: b_statement_adjustments b_statement_adjustments_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_statement_adjustments"
    ADD CONSTRAINT "b_statement_adjustments_pkey" PRIMARY KEY ("statement_adjustment_id");


--
-- Name: b_statement_payment_allocations b_statement_payment_allocations_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_statement_payment_allocations"
    ADD CONSTRAINT "b_statement_payment_allocations_pkey" PRIMARY KEY ("payment_allocation_id");


--
-- Name: b_transaction_allocations b_transaction_allocations_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_allocations"
    ADD CONSTRAINT "b_transaction_allocations_pkey" PRIMARY KEY ("allocation_id");


--
-- Name: b_transaction_entries b_transaction_entries_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_entries"
    ADD CONSTRAINT "b_transaction_entries_pkey" PRIMARY KEY ("entry_id");


--
-- Name: b_transactions b_transactions_pkey; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transactions"
    ADD CONSTRAINT "b_transactions_pkey" PRIMARY KEY ("transaction_id");


--
-- Name: b_accounts uk_accounts_code_currency; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_accounts"
    ADD CONSTRAINT "uk_accounts_code_currency" UNIQUE ("account_code", "currency");


--
-- Name: b_transaction_allocations uk_allocation_transaction_person; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_allocations"
    ADD CONSTRAINT "uk_allocation_transaction_person" UNIQUE ("transaction_id", "person_id");


--
-- Name: b_business_calendars uk_business_calendar_code; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_business_calendars"
    ADD CONSTRAINT "uk_business_calendar_code" UNIQUE ("calendar_code");


--
-- Name: b_business_calendar_days uk_business_calendar_day; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_business_calendar_days"
    ADD CONSTRAINT "uk_business_calendar_day" UNIQUE ("calendar_id", "calendar_date");


--
-- Name: b_categories uk_category_code; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_categories"
    ADD CONSTRAINT "uk_category_code" UNIQUE ("category_code");


--
-- Name: b_categories uk_category_sibling; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_categories"
    ADD CONSTRAINT "uk_category_sibling" UNIQUE ("parent_key", "category_name");


--
-- Name: b_dictionary_items uk_dictionary_item_code; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_dictionary_items"
    ADD CONSTRAINT "uk_dictionary_item_code" UNIQUE ("dictionary_type_id", "item_code");


--
-- Name: b_dictionary_types uk_dictionary_type_code; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_dictionary_types"
    ADD CONSTRAINT "uk_dictionary_type_code" UNIQUE ("type_code");


--
-- Name: b_exchange_rates uk_exchange_rate; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_exchange_rates"
    ADD CONSTRAINT "uk_exchange_rate" UNIQUE ("rate_date", "base_currency", "quote_currency", "rate_source_code");


--
-- Name: b_exchange_rate_alert_pair_states uk_fx_alert_pair_state; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_exchange_rate_alert_pair_states"
    ADD CONSTRAINT "uk_fx_alert_pair_state" UNIQUE ("base_currency", "quote_currency", "rate_source_code");


--
-- Name: b_fx_positions uk_fx_position_account_currency; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_fx_positions"
    ADD CONSTRAINT "uk_fx_position_account_currency" UNIQUE ("account_id", "currency");


--
-- Name: b_loan_payment_allocations uk_loan_payment_allocation_pair; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_payment_allocations"
    ADD CONSTRAINT "uk_loan_payment_allocation_pair" UNIQUE ("loan_payment_transaction_id", "schedule_id");


--
-- Name: b_loan_rate_histories uk_loan_rate_effective; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_rate_histories"
    ADD CONSTRAINT "uk_loan_rate_effective" UNIQUE ("account_id", "effective_date");


--
-- Name: b_payment_schedules uk_payment_schedule_installment; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_payment_schedules"
    ADD CONSTRAINT "uk_payment_schedule_installment" UNIQUE ("installment_plan_id", "installment_no");


--
-- Name: b_persons uk_persons_person_code; Type: CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_persons"
    ADD CONSTRAINT "uk_persons_person_code" UNIQUE ("person_code");


--
-- Name: idx_account_transaction_templates_account_sort; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_account_transaction_templates_account_sort" ON "bills"."b_account_transaction_templates" USING "btree" ("account_id", "enabled", "sort_order", "created_at");


--
-- Name: idx_accounts_class_type; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_accounts_class_type" ON "bills"."b_accounts" USING "btree" ("account_class_code", "account_type_code");


--
-- Name: idx_accounts_enabled; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_accounts_enabled" ON "bills"."b_accounts" USING "btree" ("enabled");


--
-- Name: idx_accounts_owner; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_accounts_owner" ON "bills"."b_accounts" USING "btree" ("owner_person_id");


--
-- Name: idx_accounts_parent; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_accounts_parent" ON "bills"."b_accounts" USING "btree" ("parent_account_id");


--
-- Name: idx_allocations_person; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_allocations_person" ON "bills"."b_transaction_allocations" USING "btree" ("person_id");


--
-- Name: idx_allocations_person_transaction; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_allocations_person_transaction" ON "bills"."b_transaction_allocations" USING "btree" ("person_id", "transaction_id");


--
-- Name: idx_allocations_transaction; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_allocations_transaction" ON "bills"."b_transaction_allocations" USING "btree" ("transaction_id");


--
-- Name: idx_budget_items_budget; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_budget_items_budget" ON "bills"."b_budget_items" USING "btree" ("budget_id");


--
-- Name: idx_budget_targets_item; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_budget_targets_item" ON "bills"."b_budget_targets" USING "btree" ("budget_item_id", "target_type_code");


--
-- Name: idx_budgets_period; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_budgets_period" ON "bills"."b_budgets" USING "btree" ("period_start", "period_end", "enabled");


--
-- Name: idx_calendar_days_lookup; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_calendar_days_lookup" ON "bills"."b_business_calendar_days" USING "btree" ("calendar_id", "calendar_date");


--
-- Name: idx_categories_parent; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_categories_parent" ON "bills"."b_categories" USING "btree" ("parent_id");


--
-- Name: idx_categories_type_enabled; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_categories_type_enabled" ON "bills"."b_categories" USING "btree" ("category_type_code", "enabled");


--
-- Name: idx_credit_rules_account; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_credit_rules_account" ON "bills"."b_credit_payment_rules" USING "btree" ("account_id", "enabled", "priority");


--
-- Name: idx_dictionary_items_type_enabled_sort; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_dictionary_items_type_enabled_sort" ON "bills"."b_dictionary_items" USING "btree" ("dictionary_type_id", "enabled", "sort_order");


--
-- Name: idx_entries_account; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_entries_account" ON "bills"."b_transaction_entries" USING "btree" ("account_id");


--
-- Name: idx_entries_account_transaction; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_entries_account_transaction" ON "bills"."b_transaction_entries" USING "btree" ("account_id", "transaction_id");


--
-- Name: idx_entries_transaction; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_entries_transaction" ON "bills"."b_transaction_entries" USING "btree" ("transaction_id");


--
-- Name: idx_exchange_rates_pair_date; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_exchange_rates_pair_date" ON "bills"."b_exchange_rates" USING "btree" ("base_currency", "quote_currency", "rate_date");


--
-- Name: idx_fx_alert_pair_state_pair; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_fx_alert_pair_state_pair" ON "bills"."b_exchange_rate_alert_pair_states" USING "btree" ("base_currency", "quote_currency", "rate_source_code");


--
-- Name: idx_fx_alert_rules_enabled; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_fx_alert_rules_enabled" ON "bills"."b_exchange_rate_alert_rules" USING "btree" ("enabled");


--
-- Name: idx_fx_alert_rules_pair; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_fx_alert_rules_pair" ON "bills"."b_exchange_rate_alert_rules" USING "btree" ("base_currency", "quote_currency", "rate_source_code");


--
-- Name: idx_fx_positions_account; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_fx_positions_account" ON "bills"."b_fx_positions" USING "btree" ("account_id");


--
-- Name: idx_installment_plan_account; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_installment_plan_account" ON "bills"."b_installment_plans" USING "btree" ("liability_account_id");


--
-- Name: idx_installment_plan_status; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_installment_plan_status" ON "bills"."b_installment_plans" USING "btree" ("status_code");


--
-- Name: idx_loan_payment_allocation_payment; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_loan_payment_allocation_payment" ON "bills"."b_loan_payment_allocations" USING "btree" ("loan_payment_transaction_id");


--
-- Name: idx_loan_payment_allocation_schedule; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_loan_payment_allocation_schedule" ON "bills"."b_loan_payment_allocations" USING "btree" ("schedule_id");


--
-- Name: idx_loan_rates_account_date; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_loan_rates_account_date" ON "bills"."b_loan_rate_histories" USING "btree" ("account_id", "effective_date");


--
-- Name: idx_loan_schedule_detail_account; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_loan_schedule_detail_account" ON "bills"."b_loan_repayment_schedule_details" USING "btree" ("loan_account_id");


--
-- Name: idx_loan_schedule_detail_generation; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_loan_schedule_detail_generation" ON "bills"."b_loan_repayment_schedule_details" USING "btree" ("loan_account_id", "generation_no");


--
-- Name: idx_payment_schedule_account_status; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_payment_schedule_account_status" ON "bills"."b_payment_schedules" USING "btree" ("liability_account_id", "status_code");


--
-- Name: idx_payment_schedule_billing_date; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_payment_schedule_billing_date" ON "bills"."b_payment_schedules" USING "btree" ("scheduled_billing_date");


--
-- Name: idx_payment_schedule_payment_date; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_payment_schedule_payment_date" ON "bills"."b_payment_schedules" USING "btree" ("calculated_payment_date");


--
-- Name: idx_persons_enabled_sort; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_persons_enabled_sort" ON "bills"."b_persons" USING "btree" ("enabled", "sort_order");


--
-- Name: idx_reminders_account; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_reminders_account" ON "bills"."b_reminders" USING "btree" ("account_id");


--
-- Name: idx_reminders_enabled; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_reminders_enabled" ON "bills"."b_reminders" USING "btree" ("enabled");


--
-- Name: idx_schedule_adjustments_schedule; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_schedule_adjustments_schedule" ON "bills"."b_schedule_adjustments" USING "btree" ("schedule_id");


--
-- Name: idx_transactions_category; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_transactions_category" ON "bills"."b_transactions" USING "btree" ("category_id");


--
-- Name: idx_transactions_time; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_transactions_time" ON "bills"."b_transactions" USING "btree" ("transaction_time");


--
-- Name: idx_transactions_type_time; Type: INDEX; Schema: bills; Owner: -
--

CREATE INDEX "idx_transactions_type_time" ON "bills"."b_transactions" USING "btree" ("transaction_type_code", "transaction_time");


--
-- Name: b_account_type_class_mappings fk_account_type_mapping_class_item; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_account_type_class_mappings"
    ADD CONSTRAINT "fk_account_type_mapping_class_item" FOREIGN KEY ("account_class_item_id") REFERENCES "bills"."b_dictionary_items"("dictionary_item_id");


--
-- Name: b_account_type_class_mappings fk_account_type_mapping_type_item; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_account_type_class_mappings"
    ADD CONSTRAINT "fk_account_type_mapping_type_item" FOREIGN KEY ("account_type_item_id") REFERENCES "bills"."b_dictionary_items"("dictionary_item_id");


--
-- Name: b_accounts fk_accounts_auto_debit; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_accounts"
    ADD CONSTRAINT "fk_accounts_auto_debit" FOREIGN KEY ("auto_debit_account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_accounts fk_accounts_owner_person; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_accounts"
    ADD CONSTRAINT "fk_accounts_owner_person" FOREIGN KEY ("owner_person_id") REFERENCES "bills"."b_persons"("person_id");


--
-- Name: b_accounts fk_accounts_parent; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_accounts"
    ADD CONSTRAINT "fk_accounts_parent" FOREIGN KEY ("parent_account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_transaction_allocations fk_allocations_person; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_allocations"
    ADD CONSTRAINT "fk_allocations_person" FOREIGN KEY ("person_id") REFERENCES "bills"."b_persons"("person_id");


--
-- Name: b_transaction_allocations fk_allocations_transaction; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_allocations"
    ADD CONSTRAINT "fk_allocations_transaction" FOREIGN KEY ("transaction_id") REFERENCES "bills"."b_transactions"("transaction_id");


--
-- Name: b_budget_items fk_budget_items_budget; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_budget_items"
    ADD CONSTRAINT "fk_budget_items_budget" FOREIGN KEY ("budget_id") REFERENCES "bills"."b_budgets"("budget_id");


--
-- Name: b_budget_targets fk_budget_targets_item; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_budget_targets"
    ADD CONSTRAINT "fk_budget_targets_item" FOREIGN KEY ("budget_item_id") REFERENCES "bills"."b_budget_items"("budget_item_id");


--
-- Name: b_business_calendar_days fk_calendar_days_calendar; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_business_calendar_days"
    ADD CONSTRAINT "fk_calendar_days_calendar" FOREIGN KEY ("calendar_id") REFERENCES "bills"."b_business_calendars"("calendar_id");


--
-- Name: b_categories fk_categories_parent; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_categories"
    ADD CONSTRAINT "fk_categories_parent" FOREIGN KEY ("parent_id") REFERENCES "bills"."b_categories"("category_id");


--
-- Name: b_credit_card_profiles fk_credit_profiles_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_card_profiles"
    ADD CONSTRAINT "fk_credit_profiles_account" FOREIGN KEY ("account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_credit_card_profiles fk_credit_profiles_calendar; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_card_profiles"
    ADD CONSTRAINT "fk_credit_profiles_calendar" FOREIGN KEY ("calendar_code") REFERENCES "bills"."b_business_calendars"("calendar_code");


--
-- Name: b_credit_payment_rules fk_credit_rules_profile; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_credit_payment_rules"
    ADD CONSTRAINT "fk_credit_rules_profile" FOREIGN KEY ("account_id") REFERENCES "bills"."b_credit_card_profiles"("account_id");


--
-- Name: b_dictionary_items fk_dictionary_items_type; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_dictionary_items"
    ADD CONSTRAINT "fk_dictionary_items_type" FOREIGN KEY ("dictionary_type_id") REFERENCES "bills"."b_dictionary_types"("dictionary_type_id");


--
-- Name: b_transaction_entries fk_entries_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_entries"
    ADD CONSTRAINT "fk_entries_account" FOREIGN KEY ("account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_transaction_entries fk_entries_transaction; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transaction_entries"
    ADD CONSTRAINT "fk_entries_transaction" FOREIGN KEY ("transaction_id") REFERENCES "bills"."b_transactions"("transaction_id");


--
-- Name: b_fx_positions fk_fx_positions_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_fx_positions"
    ADD CONSTRAINT "fk_fx_positions_account" FOREIGN KEY ("account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_installment_plans fk_installment_plan_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_installment_plans"
    ADD CONSTRAINT "fk_installment_plan_account" FOREIGN KEY ("liability_account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_installment_plans fk_installment_plan_payment_rule; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_installment_plans"
    ADD CONSTRAINT "fk_installment_plan_payment_rule" FOREIGN KEY ("payment_rule_id") REFERENCES "bills"."b_credit_payment_rules"("rule_id");


--
-- Name: b_installment_plans fk_installment_plan_transaction; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_installment_plans"
    ADD CONSTRAINT "fk_installment_plan_transaction" FOREIGN KEY ("source_transaction_id") REFERENCES "bills"."b_transactions"("transaction_id");


--
-- Name: b_loan_payment_allocations fk_loan_payment_allocation_payment; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_payment_allocations"
    ADD CONSTRAINT "fk_loan_payment_allocation_payment" FOREIGN KEY ("loan_payment_transaction_id") REFERENCES "bills"."b_transactions"("transaction_id");


--
-- Name: b_loan_payment_allocations fk_loan_payment_allocation_schedule; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_payment_allocations"
    ADD CONSTRAINT "fk_loan_payment_allocation_schedule" FOREIGN KEY ("schedule_id") REFERENCES "bills"."b_payment_schedules"("schedule_id");


--
-- Name: b_loan_profiles fk_loan_profiles_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_profiles"
    ADD CONSTRAINT "fk_loan_profiles_account" FOREIGN KEY ("account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_loan_profiles fk_loan_profiles_calendar; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_profiles"
    ADD CONSTRAINT "fk_loan_profiles_calendar" FOREIGN KEY ("calendar_code") REFERENCES "bills"."b_business_calendars"("calendar_code");


--
-- Name: b_loan_rate_histories fk_loan_rates_profile; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_rate_histories"
    ADD CONSTRAINT "fk_loan_rates_profile" FOREIGN KEY ("account_id") REFERENCES "bills"."b_loan_profiles"("account_id");


--
-- Name: b_loan_repayment_schedule_details fk_loan_schedule_detail_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_repayment_schedule_details"
    ADD CONSTRAINT "fk_loan_schedule_detail_account" FOREIGN KEY ("loan_account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_loan_repayment_schedule_details fk_loan_schedule_detail_schedule; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_loan_repayment_schedule_details"
    ADD CONSTRAINT "fk_loan_schedule_detail_schedule" FOREIGN KEY ("schedule_id") REFERENCES "bills"."b_payment_schedules"("schedule_id");


--
-- Name: b_payment_schedules fk_payment_schedule_account; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_payment_schedules"
    ADD CONSTRAINT "fk_payment_schedule_account" FOREIGN KEY ("liability_account_id") REFERENCES "bills"."b_accounts"("account_id");


--
-- Name: b_payment_schedules fk_payment_schedule_plan; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_payment_schedules"
    ADD CONSTRAINT "fk_payment_schedule_plan" FOREIGN KEY ("installment_plan_id") REFERENCES "bills"."b_installment_plans"("installment_plan_id");


--
-- Name: b_payment_schedules fk_payment_schedule_transaction; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_payment_schedules"
    ADD CONSTRAINT "fk_payment_schedule_transaction" FOREIGN KEY ("source_transaction_id") REFERENCES "bills"."b_transactions"("transaction_id");


--
-- Name: b_schedule_adjustments fk_schedule_adjustments_schedule; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_schedule_adjustments"
    ADD CONSTRAINT "fk_schedule_adjustments_schedule" FOREIGN KEY ("schedule_id") REFERENCES "bills"."b_payment_schedules"("schedule_id");


--
-- Name: b_transactions fk_transactions_category; Type: FK CONSTRAINT; Schema: bills; Owner: -
--

ALTER TABLE ONLY "bills"."b_transactions"
    ADD CONSTRAINT "fk_transactions_category" FOREIGN KEY ("category_id") REFERENCES "bills"."b_categories"("category_id");


--
-- PostgreSQL database dump complete
--


