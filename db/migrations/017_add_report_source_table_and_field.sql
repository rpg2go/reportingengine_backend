-- =============================================================================
-- Migration 017: Add Report Source Table and Field Columns
-- Schema: reporting
-- Description: Adds source_table and source_field columns to rpt_report.
-- =============================================================================

ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS source_table VARCHAR(150);
ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS source_field VARCHAR(150);
