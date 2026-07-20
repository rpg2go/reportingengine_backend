--liquibase formatted sql
--changeset devops:002_create_catalog_tables endDelimiter:;

CREATE SCHEMA IF NOT EXISTS catalog;

-- -----------------------------------------------------------------------------
-- catalog.meta_table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.meta_table (
    table_id      SERIAL PRIMARY KEY,
    schema_name   VARCHAR(63) NOT NULL DEFAULT 'analytics',
    table_name    VARCHAR(128) NOT NULL,
    label         VARCHAR(256),
    table_type    VARCHAR(20) NOT NULL CHECK (table_type IN ('fact', 'dimension', 'bridge')),
    time_key      VARCHAR(128),
    is_cached     BOOLEAN NOT NULL DEFAULT TRUE,
    description   TEXT,
    CONSTRAINT uq_meta_table UNIQUE (schema_name, table_name)
);

-- -----------------------------------------------------------------------------
-- catalog.meta_column
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.meta_column (
    column_id      SERIAL PRIMARY KEY,
    table_id       INTEGER NOT NULL REFERENCES catalog.meta_table(table_id) ON DELETE CASCADE,
    column_name    VARCHAR(128) NOT NULL,
    label          VARCHAR(256),
    data_type      VARCHAR(64),
    is_primary_key BOOLEAN NOT NULL DEFAULT FALSE,
    is_foreign_key BOOLEAN NOT NULL DEFAULT FALSE,
    is_filterable  BOOLEAN NOT NULL DEFAULT FALSE,
    is_cached      BOOLEAN NOT NULL DEFAULT FALSE,
    is_visible     BOOLEAN NOT NULL DEFAULT TRUE,
    description    TEXT,
    CONSTRAINT uq_meta_column UNIQUE (table_id, column_name)
);

-- -----------------------------------------------------------------------------
-- catalog.meta_relationship
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS catalog.meta_relationship (
    relationship_id  SERIAL PRIMARY KEY,
    from_table_id    INTEGER NOT NULL REFERENCES catalog.meta_table(table_id) ON DELETE CASCADE,
    from_column      VARCHAR(128) NOT NULL,
    to_table_id      INTEGER NOT NULL REFERENCES catalog.meta_table(table_id) ON DELETE CASCADE,
    to_column        VARCHAR(128) NOT NULL,
    join_type        VARCHAR(20) NOT NULL DEFAULT 'LEFT' CHECK (join_type IN ('LEFT', 'INNER', 'RIGHT')),
    is_conformed     BOOLEAN NOT NULL DEFAULT FALSE,
    weight           INTEGER NOT NULL DEFAULT 1,
    description      TEXT,
    CONSTRAINT uq_meta_relationship UNIQUE (from_table_id, from_column, to_table_id, to_column)
);
