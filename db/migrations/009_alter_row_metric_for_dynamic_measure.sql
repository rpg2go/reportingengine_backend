-- =============================================================================
-- Migration 009: Alter Row Metric for Dynamic Measure Definition
-- Schema: reporting
-- Description: Alters sql_expr to TEXT and adds measure_definition TEXT to rpt_row_metric.
-- Date: 2026-05-30
-- =============================================================================

-- 1. Alter existing columns in reporting.rpt_row_metric
ALTER TABLE reporting.rpt_row_metric ALTER COLUMN sql_expr TYPE TEXT;
ALTER TABLE reporting.rpt_row_metric ADD COLUMN IF NOT EXISTS measure_definition TEXT;
