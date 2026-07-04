--liquibase formatted sql
--changeset devops:010_drop_sem_measure endDelimiter:;

-- Drop legacy sem_measure columns from rpt_row_metric (no actual FK constraints, just optional refs)
ALTER TABLE reporting.rpt_row_metric DROP COLUMN IF EXISTS measure_id;
ALTER TABLE reporting.rpt_row_metric DROP COLUMN IF EXISTS explore_id;

-- Drop sem_measure table (measures now live directly as sql_expr in rpt_row_metric)
DROP TABLE IF EXISTS reporting.sem_measure CASCADE;
