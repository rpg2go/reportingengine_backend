-- =============================================================================
-- Migration 010: Schema Catalog for Dynamic Graph-Based Join Routing
-- Purpose : Provides the relational metadata registry consumed by
--           SchemaCatalogLoader at startup to build an in-memory directed
--           graph of table→column→relationship edges.  SchemaGraphRouter
--           traverses this graph to resolve multi-hop LEFT JOIN paths at
--           query-generation time, eliminating every hardcoded join block.
-- Author  : Reporting Engine – Phase 3
-- Date    : 2026-06-04
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1.  meta_table  – one row per physical table in the data warehouse
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reporting.meta_table (
    table_id      SERIAL       PRIMARY KEY,
    schema_name   VARCHAR(63)  NOT NULL DEFAULT 'analytics',
    table_name    VARCHAR(128) NOT NULL,
    table_type    VARCHAR(20)  NOT NULL CHECK (table_type IN ('fact', 'dimension', 'bridge')),
    time_key      VARCHAR(128),          -- date/timestamp column used for period slicing
    description   TEXT,
    CONSTRAINT uq_meta_table UNIQUE (schema_name, table_name)
);

COMMENT ON TABLE  reporting.meta_table             IS 'Physical table registry – one row per DWH table known to the reporting engine.';
COMMENT ON COLUMN reporting.meta_table.table_type  IS 'fact | dimension | bridge';
COMMENT ON COLUMN reporting.meta_table.time_key    IS 'Column used for date-boundary filtering in generated CTEs.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2.  meta_column  – one row per column that the engine may reference
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reporting.meta_column (
    column_id      SERIAL       PRIMARY KEY,
    table_id       INTEGER      NOT NULL REFERENCES reporting.meta_table(table_id) ON DELETE CASCADE,
    column_name    VARCHAR(128) NOT NULL,
    data_type      VARCHAR(64),
    is_primary_key BOOLEAN      NOT NULL DEFAULT FALSE,
    is_foreign_key BOOLEAN      NOT NULL DEFAULT FALSE,
    is_conformed   BOOLEAN      NOT NULL DEFAULT FALSE,  -- TRUE for conformed dimension keys
    description    TEXT,
    CONSTRAINT uq_meta_column UNIQUE (table_id, column_name)
);

COMMENT ON TABLE  reporting.meta_column              IS 'Column-level metadata for every registered table.';
COMMENT ON COLUMN reporting.meta_column.is_conformed IS 'TRUE when this key is a conformed dimension link shared across multiple fact tables.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3.  meta_relationship  – directed FK edge: from_table.from_column → to_table.to_column
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reporting.meta_relationship (
    relationship_id  SERIAL      PRIMARY KEY,
    from_table_id    INTEGER     NOT NULL REFERENCES reporting.meta_table(table_id) ON DELETE CASCADE,
    from_column      VARCHAR(128) NOT NULL,
    to_table_id      INTEGER     NOT NULL REFERENCES reporting.meta_table(table_id) ON DELETE CASCADE,
    to_column        VARCHAR(128) NOT NULL,
    join_type        VARCHAR(20)  NOT NULL DEFAULT 'LEFT' CHECK (join_type IN ('LEFT', 'INNER', 'RIGHT')),
    is_conformed     BOOLEAN      NOT NULL DEFAULT FALSE,  -- TRUE when edge traverses a conformed key
    weight           INTEGER      NOT NULL DEFAULT 1,      -- Dijkstra edge cost; lower = preferred
    description      TEXT,
    CONSTRAINT uq_meta_relationship UNIQUE (from_table_id, from_column, to_table_id, to_column)
);

