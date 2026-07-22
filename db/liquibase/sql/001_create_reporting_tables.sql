--liquibase formatted sql
--changeset devops:001_create_reporting_tables endDelimiter:;

CREATE SCHEMA IF NOT EXISTS report_builder_owner;

-- -----------------------------------------------------------------------------
-- row_style
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.row_style (
    style_id      SERIAL PRIMARY KEY,
    name          VARCHAR(50) NOT NULL UNIQUE,
    font_size     INTEGER DEFAULT 11,
    is_bold       BOOLEAN DEFAULT FALSE,
    border_top    BOOLEAN DEFAULT FALSE,
    border_bottom BOOLEAN DEFAULT FALSE,
    alignment     VARCHAR(10) DEFAULT 'left' CHECK (alignment IN ('left', 'center', 'right')),
    color_hex     VARCHAR(7),
    bg_color_hex  VARCHAR(7)
);

-- -----------------------------------------------------------------------------
-- report_config
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.report_config (
    report_id                  VARCHAR(50) NOT NULL,
    version                    INTEGER NOT NULL DEFAULT 1,
    report_name                VARCHAR(200) NOT NULL,
    description                TEXT,
    status                     VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'in_review', 'published')),
    source_table               VARCHAR(150),
    source_field               VARCHAR(150),
    granularity                VARCHAR(1000),
    reporting_date_type        VARCHAR(16) DEFAULT 'DYNAMIC' CHECK (reporting_date_type IN ('FIXED', 'DYNAMIC')),
    reporting_date_static      DATE,
    reporting_date_expression  VARCHAR(8) DEFAULT 'T-2',
    timeframe_start_type       VARCHAR(16) DEFAULT 'FIXED',
    timeframe_start_static     DATE DEFAULT '2022-01-01',
    timeframe_start_expression VARCHAR(8),
    timeframe_end_type         VARCHAR(16) DEFAULT 'DYNAMIC' CHECK (timeframe_end_type IN ('FIXED', 'DYNAMIC')),
    timeframe_end_static       DATE,
    timeframe_end_expression   VARCHAR(8) DEFAULT 'T-2',
    quick_filters              TEXT,
    general_filters            TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted                    BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (report_id, version)
);

-- -----------------------------------------------------------------------------
-- column_definition
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.column_definition (
    column_def_id  SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    col_id         VARCHAR(10) NOT NULL,
    label          VARCHAR(200),
    col_type       VARCHAR(20) NOT NULL CHECK (col_type IN ('WTD', 'MTD', 'YTD', 'ROLLING', 'CALC', 'HEADER')),
    period_offset  INTEGER DEFAULT 0,
    rolling_n      INTEGER,
    rolling_grain  VARCHAR(10) CHECK (rolling_grain IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    formula_expr   TEXT,
    display_order  INTEGER NOT NULL,
    tier_level     VARCHAR(10) DEFAULT 'L1' NOT NULL CHECK (tier_level IN ('L1', 'L2', 'L3')),
    parent_id      VARCHAR(50) DEFAULT NULL,
    FOREIGN KEY (report_id, version) REFERENCES report_builder_owner.report_config(report_id, version) ON DELETE CASCADE,
    CONSTRAINT uq_column_definition UNIQUE (report_id, version, col_id)
);

-- -----------------------------------------------------------------------------
-- row_definition
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.row_definition (
    row_id        VARCHAR(50) NOT NULL,
    report_id     VARCHAR(50) NOT NULL,
    version       INTEGER NOT NULL DEFAULT 1,
    parent_row_id VARCHAR(50),
    label         VARCHAR(300) NOT NULL,
    row_type      VARCHAR(20) NOT NULL CHECK (row_type IN ('section', 'data', 'calc', 'blank')),
    display_order INTEGER NOT NULL,
    indent_level  INTEGER NOT NULL DEFAULT 0,
    style_id      INTEGER REFERENCES report_builder_owner.row_style(style_id),
    filter_expr   TEXT,
    PRIMARY KEY (report_id, version, row_id),
    FOREIGN KEY (report_id, version) REFERENCES report_builder_owner.report_config(report_id, version) ON DELETE CASCADE,
    CONSTRAINT fk_row_definition_parent FOREIGN KEY (report_id, version, parent_row_id) REFERENCES report_builder_owner.row_definition(report_id, version, row_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);

-- -----------------------------------------------------------------------------
-- row_metric_mapping
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.row_metric_mapping (
    row_metric_id      SERIAL PRIMARY KEY,
    report_id          VARCHAR(50) NOT NULL,
    version            INTEGER NOT NULL DEFAULT 1,
    row_id             VARCHAR(50) NOT NULL,
    sql_expr           TEXT,
    measure_definition TEXT,
    FOREIGN KEY (report_id, version, row_id) REFERENCES report_builder_owner.row_definition(report_id, version, row_id) ON DELETE CASCADE,
    CONSTRAINT uq_row_metric_mapping UNIQUE (report_id, version, row_id)
);

-- -----------------------------------------------------------------------------
-- row_formula
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.row_formula (
    row_formula_id SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    row_id         VARCHAR(50) NOT NULL,
    formula_expr   TEXT NOT NULL,
    FOREIGN KEY (report_id, version, row_id) REFERENCES report_builder_owner.row_definition(report_id, version, row_id) ON DELETE CASCADE,
    CONSTRAINT uq_row_formula UNIQUE (report_id, version, row_id)
);

-- -----------------------------------------------------------------------------
-- row_column_intersection
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_builder_owner.row_column_intersection (
    mapping_id  SERIAL PRIMARY KEY,
    report_id   VARCHAR(50) NOT NULL,
    version     INTEGER NOT NULL DEFAULT 1,
    row_id      VARCHAR(50) NOT NULL,
    col_id      VARCHAR(10) NOT NULL,
    is_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (report_id, version, row_id) REFERENCES report_builder_owner.row_definition(report_id, version, row_id) ON DELETE CASCADE,
    CONSTRAINT fk_row_column_intersection_col FOREIGN KEY (report_id, version, col_id) REFERENCES report_builder_owner.column_definition(report_id, version, col_id) ON DELETE CASCADE,
    CONSTRAINT uq_row_column_intersection UNIQUE (report_id, version, row_id, col_id)
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_report_status     ON report_builder_owner.report_config(status);
CREATE INDEX IF NOT EXISTS idx_rpt_col_report_order  ON report_builder_owner.column_definition(report_id, display_order);
CREATE INDEX IF NOT EXISTS idx_rpt_row_report_order  ON report_builder_owner.row_definition(report_id, display_order);
CREATE INDEX IF NOT EXISTS idx_rpt_row_type          ON report_builder_owner.row_definition(report_id, row_type);
CREATE INDEX IF NOT EXISTS idx_rpt_row_metric        ON report_builder_owner.row_metric_mapping(report_id, row_id);
CREATE INDEX IF NOT EXISTS idx_rpt_row_formula       ON report_builder_owner.row_formula(report_id, row_id);
CREATE INDEX IF NOT EXISTS idx_rpt_col_map           ON report_builder_owner.row_column_intersection(report_id, row_id);
