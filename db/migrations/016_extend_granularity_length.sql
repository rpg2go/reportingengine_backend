-- Migration 016: Extend granularity column length in reporting.rpt_report
-- Schema: reporting

BEGIN;

ALTER TABLE reporting.rpt_report ALTER COLUMN granularity TYPE VARCHAR(1000);

COMMIT;
