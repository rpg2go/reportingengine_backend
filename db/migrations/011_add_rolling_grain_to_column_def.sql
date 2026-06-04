-- =============================================================================
-- Migration 011: Add rolling_grain to rpt_column_def
-- Schema: reporting
-- Description: Adds rolling_grain column to support flexible rolling time window grains (DAY, WEEK, MONTH).
-- Date: 2026-06-04
-- =============================================================================

ALTER TABLE reporting.rpt_column_def 
ADD COLUMN rolling_grain VARCHAR(10) 
CHECK (rolling_grain IN ('DAY', 'WEEK', 'MONTH'));
