-- Migration 014: Alter Reporting Tables for Composite Key (report_id, version) and IN_REVIEW status
-- Schema: reporting

BEGIN;

-- 1. Drop existing constraints referencing rpt_report
ALTER TABLE reporting.rpt_column_def DROP CONSTRAINT IF EXISTS rpt_column_def_report_id_fkey;
ALTER TABLE reporting.rpt_row DROP CONSTRAINT IF EXISTS rpt_row_report_id_fkey;
ALTER TABLE reporting.rpt_row DROP CONSTRAINT IF EXISTS rpt_row_parent_row_id_fkey;
ALTER TABLE reporting.rpt_row_metric DROP CONSTRAINT IF EXISTS rpt_row_metric_report_id_row_id_fkey;
ALTER TABLE reporting.rpt_row_formula DROP CONSTRAINT IF EXISTS rpt_row_formula_report_id_row_id_fkey;
ALTER TABLE reporting.rpt_row_column_map DROP CONSTRAINT IF EXISTS rpt_row_column_map_report_id_row_id_fkey;

-- 2. Drop rpt_report primary key and status check
ALTER TABLE reporting.rpt_report DROP CONSTRAINT IF EXISTS rpt_report_pkey CASCADE;
ALTER TABLE reporting.rpt_report ADD PRIMARY KEY (report_id, version);

-- Update status check constraint
ALTER TABLE reporting.rpt_report DROP CONSTRAINT IF EXISTS rpt_report_status_check;
ALTER TABLE reporting.rpt_report ADD CONSTRAINT rpt_report_status_check CHECK (status::text = ANY (ARRAY['draft'::text, 'in_review'::text, 'published'::text]));

-- 3. Add version columns to child tables if not exist
ALTER TABLE reporting.rpt_column_def ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE reporting.rpt_row ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE reporting.rpt_row_metric ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE reporting.rpt_row_formula ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE reporting.rpt_row_column_map ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- 4. Sync version values from parent report
UPDATE reporting.rpt_column_def cd SET version = r.version FROM reporting.rpt_report r WHERE cd.report_id = r.report_id;
UPDATE reporting.rpt_row rw SET version = r.version FROM reporting.rpt_report r WHERE rw.report_id = r.report_id;
UPDATE reporting.rpt_row_metric rm SET version = r.version FROM reporting.rpt_report r WHERE rm.report_id = r.report_id;
UPDATE reporting.rpt_row_formula rf SET version = r.version FROM reporting.rpt_report r WHERE rf.report_id = r.report_id;
UPDATE reporting.rpt_row_column_map rc SET version = r.version FROM reporting.rpt_report r WHERE rc.report_id = r.report_id;

-- 5. Re-add constraints with composite keys
ALTER TABLE reporting.rpt_column_def DROP CONSTRAINT IF EXISTS rpt_column_def_report_fk;
ALTER TABLE reporting.rpt_column_def ADD CONSTRAINT rpt_column_def_report_fk FOREIGN KEY (report_id, version) REFERENCES reporting.rpt_report(report_id, version) ON DELETE CASCADE;

ALTER TABLE reporting.rpt_row DROP CONSTRAINT IF EXISTS rpt_row_pkey CASCADE;
ALTER TABLE reporting.rpt_row ADD PRIMARY KEY (report_id, version, row_id);

ALTER TABLE reporting.rpt_row DROP CONSTRAINT IF EXISTS rpt_row_report_fk;
ALTER TABLE reporting.rpt_row ADD CONSTRAINT rpt_row_report_fk FOREIGN KEY (report_id, version) REFERENCES reporting.rpt_report(report_id, version) ON DELETE CASCADE;

ALTER TABLE reporting.rpt_row DROP CONSTRAINT IF EXISTS rpt_row_parent_row_fk;
ALTER TABLE reporting.rpt_row ADD CONSTRAINT rpt_row_parent_row_fk FOREIGN KEY (report_id, version, parent_row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE SET NULL;

ALTER TABLE reporting.rpt_row_metric DROP CONSTRAINT IF EXISTS rpt_row_metric_row_fk CASCADE;
ALTER TABLE reporting.rpt_row_metric DROP CONSTRAINT IF EXISTS rpt_row_metric_unique CASCADE;
ALTER TABLE reporting.rpt_row_metric ADD CONSTRAINT rpt_row_metric_row_fk FOREIGN KEY (report_id, version, row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE;
ALTER TABLE reporting.rpt_row_metric ADD CONSTRAINT rpt_row_metric_unique UNIQUE (report_id, version, row_id);

ALTER TABLE reporting.rpt_row_formula DROP CONSTRAINT IF EXISTS rpt_row_formula_row_fk CASCADE;
ALTER TABLE reporting.rpt_row_formula ADD CONSTRAINT rpt_row_formula_row_fk FOREIGN KEY (report_id, version, row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE;

ALTER TABLE reporting.rpt_row_column_map DROP CONSTRAINT IF EXISTS rpt_row_column_map_row_fk CASCADE;
ALTER TABLE reporting.rpt_row_column_map ADD CONSTRAINT rpt_row_column_map_row_fk FOREIGN KEY (report_id, version, row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE;

COMMIT;
