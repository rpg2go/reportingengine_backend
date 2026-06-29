-- Migration: Add HEADER column type constraint validation
-- Drops the old col_type check constraint and adds the new one including 'HEADER'

ALTER TABLE reporting.rpt_column_def DROP CONSTRAINT IF EXISTS rpt_column_def_col_type_check;
ALTER TABLE reporting.rpt_column_def ADD CONSTRAINT rpt_column_def_col_type_check CHECK (col_type IN ('WEEK', 'MTD', 'YTD', 'ROLLING', 'CALC', 'HEADER'));
