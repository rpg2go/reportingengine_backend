-- =============================================================================
-- Migration 003: Create Governance & Operational Tables
-- Schema: reporting
-- Description: Import run tracking, audit log, and time intelligence metadata
--              used by the SQL generator to build date-filtered CTEs.
-- Date: 2026-04-05
-- Depends on: 001_create_semantic_tables.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- import_run: Tracks every ingestion job (Excel import, YAML import)
-- Used to trace which version of a report/model was loaded from which source.
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.import_run (
    run_id      SERIAL PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL       -- 'excel' | 'yaml'
        CHECK (source_type IN ('excel', 'yaml')),
    source_path TEXT NOT NULL,             -- file path or URL
    report_id   VARCHAR(50),               -- populated if source_type = 'excel'
    model_name  VARCHAR(100),              -- populated if source_type = 'yaml'
    status      VARCHAR(20) NOT NULL DEFAULT 'pending'  -- 'pending' | 'success' | 'failed'
        CHECK (status IN ('pending', 'success', 'failed')),
    error_msg   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- -----------------------------------------------------------------------------
-- time_period_type: Canonical registry of period types
-- Drives the SQL generator's time-filter logic.
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.time_period_type (
    period_type VARCHAR(20) PRIMARY KEY,   -- WEEK | MTD | YTD | ROLLING | CALC
    label       VARCHAR(100) NOT NULL,
    description TEXT
);

-- -----------------------------------------------------------------------------
-- time_offset_rule: SQL generation templates per period type
-- :ref_date is the runtime parameter injected by the executor.
-- The where_clause template uses named parameter :ref_date (psycopg style).
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.time_offset_rule (
    rule_id       SERIAL PRIMARY KEY,
    period_type   VARCHAR(20) NOT NULL REFERENCES reporting.time_period_type(period_type),
    offset_value  INTEGER NOT NULL DEFAULT 0,  -- 0 = current, -1 = prior, etc.
    where_clause  TEXT NOT NULL,               -- parameterized SQL template
    label_suffix  VARCHAR(100),                -- human-readable label e.g. "Current Week"
    UNIQUE (period_type, offset_value)
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX idx_import_run_status      ON reporting.import_run(status);
CREATE INDEX idx_import_run_report      ON reporting.import_run(report_id);
CREATE INDEX idx_time_rule_period_type  ON reporting.time_offset_rule(period_type);
