-- =============================================================================
-- Migration 001: Create Semantic Layer Tables
-- Schema: reporting
-- Description: Stores the semantic model (LookML-equivalent) for the
--              reporting engine. The analytics.* tables ARE the data warehouse.
--              This schema stores the METADATA about that warehouse used
--              for dynamic SQL generation.
-- Date: 2026-04-05
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS reporting;

-- -----------------------------------------------------------------------------
-- sem_model: Top-level grouping (equivalent to a LookML model file)
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_model (
    model_id    SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    label       VARCHAR(200),
    description TEXT,
    version     INTEGER NOT NULL DEFAULT 1,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- sem_view: Physical tables / views in the analytics schema
-- Maps to: analytics.dim_*, analytics.fact_*
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_view (
    view_id     SERIAL PRIMARY KEY,
    model_id    INTEGER NOT NULL REFERENCES reporting.sem_model(model_id),
    name        VARCHAR(100) NOT NULL,          -- logical name e.g. "fact_sales"
    label       VARCHAR(200),
    table_ref   VARCHAR(300) NOT NULL,          -- e.g. "analytics.fact_sales"
    view_type   VARCHAR(20) NOT NULL DEFAULT 'dimension', -- 'fact' | 'dimension'
    primary_key VARCHAR(100),                   -- e.g. "id" or "date_key"
    time_key    VARCHAR(100),                   -- e.g. "reporting_date" (facts only)
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (model_id, name)
);

-- -----------------------------------------------------------------------------
-- sem_explore: Entry-point explores (one per fact table)
-- Defines which fact drives the query and its global filters.
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_explore (
    explore_id      SERIAL PRIMARY KEY,
    model_id        INTEGER NOT NULL REFERENCES reporting.sem_model(model_id),
    fact_view_id    INTEGER NOT NULL REFERENCES reporting.sem_view(view_id),
    name            VARCHAR(100) NOT NULL,
    label           VARCHAR(200),
    sql_always_where TEXT,   -- optional global WHERE clause e.g. "status <> 'DELETED'"
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (model_id, name)
);

-- -----------------------------------------------------------------------------
-- sem_dimension: Dimension attributes (columns usable for GROUP BY / WHERE)
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_dimension (
    dimension_id SERIAL PRIMARY KEY,
    view_id      INTEGER NOT NULL REFERENCES reporting.sem_view(view_id),
    name         VARCHAR(100) NOT NULL,      -- logical name e.g. "region"
    label        VARCHAR(200),
    column_ref   VARCHAR(200) NOT NULL,      -- physical column e.g. "region"
    data_type    VARCHAR(50),                -- string | integer | date | boolean | numeric
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (view_id, name)
);

-- -----------------------------------------------------------------------------
-- sem_measure: Aggregated metrics (columns usable for SELECT with aggregation)
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_measure (
    measure_id   SERIAL PRIMARY KEY,
    view_id      INTEGER NOT NULL REFERENCES reporting.sem_view(view_id),
    name         VARCHAR(100) NOT NULL,      -- logical name e.g. "total_revenue"
    label        VARCHAR(200),
    sql_expr     TEXT NOT NULL,              -- e.g. "SUM(analytics.fact_sales.amount)"
    agg_type     VARCHAR(20) NOT NULL,       -- SUM | COUNT | AVG | MAX | MIN | FORMULA
    data_type    VARCHAR(50),                -- currency | integer | percent | numeric
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (view_id, name)
);

-- -----------------------------------------------------------------------------
-- sem_join: Join relationships between an explore's fact view and dimensions
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_join (
    join_id      SERIAL PRIMARY KEY,
    explore_id   INTEGER NOT NULL REFERENCES reporting.sem_explore(explore_id),
    from_view_id INTEGER NOT NULL REFERENCES reporting.sem_view(view_id),  -- fact view
    to_view_id   INTEGER NOT NULL REFERENCES reporting.sem_view(view_id),  -- dimension view
    join_sql     TEXT NOT NULL,   -- e.g. "analytics.fact_sales.location_id = analytics.dim_location.id"
    join_type    VARCHAR(10) NOT NULL DEFAULT 'LEFT',  -- LEFT | INNER
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- sem_derived_metric: Cross-measure post-processor formulas
-- Evaluated in Python after SQL execution (not in SQL)
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.sem_derived_metric (
    derived_metric_id SERIAL PRIMARY KEY,
    model_id          INTEGER NOT NULL REFERENCES reporting.sem_model(model_id),
    name              VARCHAR(100) NOT NULL UNIQUE,
    label             VARCHAR(200),
    formula_expr      TEXT NOT NULL,   -- e.g. "total_cost / total_revenue"
    depends_on        JSONB NOT NULL,  -- e.g. ["total_cost", "total_revenue"]
    description       TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX idx_sem_view_model    ON reporting.sem_view(model_id);
CREATE INDEX idx_sem_view_type     ON reporting.sem_view(view_type);
CREATE INDEX idx_sem_explore_model ON reporting.sem_explore(model_id);
CREATE INDEX idx_sem_dim_view      ON reporting.sem_dimension(view_id);
CREATE INDEX idx_sem_measure_view  ON reporting.sem_measure(view_id);
CREATE INDEX idx_sem_join_explore  ON reporting.sem_join(explore_id);
CREATE INDEX idx_sem_join_to_view  ON reporting.sem_join(to_view_id);
