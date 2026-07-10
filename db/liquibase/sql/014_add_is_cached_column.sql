--liquibase formatted sql
--changeset devops:014_add_is_cached_column endDelimiter:;

ALTER TABLE reporting.meta_table
ADD COLUMN IF NOT EXISTS is_cached BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE reporting.meta_table
SET is_cached = TRUE
WHERE schema_name='analytics' and table_name in ('dim_products','dim_location','dim_relationship_manager','dim_investment_hierarchy','dim_date','dim_countries');
