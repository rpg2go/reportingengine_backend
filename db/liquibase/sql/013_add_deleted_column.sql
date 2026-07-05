-- liquibase formatted sql
-- changeset author:add_deleted_column

ALTER TABLE reporting.rpt_report ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
