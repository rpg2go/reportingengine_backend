-- =============================================================================
-- PostgreSQL Schema Discrepancy Discovery Query (audit_missing_metadata.sql)
--
-- Objective:
-- Inspect information_schema.columns against reporting.meta_column to identify
-- physical columns in analytics tables (facts & dimensions) that have no registered metadata.
-- =============================================================================

SELECT 
    c.table_schema,
    c.table_name,
    c.column_name,
    c.data_type,
    mt.table_id AS registered_table_id,
    mt.table_type
FROM information_schema.columns c
JOIN reporting.meta_table mt 
    ON mt.schema_name = c.table_schema 
   AND mt.table_name = c.table_name
LEFT JOIN reporting.meta_column mc 
    ON mc.table_id = mt.table_id 
   AND mc.column_name = c.column_name
WHERE mc.column_id IS NULL
ORDER BY c.table_name, c.ordinal_position;
