-- =============================================================================
-- Programmatic Metadata Delta Patch Generation Script (generate_meta_delta.sql)
--
-- Objective:
-- Automatically insert missing columns from all registered analytics tables (facts & dimensions)
-- into reporting.meta_column to resolve catalog synchronization bugs.
-- =============================================================================

INSERT INTO reporting.meta_column (
    table_id,
    column_name,
    label,
    data_type,
    is_primary_key,
    is_foreign_key,
    is_conformed,
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
        ELSE c.data_type
    END AS data_type,
    -- 1. Check if column is a primary key via system constraints
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
    -- 2. Check if column is a foreign key via system constraints
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
    -- 3. Conformed check for conformed foreign keys/attributes
    (c.column_name IN ('customer_id', 'location_id', 'rm_id', 'country_code')) AS is_conformed,
    -- 4. Filterable check (strings/booleans that are not PKs/FKs)
    (
        c.data_type IN ('character varying', 'varchar', 'text', 'boolean')
        AND c.column_name NOT LIKE '%_id' 
        AND c.column_name <> 'id'
    ) AS is_filterable,
    -- 5. Caching check
    (
        c.data_type IN ('character varying', 'varchar', 'text', 'boolean')
        AND c.column_name NOT LIKE '%_id' 
        AND c.column_name <> 'id'
    ) AS is_cached,
    -- 6. Visibility check (hidden primary keys, visible others)
    NOT EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints tc 
        JOIN information_schema.key_column_usage kcu 
          ON tc.constraint_name = kcu.constraint_name 
         AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'PRIMARY KEY' 
          AND tc.table_schema = c.table_schema 
          AND tc.table_name = c.table_name 
          AND kcu.column_name = c.column_name
    ) AS is_visible,
    'Physical column [' || c.column_name || '] of analytical table [' || c.table_name || '].' AS description
FROM information_schema.columns c
JOIN reporting.meta_table mt 
    ON mt.schema_name = c.table_schema 
   AND mt.table_name = c.table_name
LEFT JOIN reporting.meta_column mc 
    ON mc.table_id = mt.table_id 
   AND mc.column_name = c.column_name
WHERE c.table_schema = 'analytics'
  AND mc.column_id IS NULL
ON CONFLICT (table_id, column_name) DO NOTHING;
