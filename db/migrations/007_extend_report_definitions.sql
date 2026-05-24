-- =============================================================================
-- Migration 007: Extend Report Definitions for UI Builder (Bypass Semantic Layer)
-- Schema: reporting
-- Description: Adds source_table, granularity, timeframe, and report-level filters
--              to rpt_report. Adds sql_expr to rpt_row_metric and makes measure_id optional.
-- Date: 2026-05-24
-- =============================================================================

-- 1. Extend Report Header for source table, timeframe, and report-level filters
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS source_table VARCHAR(150);
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS granularity VARCHAR(100);
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS timeframe_start VARCHAR(50);
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS timeframe_end VARCHAR(50);
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS timeframe_today BOOLEAN DEFAULT FALSE;
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS quick_filters TEXT;
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS general_filters TEXT; -- Store general filters as JSON text representation

-- 2. Extend row metric to support direct SQL expressions instead of measure references
ALTER TABLE reporting.rpt_row_metric ADD COLUMN IF NOT EXISTS sql_expr VARCHAR(500);
ALTER TABLE reporting.rpt_row_metric ALTER COLUMN measure_id DROP NOT NULL;
