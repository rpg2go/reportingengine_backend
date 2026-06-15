-- Migration 015: Refactor Unique and Foreign Key Constraints for versioning
-- Schema: reporting

BEGIN;

-- 1. Drop constraints on reporting.rpt_row_column_map that depend on column_def or old columns
ALTER TABLE reporting.rpt_row_column_map DROP CONSTRAINT IF EXISTS rpt_row_column_map_report_id_col_id_fkey;
ALTER TABLE reporting.rpt_row_column_map DROP CONSTRAINT IF EXISTS rpt_row_column_map_report_id_row_id_col_id_key;

-- 2. Drop constraints on reporting.rpt_column_def
ALTER TABLE reporting.rpt_column_def DROP CONSTRAINT IF EXISTS rpt_column_def_report_id_col_id_key;

-- 3. Add new composite unique constraint to reporting.rpt_column_def including version
ALTER TABLE reporting.rpt_column_def ADD CONSTRAINT rpt_column_def_report_id_version_col_id_key UNIQUE (report_id, version, col_id);

-- 4. Add new constraints on reporting.rpt_row_column_map including version
ALTER TABLE reporting.rpt_row_column_map ADD CONSTRAINT rpt_row_column_map_col_fk FOREIGN KEY (report_id, version, col_id) REFERENCES reporting.rpt_column_def (report_id, version, col_id) ON DELETE CASCADE;
ALTER TABLE reporting.rpt_row_column_map ADD CONSTRAINT rpt_row_column_map_report_version_row_col_key UNIQUE (report_id, version, row_id, col_id);

-- 5. Drop and recreate constraints on reporting.rpt_row_formula including version
ALTER TABLE reporting.rpt_row_formula DROP CONSTRAINT IF EXISTS rpt_row_formula_report_id_row_id_key;
ALTER TABLE reporting.rpt_row_formula ADD CONSTRAINT rpt_row_formula_report_version_row_key UNIQUE (report_id, version, row_id);

-- 6. Drop constraints on reporting.rpt_row_metric including version
ALTER TABLE reporting.rpt_row_metric DROP CONSTRAINT IF EXISTS rpt_row_metric_report_id_row_id_key;

-- 7. Drop and recreate rpt_row self-referencing hierarchy constraint with ON DELETE CASCADE
ALTER TABLE reporting.rpt_row DROP CONSTRAINT IF EXISTS rpt_row_parent_row_fk;
ALTER TABLE reporting.rpt_row ADD CONSTRAINT rpt_row_parent_row_fk FOREIGN KEY (report_id, version, parent_row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE;

COMMIT;
