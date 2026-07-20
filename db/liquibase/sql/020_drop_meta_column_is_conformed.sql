--liquibase formatted sql
--changeset marius-druga:20260720-001-drop-meta-column-is-conformed endDelimiter:;

-- Drop the redundant is_conformed column from meta_column
ALTER TABLE reporting.meta_column
    DROP COLUMN IF EXISTS is_conformed;
