-- =============================================================================
-- Migration 002: Create Reporting Layer Tables
-- Schema: reporting
-- Description: Stores Excel-based report definitions in normalized form.
--              These tables mirror the structure of hybrid_reporting_template.xlsx.
--              The Excel parser (Phase B2) will populate these tables at import time.
-- Date: 2026-04-05
-- Depends on: 001_create_semantic_tables.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- rpt_report: Report header / registry
-- One row per report_id (matches the report_id column in the Excel sheet)
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_report (
    report_id   VARCHAR(50) PRIMARY KEY,    -- e.g. "RPT_001", matches Excel report_id
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    explore_id  INTEGER REFERENCES reporting.sem_explore(explore_id),  -- default explore
    version     INTEGER NOT NULL DEFAULT 1,
    status      VARCHAR(20) NOT NULL DEFAULT 'draft'  -- 'draft' | 'published'
        CHECK (status IN ('draft', 'published')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- rpt_column_def: Section A of the Excel template — time-based column definitions
-- Defines C1..C7 columns with their type (WEEK, MTD, YTD, ROLLING, CALC)
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_column_def (
    column_def_id  SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL REFERENCES reporting.rpt_report(report_id) ON DELETE CASCADE,
    col_id         VARCHAR(10) NOT NULL,    -- C1, C2, ... C7
    label          VARCHAR(200),            -- e.g. "Current Week", "MTD", "WoW %"
    col_type       VARCHAR(20) NOT NULL     -- WEEK | MTD | YTD | ROLLING | CALC
        CHECK (col_type IN ('WEEK', 'MTD', 'YTD', 'ROLLING', 'CALC')),
    period_offset  INTEGER DEFAULT 0,       -- 0=current period, -1=prior, -2=2 periods ago
    rolling_n      INTEGER,                 -- number of periods for ROLLING type
    formula_expr   TEXT,                    -- for CALC columns e.g. "(C1-C2)/C2"
    display_order  INTEGER NOT NULL,        -- left-to-right rendering order
    UNIQUE (report_id, col_id)
);

-- -----------------------------------------------------------------------------
-- rpt_style: Style definitions for rows (font, bold, borders, alignment)
-- Must be created BEFORE rpt_row because rpt_row.style_id references it.
-- Pre-populated with standard styles: header, section, normal, total, blank
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_style (
    style_id      SERIAL PRIMARY KEY,
    name          VARCHAR(50) NOT NULL UNIQUE,  -- header | section | normal | total | blank
    font_size     INTEGER DEFAULT 11,
    is_bold       BOOLEAN DEFAULT FALSE,
    border_top    BOOLEAN DEFAULT FALSE,
    border_bottom BOOLEAN DEFAULT FALSE,
    alignment     VARCHAR(10) DEFAULT 'left'    -- left | center | right
        CHECK (alignment IN ('left', 'center', 'right')),
    color_hex     VARCHAR(7),                   -- font color e.g. "#2C3E50"
    bg_color_hex  VARCHAR(7)                    -- background color e.g. "#F0F4F8"
);

-- -----------------------------------------------------------------------------
-- rpt_row: Section B of the Excel template — report body rows
-- Self-referencing hierarchy via parent_row_id.
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_row (
    row_id        VARCHAR(50) NOT NULL,     -- e.g. "R1", "R2"  (unique within report)
    report_id     VARCHAR(50) NOT NULL REFERENCES reporting.rpt_report(report_id) ON DELETE CASCADE,
    parent_row_id VARCHAR(50),              -- NULL = root row (no parent)
    label         VARCHAR(300) NOT NULL,    -- display text shown in the report
    row_type      VARCHAR(20) NOT NULL      -- section | data | calc | blank
        CHECK (row_type IN ('section', 'data', 'calc', 'blank')),
    display_order INTEGER NOT NULL,         -- top-to-bottom rendering order (1, 2, 3...)
    indent_level  INTEGER NOT NULL DEFAULT 0,  -- 0=flush left, 1=one level in, etc.
    style_id      INTEGER REFERENCES reporting.rpt_style(style_id),
    filter_expr   TEXT,
    PRIMARY KEY   (report_id, row_id),
    FOREIGN KEY   (report_id, parent_row_id) REFERENCES reporting.rpt_row(report_id, row_id)
);

-- -----------------------------------------------------------------------------
-- rpt_row_metric: Links a 'data' row to a semantic measure
-- Only populated for rows where row_type = 'data'
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_row_metric (
    row_metric_id SERIAL PRIMARY KEY,
    report_id     VARCHAR(50) NOT NULL,
    row_id        VARCHAR(50) NOT NULL,
    measure_id    INTEGER NOT NULL REFERENCES reporting.sem_measure(measure_id),
    explore_id    INTEGER REFERENCES reporting.sem_explore(explore_id),  -- override default explore
    FOREIGN KEY   (report_id, row_id) REFERENCES reporting.rpt_row(report_id, row_id) ON DELETE CASCADE,
    UNIQUE (report_id, row_id)   -- one metric source per row
);

-- -----------------------------------------------------------------------------
-- rpt_row_formula: Stores formula expression for 'calc' rows
-- Only populated for rows where row_type = 'calc'
-- e.g. formula_expr = "R2 - R3" or "R5 / R6"
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_row_formula (
    row_formula_id SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL,
    row_id         VARCHAR(50) NOT NULL,
    formula_expr   TEXT NOT NULL,
    FOREIGN KEY    (report_id, row_id) REFERENCES reporting.rpt_row(report_id, row_id) ON DELETE CASCADE,
    UNIQUE (report_id, row_id)   -- one formula per calc row
);

-- -----------------------------------------------------------------------------
-- rpt_row_column_map: Which columns (C1..C7) are active for each row
-- Represents the "X" flags in the Excel template columns C1..C7
-- -----------------------------------------------------------------------------
CREATE TABLE reporting.rpt_row_column_map (
    mapping_id  SERIAL PRIMARY KEY,
    report_id   VARCHAR(50) NOT NULL,
    row_id      VARCHAR(50) NOT NULL,
    col_id      VARCHAR(10) NOT NULL,
    is_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (report_id, row_id) REFERENCES reporting.rpt_row(report_id, row_id) ON DELETE CASCADE,
    FOREIGN KEY (report_id, col_id) REFERENCES reporting.rpt_column_def(report_id, col_id) ON DELETE CASCADE,
    UNIQUE (report_id, row_id, col_id)
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX idx_rpt_report_status     ON reporting.rpt_report(status);
CREATE INDEX idx_rpt_col_report_order  ON reporting.rpt_column_def(report_id, display_order);
CREATE INDEX idx_rpt_row_report_order  ON reporting.rpt_row(report_id, display_order);
CREATE INDEX idx_rpt_row_type          ON reporting.rpt_row(report_id, row_type);
CREATE INDEX idx_rpt_row_metric        ON reporting.rpt_row_metric(report_id, row_id);
CREATE INDEX idx_rpt_row_formula       ON reporting.rpt_row_formula(report_id, row_id);
CREATE INDEX idx_rpt_col_map           ON reporting.rpt_row_column_map(report_id, row_id);

-- Note: rpt_row references rpt_style, so rpt_style must exist first.
-- The FK constraint on rpt_row.style_id is satisfied because rpt_style
-- is created before rpt_row in this same migration file.
