--liquibase formatted sql
--changeset marius-druga:20260712-003-polymorphic-time endDelimiter:;

-- Alter the report definition metadata table to hold both static and dynamic tokens
ALTER TABLE reporting.rpt_report 
    ADD COLUMN IF NOT EXISTS reporting_date_type VARCHAR(16) DEFAULT 'DYNAMIC', -- 'FIXED' or 'DYNAMIC'
    ADD COLUMN IF NOT EXISTS reporting_date_static DATE,
    ADD COLUMN IF NOT EXISTS reporting_date_expression VARCHAR(8) DEFAULT 'T-2',
    
    ADD COLUMN IF NOT EXISTS timeframe_start_type VARCHAR(16) DEFAULT 'FIXED',
    ADD COLUMN IF NOT EXISTS timeframe_start_static DATE DEFAULT '2022-01-01',
    ADD COLUMN IF NOT EXISTS timeframe_start_expression VARCHAR(8),
    
    ADD COLUMN IF NOT EXISTS timeframe_end_type VARCHAR(16) DEFAULT 'DYNAMIC',
    ADD COLUMN IF NOT EXISTS timeframe_end_static DATE,
    ADD COLUMN IF NOT EXISTS timeframe_end_expression VARCHAR(8) DEFAULT 'T-2';

-- Drop check constraints if they exist to support re-runnability/idempotency
ALTER TABLE reporting.rpt_report 
    DROP CONSTRAINT IF EXISTS chk_reporting_date_spec,
    DROP CONSTRAINT IF EXISTS chk_timeframe_end_spec;

-- Add check constraints to protect configuration integrity inside Cloud SQL
ALTER TABLE reporting.rpt_report 
    ADD CONSTRAINT chk_reporting_date_spec CHECK (reporting_date_type IN ('FIXED', 'DYNAMIC')),
    ADD CONSTRAINT chk_timeframe_end_spec CHECK (timeframe_end_type IN ('FIXED', 'DYNAMIC'));