COMMENT ON TABLE  reporting.meta_relationship             IS 'Directed FK edges used by SchemaGraphRouter to compute multi-hop join paths.';
COMMENT ON COLUMN reporting.meta_relationship.is_conformed IS 'Conformed edges are preferred by the pathfinder to avoid fan-out.';
COMMENT ON COLUMN reporting.meta_relationship.weight       IS 'Dijkstra cost – conformed edges receive weight=1, non-conformed weight=2.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4.  Seed: meta_table
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO reporting.meta_table (schema_name, table_name, table_type, time_key, description) VALUES
  ('analytics', 'fact_sales',                  'fact',      'reporting_date', 'Sales transactions fact table'),
  ('analytics', 'fact_banking_transactions',   'fact',      'reporting_date', 'Banking ledger transactions fact table'),
  ('analytics', 'fact_loans',                  'fact',      'reporting_date', 'Loan portfolio fact table'),
  ('analytics', 'fact_investments',            'fact',      'reporting_date', 'Investment portfolio fact table'),
  ('analytics', 'fact_department_performance', 'fact',      'reporting_date', 'Department budget vs cost fact table'),
  ('analytics', 'dim_customers',               'dimension', NULL,             'Customer master dimension'),
  ('analytics', 'dim_accounts',                'dimension', NULL,             'Bank accounts dimension'),
  ('analytics', 'dim_products',                'dimension', NULL,             'Product catalog dimension'),
  ('analytics', 'dim_location',                'dimension', NULL,             'Geographic location dimension'),
  ('analytics', 'dim_relationship_manager',    'dimension', NULL,             'Relationship manager dimension'),
  ('analytics', 'dim_investment_hierarchy',    'dimension', NULL,             'Investment asset-class hierarchy dimension'),
  ('analytics', 'dim_date',                    'dimension', 'date_key',       'Date/calendar dimension'),
  ('analytics', 'dim_countries',               'dimension', NULL,             'Country master dimension')
ON CONFLICT (schema_name, table_name) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5.  Seed: meta_column  (key columns only – the router only needs FKs & PKs)
-- ─────────────────────────────────────────────────────────────────────────────

-- Helper: resolve table_id inline
-- fact_sales
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',              'integer', TRUE,  FALSE, FALSE),
           ('reporting_date',  'date',    FALSE, TRUE,  FALSE),
           ('product_id',      'integer', FALSE, TRUE,  FALSE),
           ('customer_id',     'integer', FALSE, TRUE,  TRUE ),
           ('location_id',     'integer', FALSE, TRUE,  TRUE ),
           ('rm_id',           'integer', FALSE, TRUE,  TRUE )
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'fact_sales'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- fact_banking_transactions
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',              'integer', TRUE,  FALSE, FALSE),
           ('reporting_date',  'date',    FALSE, TRUE,  FALSE),
           ('account_id',      'integer', FALSE, TRUE,  FALSE),
           ('location_id',     'integer', FALSE, TRUE,  TRUE ),
           ('rm_id',           'integer', FALSE, TRUE,  TRUE )
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'fact_banking_transactions'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- fact_loans
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',              'integer', TRUE,  FALSE, FALSE),
           ('customer_id',     'integer', FALSE, TRUE,  TRUE ),
           ('reporting_date',  'date',    FALSE, TRUE,  FALSE),
           ('location_id',     'integer', FALSE, TRUE,  TRUE ),
           ('rm_id',           'integer', FALSE, TRUE,  TRUE )
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'fact_loans'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- fact_investments
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',              'integer', TRUE,  FALSE, FALSE),
           ('customer_id',     'integer', FALSE, TRUE,  TRUE ),
           ('reporting_date',  'date',    FALSE, TRUE,  FALSE),
           ('hier_id',         'integer', FALSE, TRUE,  FALSE),
           ('location_id',     'integer', FALSE, TRUE,  TRUE ),
           ('rm_id',           'integer', FALSE, TRUE,  TRUE )
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'fact_investments'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- fact_department_performance
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',              'integer', TRUE,  FALSE, FALSE),
           ('reporting_date',  'date',    FALSE, TRUE,  FALSE),
           ('location_id',     'integer', FALSE, TRUE,  TRUE ),
           ('rm_id',           'integer', FALSE, TRUE,  TRUE )
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'fact_department_performance'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_customers
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',           'integer',      TRUE,  FALSE, TRUE ),
           ('country_code', 'varchar',      FALSE, FALSE, FALSE)
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_customers'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_accounts
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',          'integer', TRUE,  FALSE, FALSE),
           ('customer_id', 'integer', FALSE, TRUE,  TRUE )
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_accounts'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_products
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES ('id', 'integer', TRUE, FALSE, FALSE)) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_products'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_location
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES ('id', 'integer', TRUE, FALSE, TRUE)) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_location'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_relationship_manager
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES ('id', 'integer', TRUE, FALSE, TRUE)) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_relationship_manager'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_investment_hierarchy
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES ('id', 'integer', TRUE, FALSE, FALSE)) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_investment_hierarchy'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_date
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES ('date_key', 'date', TRUE, FALSE, TRUE)) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_date'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- dim_countries
INSERT INTO reporting.meta_column (table_id, column_name, data_type, is_primary_key, is_foreign_key, is_conformed)
SELECT t.table_id, v.column_name, v.data_type, v.is_pk, v.is_fk, v.is_conformed
FROM   reporting.meta_table t,
       (VALUES
           ('id',       'integer', TRUE,  FALSE, FALSE),
           ('iso_code', 'varchar', FALSE, FALSE, FALSE)
       ) AS v(column_name, data_type, is_pk, is_fk, is_conformed)
