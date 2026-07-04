--liquibase formatted sql
--changeset devops:004_create_global_filters endDelimiter:;

CREATE SCHEMA IF NOT EXISTS reporting;

-- -----------------------------------------------------------------------------
-- rpt_global_filter: Stores cross-table filters in a recursive tree canvas
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_global_filter (
    filter_id        VARCHAR(50) PRIMARY KEY,                             -- unique UUID or string identifier
    report_id        VARCHAR(50) NOT NULL,
    version          INTEGER NOT NULL DEFAULT 1,
    parent_filter_id VARCHAR(50) REFERENCES reporting.rpt_global_filter(filter_id) ON DELETE CASCADE,
    filter_type      VARCHAR(20) NOT NULL                                 -- GROUP | CONDITION
        CHECK (filter_type IN ('GROUP', 'CONDITION')),
    logical_operator VARCHAR(10)                                          -- AND | OR (valid only for GROUP type)
        CHECK (logical_operator IN ('AND', 'OR')),
    target_table     VARCHAR(100),                                        -- data warehouse physical table name
    target_column    VARCHAR(100),                                        -- table column name
    operator         VARCHAR(20)                                          -- comparison operator (=, !=, >, IN, etc.)
        CHECK (operator IN ('=', '!=', '>', '<', '>=', '<=', 'IN', 'NOT_IN', 'LIKE', 'ILIKE', 'BETWEEN')),
    filter_value     TEXT,                                                -- serialized filter values (single value or JSON array)
    display_order    INTEGER NOT NULL,                                    -- sequence index within current tree level
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (report_id, version) REFERENCES reporting.rpt_report(report_id, version) ON DELETE CASCADE
);

-- Optimize routing queries by report_id
CREATE INDEX IF NOT EXISTS idx_rpt_global_filter_report 
ON reporting.rpt_global_filter (report_id);

-- Optimize tree traversal hierarchical lookup queries
CREATE INDEX IF NOT EXISTS idx_rpt_global_filter_parent 
ON reporting.rpt_global_filter (parent_filter_id);
