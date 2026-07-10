--liquibase formatted sql
--changeset devops:015_add_column_value_caching endDelimiter:;

-- 1. Add is_cached column to meta_column (defaulting to FALSE)
ALTER TABLE reporting.meta_column ADD COLUMN IF NOT EXISTS is_cached BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Populate is_cached column
UPDATE reporting.meta_column
SET is_cached = TRUE
WHERE is_filterable = TRUE; 

UPDATE reporting.meta_table
SET is_cached = TRUE
WHERE table_id in (select table_id from reporting.meta_column where is_cached = true group by table_id);