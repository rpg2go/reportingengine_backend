-- Migration 021: Add YEAR to rolling_grain check constraint
-- Schema: reporting
-- Description: Alters the rolling_grain check constraint on rpt_column_def to allow 'YEAR'.
-- Date: 2026-06-30

ALTER TABLE reporting.rpt_column_def
DROP CONSTRAINT IF EXISTS rpt_column_def_rolling_grain_check;

ALTER TABLE reporting.rpt_column_def
ADD CONSTRAINT rpt_column_def_rolling_grain_check
CHECK (rolling_grain IN ('DAY', 'WEEK', 'MONTH', 'YEAR'));
