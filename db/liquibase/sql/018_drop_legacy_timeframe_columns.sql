--liquibase formatted sql
--changeset marius-druga:20260712-004-drop-legacy-timeframe endDelimiter:;

-- Drop legacy timeframe columns from the report definition table
ALTER TABLE reporting.rpt_report 
    DROP COLUMN IF EXISTS timeframe_start,
    DROP COLUMN IF EXISTS timeframe_end,
    DROP COLUMN IF EXISTS timeframe_today;
