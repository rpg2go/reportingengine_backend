--liquibase formatted sql
--changeset marius-druga:20260719-001-drop-period-type endDelimiter:;

-- 1. Update YTD offsets from months to years
UPDATE reporting.rpt_column_def
SET period_offset = period_offset / 12
WHERE col_type = 'YTD';

-- 2. Update ROLLING offsets from period_type
UPDATE reporting.rpt_column_def
SET period_offset = CASE 
    WHEN period_type = 'PREVIOUS_YEAR' THEN -1 
    ELSE 0 
END
WHERE col_type = 'ROLLING' AND period_type IS NOT NULL;

-- 3. Drop period_type column
ALTER TABLE reporting.rpt_column_def
    DROP COLUMN IF EXISTS period_type;
