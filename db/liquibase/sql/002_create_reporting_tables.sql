--liquibase formatted sql
--changeset devops:002_create_reporting_tables endDelimiter:;
--validCheckSum: *

CREATE SCHEMA IF NOT EXISTS reporting;

-- -----------------------------------------------------------------------------
-- rpt_report
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_report (
    report_id       VARCHAR(50) NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    report_name     VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'in_review', 'published')),
    source_table    VARCHAR(150),
    source_field    VARCHAR(150),
    granularity     VARCHAR(1000),
    timeframe_start VARCHAR(50),
    timeframe_end   VARCHAR(50),
    timeframe_today BOOLEAN DEFAULT FALSE,
    quick_filters   TEXT,
    general_filters TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (report_id, version)
);

-- -----------------------------------------------------------------------------
-- rpt_style
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_style (
    style_id      SERIAL PRIMARY KEY,
    name          VARCHAR(50) NOT NULL UNIQUE,                            -- header | section | normal | total | blank
    font_size     INTEGER DEFAULT 11,
    is_bold       BOOLEAN DEFAULT FALSE,
    border_top    BOOLEAN DEFAULT FALSE,
    border_bottom BOOLEAN DEFAULT FALSE,
    alignment     VARCHAR(10) DEFAULT 'left'                              -- left | center | right
        CHECK (alignment IN ('left', 'center', 'right')),
    color_hex     VARCHAR(7),
    bg_color_hex  VARCHAR(7)
);

-- -----------------------------------------------------------------------------
-- rpt_column_def
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_column_def (
    column_def_id  SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    col_id         VARCHAR(10) NOT NULL,                                  -- e.g. "C1", "C2"
    label          VARCHAR(200),
    col_type       VARCHAR(20) NOT NULL                                   -- WTD | MTD | YTD | ROLLING | CALC | HEADER
        CHECK (col_type IN ('WTD', 'MTD', 'YTD', 'ROLLING', 'CALC', 'HEADER')),
    period_offset  INTEGER DEFAULT 0,
    period_type    VARCHAR(50) DEFAULT NULL,
    rolling_n      INTEGER,
    rolling_grain  VARCHAR(10)
        CHECK (rolling_grain IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    formula_expr   TEXT,
    display_order  INTEGER NOT NULL,
    tier_level     VARCHAR(10) DEFAULT 'L1' NOT NULL                      -- horizontal tier ('L1', 'L2', etc.)
        CHECK (tier_level IN ('L1', 'L2', 'L3')),
    parent_id      VARCHAR(50) DEFAULT NULL,
    FOREIGN KEY (report_id, version) REFERENCES reporting.rpt_report(report_id, version) ON DELETE CASCADE,
    CONSTRAINT rpt_column_def_report_id_version_col_id_key UNIQUE (report_id, version, col_id)
);

-- -----------------------------------------------------------------------------
-- rpt_row
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_row (
    row_id        VARCHAR(50) NOT NULL,                                   -- unique row key (R1, R2, etc.)
    report_id     VARCHAR(50) NOT NULL,
    version       INTEGER NOT NULL DEFAULT 1,
    parent_row_id VARCHAR(50),
    label         VARCHAR(300) NOT NULL,
    row_type      VARCHAR(20) NOT NULL                                    -- section | data | calc | blank
        CHECK (row_type IN ('section', 'data', 'calc', 'blank')),
    display_order INTEGER NOT NULL,
    indent_level  INTEGER NOT NULL DEFAULT 0,
    style_id      INTEGER REFERENCES reporting.rpt_style(style_id),
    filter_expr   TEXT,
    PRIMARY KEY (report_id, version, row_id),
    FOREIGN KEY (report_id, version) REFERENCES reporting.rpt_report(report_id, version) ON DELETE CASCADE,
    CONSTRAINT rpt_row_parent_row_fk FOREIGN KEY (report_id, version, parent_row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);

-- -----------------------------------------------------------------------------
-- rpt_row_metric
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_row_metric (
    row_metric_id     SERIAL PRIMARY KEY,
    report_id         VARCHAR(50) NOT NULL,
    version           INTEGER NOT NULL DEFAULT 1,
    row_id            VARCHAR(50) NOT NULL,
    sql_expr          TEXT,
    measure_definition TEXT,
    FOREIGN KEY (report_id, version, row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE,
    CONSTRAINT rpt_row_metric_unique UNIQUE (report_id, version, row_id)
);

-- -----------------------------------------------------------------------------
-- rpt_row_formula
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_row_formula (
    row_formula_id SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    row_id         VARCHAR(50) NOT NULL,
    formula_expr   TEXT NOT NULL,
    FOREIGN KEY (report_id, version, row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE,
    CONSTRAINT rpt_row_formula_report_version_row_key UNIQUE (report_id, version, row_id)
);

-- -----------------------------------------------------------------------------
-- rpt_row_column_map
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.rpt_row_column_map (
    mapping_id  SERIAL PRIMARY KEY,
    report_id   VARCHAR(50) NOT NULL,
    version     INTEGER NOT NULL DEFAULT 1,
    row_id      VARCHAR(50) NOT NULL,
    col_id      VARCHAR(10) NOT NULL,
    is_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (report_id, version, row_id) REFERENCES reporting.rpt_row(report_id, version, row_id) ON DELETE CASCADE,
    CONSTRAINT rpt_row_column_map_col_fk FOREIGN KEY (report_id, version, col_id) REFERENCES reporting.rpt_column_def(report_id, version, col_id) ON DELETE CASCADE,
    CONSTRAINT rpt_row_column_map_report_version_row_col_key UNIQUE (report_id, version, row_id, col_id)
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_report_status     ON reporting.rpt_report(status);
CREATE INDEX IF NOT EXISTS idx_rpt_col_report_order  ON reporting.rpt_column_def(report_id, display_order);
CREATE INDEX IF NOT EXISTS idx_rpt_row_report_order  ON reporting.rpt_row(report_id, display_order);
CREATE INDEX IF NOT EXISTS idx_rpt_row_type          ON reporting.rpt_row(report_id, row_type);
CREATE INDEX IF NOT EXISTS idx_rpt_row_metric        ON reporting.rpt_row_metric(report_id, row_id);
CREATE INDEX IF NOT EXISTS idx_rpt_row_formula       ON reporting.rpt_row_formula(report_id, row_id);
CREATE INDEX IF NOT EXISTS idx_rpt_col_map           ON reporting.rpt_row_column_map(report_id, row_id);
