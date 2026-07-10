-- liquibase formatted sql
-- changeset marius:016_add_column_visibility

-- Add is_visible to reporting.meta_column table
ALTER TABLE reporting.meta_column ADD COLUMN IF NOT EXISTS is_visible BOOLEAN NOT NULL DEFAULT TRUE;

-- Set is_visible = false for all primary keys
UPDATE reporting.meta_column SET is_visible = FALSE WHERE is_primary_key = TRUE;
