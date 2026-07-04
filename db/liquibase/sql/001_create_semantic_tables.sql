--liquibase formatted sql
--changeset devops:001_create_semantic_tables endDelimiter:;

CREATE SCHEMA IF NOT EXISTS reporting;

-- -----------------------------------------------------------------------------
-- sem_view: one row per logical "view" mapping to a physical table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.sem_view (
    view_id       SERIAL        PRIMARY KEY,
    name          VARCHAR(128)  NOT NULL UNIQUE,
    label         VARCHAR(256),
    table_ref     VARCHAR(256),               -- e.g. 'analytics.fact_sales'
    view_type     VARCHAR(20)   CHECK (view_type IN ('fact', 'dimension', 'bridge', 'derived')),
    primary_key   VARCHAR(128),
    time_key      VARCHAR(128),               -- column used for date filtering
    description   TEXT
);

-- -----------------------------------------------------------------------------
-- sem_explore: one row per logical "explore" (a fact view + its join graph)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.sem_explore (
    explore_id    SERIAL        PRIMARY KEY,
    name          VARCHAR(128)  NOT NULL UNIQUE,
    label         VARCHAR(256),
    fact_view_id  INTEGER       NOT NULL REFERENCES reporting.sem_view(view_id) ON DELETE CASCADE,
    sql_always_where TEXT
);

-- -----------------------------------------------------------------------------
-- sem_join: dimension views joined to an explore
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.sem_join (
    join_id       SERIAL        PRIMARY KEY,
    explore_id    INTEGER       NOT NULL REFERENCES reporting.sem_explore(explore_id) ON DELETE CASCADE,
    from_view_id  INTEGER       NOT NULL REFERENCES reporting.sem_view(view_id)    ON DELETE CASCADE,
    to_view_id    INTEGER       NOT NULL REFERENCES reporting.sem_view(view_id)    ON DELETE CASCADE,
    join_type     VARCHAR(20)   NOT NULL DEFAULT 'LEFT' CHECK (join_type IN ('LEFT', 'INNER', 'RIGHT')),
    join_sql      TEXT          NOT NULL,     -- raw SQL ON clause
    CONSTRAINT uq_sem_join UNIQUE (explore_id, from_view_id, to_view_id)
);

-- -----------------------------------------------------------------------------
-- sem_dimension: filterable/groupable columns exposed through a view
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.sem_dimension (
    dimension_id  SERIAL        PRIMARY KEY,
    view_id       INTEGER       NOT NULL REFERENCES reporting.sem_view(view_id) ON DELETE CASCADE,
    name          VARCHAR(128)  NOT NULL,
    label         VARCHAR(256),
    column_ref    VARCHAR(256),               -- physical column expression
    data_type     VARCHAR(64),
    description   TEXT,
    CONSTRAINT uq_sem_dimension UNIQUE (view_id, name)
);

-- -----------------------------------------------------------------------------
-- sem_measure: aggregation expressions exposed through a view
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.sem_measure (
    measure_id    SERIAL        PRIMARY KEY,
    view_id       INTEGER       NOT NULL REFERENCES reporting.sem_view(view_id) ON DELETE CASCADE,
    name          VARCHAR(128)  NOT NULL,
    label         VARCHAR(256),
    sql_expr      TEXT          NOT NULL,     -- e.g. SUM(${TABLE}.amount)
    agg_type      VARCHAR(32),               -- SUM / COUNT / AVG / etc.
    data_type     VARCHAR(64),
    description   TEXT,
    CONSTRAINT uq_sem_measure UNIQUE (view_id, name)
);