WHERE  t.schema_name = 'analytics' AND t.table_name = 'dim_countries'
ON CONFLICT (table_id, column_name) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6.  Seed: meta_relationship  (directed FK edges)
--     Each row models the JOIN predicate:
--       <from_schema.from_table>.<from_column> = <to_schema.to_table>.<to_column>
--     Conformed edges get weight=1; non-conformed get weight=2.
-- ─────────────────────────────────────────────────────────────────────────────

-- fact_sales → dim_date
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'reporting_date', t.table_id, 'date_key', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_sales'
AND    t.schema_name='analytics' AND t.table_name='dim_date'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_sales → dim_products
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'product_id', t.table_id, 'id', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_sales'
AND    t.schema_name='analytics' AND t.table_name='dim_products'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_sales → dim_customers  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'customer_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_sales'
AND    t.schema_name='analytics' AND t.table_name='dim_customers'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_sales → dim_location  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'location_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_sales'
AND    t.schema_name='analytics' AND t.table_name='dim_location'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_sales → dim_relationship_manager  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'rm_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_sales'
AND    t.schema_name='analytics' AND t.table_name='dim_relationship_manager'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_banking_transactions → dim_date
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'reporting_date', t.table_id, 'date_key', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_banking_transactions'
AND    t.schema_name='analytics' AND t.table_name='dim_date'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_banking_transactions → dim_accounts
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'account_id', t.table_id, 'id', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_banking_transactions'
AND    t.schema_name='analytics' AND t.table_name='dim_accounts'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_banking_transactions → dim_location  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'location_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_banking_transactions'
AND    t.schema_name='analytics' AND t.table_name='dim_location'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_banking_transactions → dim_relationship_manager  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'rm_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_banking_transactions'
AND    t.schema_name='analytics' AND t.table_name='dim_relationship_manager'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- dim_accounts → dim_customers  (conformed – the bridge hop for banking txns)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'customer_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='dim_accounts'
AND    t.schema_name='analytics' AND t.table_name='dim_customers'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- dim_customers → dim_countries  (via country_code = iso_code)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'country_code', t.table_id, 'iso_code', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='dim_customers'
AND    t.schema_name='analytics' AND t.table_name='dim_countries'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_loans → dim_customers  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'customer_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_loans'
AND    t.schema_name='analytics' AND t.table_name='dim_customers'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_loans → dim_date
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'reporting_date', t.table_id, 'date_key', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_loans'
AND    t.schema_name='analytics' AND t.table_name='dim_date'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_loans → dim_location  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'location_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_loans'
AND    t.schema_name='analytics' AND t.table_name='dim_location'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_loans → dim_relationship_manager  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'rm_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_loans'
AND    t.schema_name='analytics' AND t.table_name='dim_relationship_manager'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_investments → dim_customers  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'customer_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_investments'
AND    t.schema_name='analytics' AND t.table_name='dim_customers'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_investments → dim_date
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'reporting_date', t.table_id, 'date_key', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_investments'
AND    t.schema_name='analytics' AND t.table_name='dim_date'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_investments → dim_investment_hierarchy
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'hier_id', t.table_id, 'id', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_investments'
AND    t.schema_name='analytics' AND t.table_name='dim_investment_hierarchy'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_investments → dim_location  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'location_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_investments'
AND    t.schema_name='analytics' AND t.table_name='dim_location'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_investments → dim_relationship_manager  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'rm_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_investments'
AND    t.schema_name='analytics' AND t.table_name='dim_relationship_manager'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_department_performance → dim_date
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'reporting_date', t.table_id, 'date_key', 'LEFT', FALSE, 2
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_department_performance'
AND    t.schema_name='analytics' AND t.table_name='dim_date'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_department_performance → dim_location  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'location_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_department_performance'
AND    t.schema_name='analytics' AND t.table_name='dim_location'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;

-- fact_department_performance → dim_relationship_manager  (conformed)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, is_conformed, weight)
SELECT f.table_id, 'rm_id', t.table_id, 'id', 'LEFT', TRUE, 1
FROM   reporting.meta_table f, reporting.meta_table t
WHERE  f.schema_name='analytics' AND f.table_name='fact_department_performance'
AND    t.schema_name='analytics' AND t.table_name='dim_relationship_manager'
ON CONFLICT (from_table_id, from_column, to_table_id, to_column) DO NOTHING;
