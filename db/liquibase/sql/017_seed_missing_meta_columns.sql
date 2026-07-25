--liquibase formatted sql
--changeset devops:017_seed_missing_meta_columns runOnChange:true endDelimiter:;

INSERT INTO catalog_owner.meta_column (
    table_id,
    column_name,
    label,
    data_type,
    is_primary_key,
    is_foreign_key,
    is_filterable,
    is_cached,
    is_visible,
    description
)
SELECT 
    mt.table_id,
    c.column_name,
    INITCAP(REPLACE(c.column_name, '_', ' ')) AS label,
    CASE 
        WHEN c.data_type = 'character varying' THEN 'varchar'
        WHEN c.data_type = 'integer' THEN 'integer'
        WHEN c.data_type = 'numeric' THEN 'numeric'
        WHEN c.data_type = 'double precision' THEN 'numeric'
        ELSE c.data_type
    END AS data_type,
    EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints tc 
        JOIN information_schema.key_column_usage kcu 
          ON tc.constraint_name = kcu.constraint_name 
         AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'PRIMARY KEY' 
          AND tc.table_schema = c.table_schema 
          AND tc.table_name = c.table_name 
          AND kcu.column_name = c.column_name
    ) AS is_primary_key,
    EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints tc 
        JOIN information_schema.key_column_usage kcu 
          ON tc.constraint_name = kcu.constraint_name 
         AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY' 
          AND tc.table_schema = c.table_schema 
          AND tc.table_name = c.table_name 
          AND kcu.column_name = c.column_name
    ) AS is_foreign_key,
    (
        c.data_type IN ('character varying', 'varchar', 'text', 'boolean')
        AND c.column_name NOT LIKE '%_id' 
        AND c.column_name <> 'id'
    ) AS is_filterable,
    (
        c.data_type IN ('character varying', 'varchar', 'text', 'boolean')
        AND c.column_name NOT LIKE '%_id' 
        AND c.column_name <> 'id'
    ) AS is_cached,
    TRUE AS is_visible,
    'Physical column [' || c.column_name || '] of analytical table [' || c.table_name || '].' AS description
FROM information_schema.columns c
JOIN catalog_owner.meta_table mt 
    ON mt.schema_name = c.table_schema 
   AND mt.table_name = c.table_name
LEFT JOIN catalog_owner.meta_column mc 
    ON mc.table_id = mt.table_id 
   AND mc.column_name = c.column_name
WHERE c.table_schema = 'analytics'
  AND mc.column_id IS NULL
ON CONFLICT (table_id, column_name) DO NOTHING;
