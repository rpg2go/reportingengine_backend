-- =============================================================================
-- Migration 004: Seed Time Period Types & Offset Rules
-- Schema: reporting
-- Description: Populates the canonical period type registry and SQL template
--              rules used by the SQL generator to build time-filtered CTEs.
--
--              The where_clause uses %(ref_date)s placeholder (psycopg v3
--              parameterized style). The SQL generator will substitute the
--              actual reference_date value at query execution time.
--
-- Date: 2026-04-05
-- Depends on: 003_create_governance_tables.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Canonical period types
-- -----------------------------------------------------------------------------
INSERT INTO reporting.time_period_type (period_type, label, description) VALUES
    ('WEEK',    'Week to Date',     'Filters to the ISO week containing the reference date'),
    ('MTD',     'Month to Date',    'Filters from start of calendar month to reference date'),
    ('YTD',     'Year to Date',     'Filters from start of calendar year to reference date'),
    ('ROLLING', 'Rolling N Weeks',  'Filters the last N weeks relative to the reference date'),
    ('CALC',    'Calculated',       'Derived from other columns via a formula expression; no SQL filter generated');

-- -----------------------------------------------------------------------------
-- 2. Time offset rules
--    - period_type = WEEK:    offset 0 = current week, -1 = prior week, -2 = 2 weeks ago
--    - period_type = MTD:     offset 0 = current month-to-date (no historical offset defined here)
--    - period_type = YTD:     offset 0 = current year-to-date
--    - period_type = ROLLING: rolling_n stored on rpt_column_def; rule provides template
--
--    The table_time_key column in the where_clause is a placeholder that the
--    SQL generator replaces at runtime with the actual time_key from sem_view.
--    The SQL generator will substitute `reporting_date` (or the fact's time_key)
--    in place of the generic literal shown here.
-- -----------------------------------------------------------------------------

-- WEEK: current week (offset = 0)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('WEEK', 0,
     'date_trunc(''week'', {time_key}::date) = date_trunc(''week'', %(ref_date)s::date)',
     'Current Week');

-- WEEK: prior week (offset = -1)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('WEEK', -1,
     'date_trunc(''week'', {time_key}::date) = date_trunc(''week'', %(ref_date)s::date - INTERVAL ''7 days'')',
     'Prior Week');

-- WEEK: 2 weeks ago (offset = -2)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('WEEK', -2,
     'date_trunc(''week'', {time_key}::date) = date_trunc(''week'', %(ref_date)s::date - INTERVAL ''14 days'')',
     '2 Weeks Ago');

-- WEEK: 3 weeks ago (offset = -3)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('WEEK', -3,
     'date_trunc(''week'', {time_key}::date) = date_trunc(''week'', %(ref_date)s::date - INTERVAL ''21 days'')',
     '3 Weeks Ago');

-- MTD: current month-to-date (offset = 0)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('MTD', 0,
     '{time_key}::date >= date_trunc(''month'', %(ref_date)s::date) AND {time_key}::date <= %(ref_date)s::date',
     'MTD');

-- MTD: prior month full (offset = -1)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('MTD', -1,
     '{time_key}::date >= date_trunc(''month'', %(ref_date)s::date - INTERVAL ''1 month'') AND {time_key}::date < date_trunc(''month'', %(ref_date)s::date)',
     'Prior Month');

-- YTD: current year-to-date (offset = 0)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('YTD', 0,
     '{time_key}::date >= date_trunc(''year'', %(ref_date)s::date) AND {time_key}::date <= %(ref_date)s::date',
     'YTD');

-- YTD: prior year full (offset = -1)
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('YTD', -1,
     '{time_key}::date >= date_trunc(''year'', %(ref_date)s::date - INTERVAL ''1 year'') AND {time_key}::date < date_trunc(''year'', %(ref_date)s::date)',
     'Prior Year');

-- ROLLING: last N weeks — the {rolling_n} placeholder is substituted by the SQL generator
--          using the rolling_n value from rpt_column_def at build time.
INSERT INTO reporting.time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
    ('ROLLING', 0,
     '{time_key}::date >= %(ref_date)s::date - INTERVAL ''{rolling_n} weeks'' AND {time_key}::date <= %(ref_date)s::date',
     'Rolling {rolling_n} Weeks');
