-- Migration: Rename col_type 'WEEK' to 'WTD' in rpt_column_def, drop/recreate check constraint, and add period_type column

-- 1. Drop existing check constraint first to allow updating values without violation
ALTER TABLE reporting.rpt_column_def DROP CONSTRAINT IF EXISTS rpt_column_def_col_type_check;

-- 2. Update existing column definition records from 'WEEK' to 'WTD'
UPDATE reporting.rpt_column_def SET col_type = 'WTD' WHERE col_type = 'WEEK';

-- 3. Recreate the check constraint to include 'WTD' and drop 'WEEK'
ALTER TABLE reporting.rpt_column_def ADD CONSTRAINT rpt_column_def_col_type_check CHECK (col_type IN ('WTD', 'MTD', 'YTD', 'ROLLING', 'CALC', 'HEADER'));

-- 4. Add the new period_type column to support calculated values filter rules
ALTER TABLE reporting.rpt_column_def ADD COLUMN period_type VARCHAR(50) DEFAULT NULL;
