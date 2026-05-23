# Headless BI Reporting Engine — Implementation Plan (Finalized)

## Confirmed Architectural Decisions

| Decision                  | Resolution                                                                            |
| ------------------------- | ------------------------------------------------------------------------------------- |
| **Build order**           | Workstream A (DB Schema) first, then Workstream B (Python Engine)                     |
| **Semantic layer source** | Manually populated via SQL/migration scripts (no LookML importer needed)              |
| **Dev DB topology**       | Single PostgreSQL instance for both metadata AND query execution                      |
| **Prod DB topology**      | PostgreSQL for metadata + BigQuery for report query execution                         |
| **Output target**         | Excel (`.xlsx`) first — validates the end-to-end pipeline before API/UI               |
| **Reference date**        | Input parameter to `run_report()`; defaults to `CURRENT_DATE` if not provided         |
| **Future integration**    | Report engine will be invokable from the Agentic Layer (ADK agent asks user for date) |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    METADATA LAYER (PostgreSQL)              │
│  ┌─────────────────────┐  ┌──────────────────────────────┐  │
│  │   Semantic Layer    │  │      Reporting Layer         │  │
│  │  sem_model          │  │  rpt_report                  │  │
│  │  sem_explore        │  │  rpt_column_def              │  │
│  │  sem_view           │  │  rpt_row                     │  │
│  │  sem_dimension      │  │  rpt_row_metric              │  │
│  │  sem_measure        │  │  rpt_row_formula             │  │
│  │  sem_join           │  │  rpt_row_column_map          │  │
│  │  sem_derived_metric │  │  rpt_style / rpt_row_style   │  │
│  └─────────────────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
          │                              │
          └──────────┬───────────────────┘
                     ▼
        ┌─────────────────────────┐
        │   Python Engine         │
        │  excel_parser.py        │  ← ingests Excel → populates rpt_* tables
        │  semantic_resolver.py   │  ← resolves metrics from sem_* tables
        │  sql_generator.py       │  ← builds CTE query
        │  post_processor.py      │  ← row/column formula evaluation
        │  layout_renderer.py     │  ← Excel output with openpyxl
        │  report_runner.py       │  ← orchestrates all phases
        │  validator.py           │  ← pre-flight validation
        └─────────────────────────┘
          │ Dev                      │ Prod
          ▼                          ▼
     PostgreSQL                 BigQuery
     (execution)                (execution)
```

---

## WORKSTREAM A — PostgreSQL Metadata Schema

### Phase A1 — Semantic Layer Tables

**Goal:** Represent the LookML semantic layer (facts, dimensions, measures, joins) in a navigable relational model. Manually populated via seed SQL for now.

#### Tables

```sql
-- Top-level model grouping
CREATE TABLE sem_model (
    model_id     SERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    description  TEXT,
    version      INTEGER NOT NULL DEFAULT 1,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Entry-point explores (equivalent to LookML explores)
CREATE TABLE sem_explore (
    explore_id      SERIAL PRIMARY KEY,
    model_id        INTEGER NOT NULL REFERENCES sem_model(model_id),
    name            VARCHAR(100) NOT NULL,
    label           VARCHAR(200),
    fact_view_id    INTEGER,          -- resolved after sem_view insert
    sql_always_where TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Physical views / tables
CREATE TABLE sem_view (
    view_id      SERIAL PRIMARY KEY,
    model_id     INTEGER NOT NULL REFERENCES sem_model(model_id),
    name         VARCHAR(100) NOT NULL,
    table_ref    VARCHAR(300) NOT NULL,  -- e.g. project.dataset.fct_orders
    primary_key  VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Dimensions
CREATE TABLE sem_dimension (
    dimension_id  SERIAL PRIMARY KEY,
    view_id       INTEGER NOT NULL REFERENCES sem_view(view_id),
    name          VARCHAR(100) NOT NULL,
    label         VARCHAR(200),
    sql_expr      TEXT NOT NULL,    -- e.g. ${TABLE}.product_category
    data_type     VARCHAR(50),      -- string, number, date, boolean
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Measures (metrics)
CREATE TABLE sem_measure (
    measure_id    SERIAL PRIMARY KEY,
    view_id       INTEGER NOT NULL REFERENCES sem_view(view_id),
    name          VARCHAR(100) NOT NULL,
    label         VARCHAR(200),
    sql_expr      TEXT NOT NULL,    -- e.g. SUM(sales_amount_usd)
    agg_type      VARCHAR(50),      -- SUM, COUNT, AVG, MAX, MIN
    data_type     VARCHAR(50),      -- number, currency, percent
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (view_id, name)
);

-- Join relationships between explores and views
CREATE TABLE sem_join (
    join_id       SERIAL PRIMARY KEY,
    explore_id    INTEGER NOT NULL REFERENCES sem_explore(explore_id),
    from_view_id  INTEGER NOT NULL REFERENCES sem_view(view_id),
    to_view_id    INTEGER NOT NULL REFERENCES sem_view(view_id),
    join_sql      TEXT NOT NULL,    -- e.g. fact.product_id = dim.product_id
    join_type     VARCHAR(20) NOT NULL DEFAULT 'LEFT',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Derived cross-measure formulas (e.g. gross_margin = revenue - cogs)
CREATE TABLE sem_derived_metric (
    derived_metric_id  SERIAL PRIMARY KEY,
    model_id           INTEGER NOT NULL REFERENCES sem_model(model_id),
    name               VARCHAR(100) NOT NULL UNIQUE,
    label              VARCHAR(200),
    formula_expr       TEXT NOT NULL,  -- e.g. total_revenue - total_cost
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Indexes:**

```sql
CREATE INDEX idx_sem_measure_view    ON sem_measure(view_id);
CREATE INDEX idx_sem_dimension_view  ON sem_dimension(view_id);
CREATE INDEX idx_sem_join_explore    ON sem_join(explore_id);
```

---

### Phase A2 — Reporting Layer Tables

**Goal:** Normalize the Excel template structure into the database. The Excel parser (Phase B1) will populate these tables.

```sql
-- Report header
CREATE TABLE rpt_report (
    report_id    VARCHAR(50) PRIMARY KEY,  -- matches Excel report_id
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    version      INTEGER NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'draft',  -- draft | published
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Section A: Column definitions (C1..C7)
CREATE TABLE rpt_column_def (
    column_def_id  SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL REFERENCES rpt_report(report_id),
    col_id         VARCHAR(10) NOT NULL,   -- C1, C2, ... C7
    label          VARCHAR(200),
    col_type       VARCHAR(20) NOT NULL,   -- WEEK, MTD, YTD, ROLLING, CALC
    period_offset  INTEGER DEFAULT 0,      -- 0=current, -1=prior period, etc.
    rolling_n      INTEGER,               -- used when col_type = ROLLING
    formula_expr   TEXT,                  -- used when col_type = CALC e.g. (C1-C2)/C2
    display_order  INTEGER NOT NULL,
    UNIQUE (report_id, col_id)
);

-- Section B: Report body rows
CREATE TABLE rpt_row (
    row_id         VARCHAR(50) NOT NULL,
    report_id      VARCHAR(50) NOT NULL REFERENCES rpt_report(report_id),
    parent_row_id  VARCHAR(50),            -- self-referencing FK (nullable = root)
    label          VARCHAR(300) NOT NULL,
    row_type       VARCHAR(20) NOT NULL,   -- section | data | calc | blank
    display_order  INTEGER NOT NULL,
    indent_level   INTEGER NOT NULL DEFAULT 0,
    style_id       INTEGER,               -- FK to rpt_style
    PRIMARY KEY (report_id, row_id),
    FOREIGN KEY (report_id, parent_row_id) REFERENCES rpt_row(report_id, row_id)
);

-- Maps a 'data' row to a semantic measure
CREATE TABLE rpt_row_metric (
    row_metric_id  SERIAL PRIMARY KEY,
    report_id      VARCHAR(50) NOT NULL,
    row_id         VARCHAR(50) NOT NULL,
    measure_id     INTEGER NOT NULL REFERENCES sem_measure(measure_id),
    explore_id     INTEGER REFERENCES sem_explore(explore_id),
    FOREIGN KEY (report_id, row_id) REFERENCES rpt_row(report_id, row_id)
);

-- Stores formula expression for 'calc' rows (e.g. R2 - R3)
CREATE TABLE rpt_row_formula (
    row_formula_id  SERIAL PRIMARY KEY,
    report_id       VARCHAR(50) NOT NULL,
    row_id          VARCHAR(50) NOT NULL,
    formula_expr    TEXT NOT NULL,         -- e.g. "R2 - R3" or "R5 / R6"
    FOREIGN KEY (report_id, row_id) REFERENCES rpt_row(report_id, row_id)
);

-- Which columns (C1..C7) are active for each row (the X flags in Excel)
CREATE TABLE rpt_row_column_map (
    mapping_id   SERIAL PRIMARY KEY,
    report_id    VARCHAR(50) NOT NULL,
    row_id       VARCHAR(50) NOT NULL,
    col_id       VARCHAR(10) NOT NULL,
    is_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (report_id, row_id) REFERENCES rpt_row(report_id, row_id),
    FOREIGN KEY (report_id, col_id) REFERENCES rpt_column_def(report_id, col_id)
);

-- Style definitions
CREATE TABLE rpt_style (
    style_id    SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,  -- header, section, normal, total, blank
    font_size   INTEGER,
    is_bold     BOOLEAN DEFAULT FALSE,
    border_top  BOOLEAN DEFAULT FALSE,
    border_bottom BOOLEAN DEFAULT FALSE,
    alignment   VARCHAR(20) DEFAULT 'left',   -- left, center, right
    color_hex   VARCHAR(7),                   -- e.g. #2C3E50
    bg_color_hex VARCHAR(7)
);
```

**Indexes:**

```sql
CREATE INDEX idx_rpt_row_report    ON rpt_row(report_id, display_order);
CREATE INDEX idx_rpt_col_report    ON rpt_column_def(report_id, display_order);
CREATE INDEX idx_rpt_row_metric    ON rpt_row_metric(report_id, row_id);
```

---

### Phase A3 — Shared & Governance Tables

```sql
-- Tracks ingestion runs (Excel import, YAML import)
CREATE TABLE import_run (
    run_id       SERIAL PRIMARY KEY,
    source_type  VARCHAR(20) NOT NULL,   -- excel | yaml | lookml
    source_path  TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL,   -- pending | success | failed
    error_msg    TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit log for all metadata changes
CREATE TABLE audit_log (
    log_id       BIGSERIAL PRIMARY KEY,
    table_name   VARCHAR(100) NOT NULL,
    record_id    TEXT NOT NULL,
    action       VARCHAR(10) NOT NULL,  -- INSERT | UPDATE | DELETE
    changed_by   VARCHAR(100),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    old_values   JSONB,
    new_values   JSONB
);
```

---

### Phase A4 — Time Intelligence Metadata

```sql
-- Canonical period types (drives SQL generation logic)
CREATE TABLE time_period_type (
    period_type  VARCHAR(20) PRIMARY KEY,   -- WEEK, MTD, YTD, ROLLING, CALC
    label        VARCHAR(100) NOT NULL,
    description  TEXT
);

INSERT INTO time_period_type VALUES
  ('WEEK',    'Week to Date',    'From start of ISO week to reference date'),
  ('MTD',     'Month to Date',   'From start of calendar month to reference date'),
  ('YTD',     'Year to Date',    'From start of fiscal/calendar year to reference date'),
  ('ROLLING', 'Rolling N Weeks', 'Last N weeks from reference date'),
  ('CALC',    'Calculated',      'Derived from other columns via formula');

-- SQL generation templates per period type
-- :ref_date is the reference date parameter injected at runtime
CREATE TABLE time_offset_rule (
    rule_id       SERIAL PRIMARY KEY,
    period_type   VARCHAR(20) NOT NULL REFERENCES time_period_type(period_type),
    offset_value  INTEGER NOT NULL DEFAULT 0,
    where_clause  TEXT NOT NULL,  -- parameterized SQL template
    label_suffix  VARCHAR(50)     -- e.g. "Prior Week" for offset -1
);

INSERT INTO time_offset_rule (period_type, offset_value, where_clause, label_suffix) VALUES
  ('WEEK',  0,  'date_trunc(''week'', order_date) = date_trunc(''week'', :ref_date::date)',                        'Current Week'),
  ('WEEK', -1,  'date_trunc(''week'', order_date) = date_trunc(''week'', :ref_date::date - INTERVAL ''7 days'')',  'Prior Week'),
  ('WEEK', -2,  'date_trunc(''week'', order_date) = date_trunc(''week'', :ref_date::date - INTERVAL ''14 days'')', '2 Weeks Ago'),
  ('MTD',   0,  'order_date >= date_trunc(''month'', :ref_date::date) AND order_date <= :ref_date::date',          'MTD'),
  ('YTD',   0,  'order_date >= date_trunc(''year'',  :ref_date::date) AND order_date <= :ref_date::date',          'YTD');
```

---

### Phase A5 — Seed Semantic Data (from `semantic_model.yaml`)

**Goal:** Migrate the existing YAML semantic layer into PostgreSQL as the initial seed.

Migration script: `report_template/db/seed_semantic.sql`

Data to insert from `semantic_model.yaml`:

- `sem_model`: `retail_reporting`
- `sem_view`: `fact_sales`, `fact_inventory`, `fact_marketing`, `dim_product`, `dim_geography`, `dim_date`
- `sem_measure`: `total_revenue`, `order_count`, `stock_on_hand`, `inventory_cost`, `ad_spend`, `impressions`
- `sem_join`: all join relationships defined in YAML
- `sem_explore`: one explore per fact table

---

### Phase A6 — Database Migration Setup

**Tool:** Alembic (already likely used in the project)

- `db/migrations/001_create_semantic_tables.sql`
- `db/migrations/002_create_reporting_tables.sql`
- `db/migrations/003_create_governance_tables.sql`
- `db/migrations/004_seed_time_period_types.sql`
- `db/migrations/005_seed_semantic_data.sql`

---

## WORKSTREAM B — Python Engine

> **Note:** All phases below inherit the confirmed decisions:
>
> - **Dev**: uses PostgreSQL as both metadata store and execution target
> - **Prod**: uses BigQuery as execution target (abstracted via `db_executor.py`)
> - **Reference date**: `reference_date: date | None` parameter on all entry points; defaults to `date.today()`

### Phase B1 — Data Models & Excel Parser (`engine/excel_parser.py`) [COMPLETED]

**Status:** Done.

- Immutable dataclasses implemented in `models.py`.
- `ExcelParser` implemented with full support for Section A (columns) and Section B (rows).
- `SemanticResolver` implemented with batch-querying for measure SQL and JOIN paths.
- Unit test suite verified 100% pass rate.

---

### Phase B2 — Date Utilities (`engine/date_utils.py`)

**Goal:** Provide a shared core for relative time period calculations (e.g., "What was the date range for MTD -1 relative to April 5th?").

**Logic:**

- `get_period_range(ref_date, type, offset, rolling_n)` function.
- Support `WEEK` (ISO), `MTD`, `YTD`, and `ROLLING`.
- Handle year-wraparound and month-length correctly.

---

### Phase B3 — SQL Generator (`engine/sql_generator.py`)

**Goal:** Build the BigQuery/PostgreSQL super-query.

**Logic:**

1. Group `ResolvedMetric`s by `Explore` (fact table).
2. For each explore, identify the "Max Time Range" needed.
3. Build a CTE per Explore using **Conditional Aggregation**:
    ```sql
    SELECT
      SUM(CASE WHEN date >= '...' AND date <= '...' THEN val ELSE 0 END) AS r1_c1,
      ...
    FROM fact_table
    JOIN ... (resolved joins)
    ```
4. Final SELECT aliases everything into a flat result set.

---

### Phase B4 — Post-Processor (`engine/post_processor.py`)

**Goal:** Execute the report-level logic that SQL cannot do (cross-row math, cross-col math).

**Logic:**

1. **Vertical Calculation**: Execute `calc` rows (e.g., `R1 - R2`) using a dependency graph or topological sort to handle nested formulas.
2. **Horizontal Calculation**: Execute `CALC` columns (e.g., `(C1-C2)/C2`) across the result matrix.
3. **Formatters**: Convert raw floats into formatted currency/percentage strings if needed.

---

### Phase B5 — Report Runner & Integration (`engine/report_runner.py`)

**Goal:** The standard entry point.

**Logic:**

- `run_report(report_id, ref_date)` orchestrates Resolver → Generator → Executor → Processor.
- Returns a `FinalReport` object ready for rendering.

---

### Phase B6 — Excel Layout Renderer (`engine/layout_renderer.py`)

**Goal:** Final output in `.xlsx`.

- Apply `rpt_style` (colors, borders).
- Apply `indent_level`.
- Generate the final workbook for the user.

---

### Phase B7 — Layout Renderer (`engine/layout_renderer.py`)

Renders final DataFrame → `.xlsx` using `openpyxl`:

- Apply style per row type (from `rpt_style` table defaults or config)
- Apply `indent_level` as cell left-padding
- Header row uses frozen panes
- Column widths auto-sized
- Format numbers: 2 decimal places, comma separator, negative in parentheses

---

### Phase B8 — Validator (`engine/validator.py`)

Pre-flight validation called before SQL generation:

| Check                                                | Error Type                |
| ---------------------------------------------------- | ------------------------- |
| All `source` metrics exist in `sem_measure`          | `SemanticValidationError` |
| All `col_id` refs in formulas are defined            | `FormulaValidationError`  |
| No circular row formula dependencies                 | `CircularDependencyError` |
| Column formulas only reference valid `col_id` values | `FormulaValidationError`  |

---

### Phase B9 — Report Runner (`engine/report_runner.py`)

Single entry point for the full pipeline:

```python
def run_report(
    report_id: str,
    reference_date: date | None = None,   # defaults to date.today()
    output_path: Path | None = None,
    output_format: str = "excel"
) -> Path:
    ref_date = reference_date or date.today()
    config   = load_report_config(report_id, ref_date)
    validator.validate(config)
    metrics  = semantic_resolver.resolve(config)
    sql      = sql_generator.build(config, metrics)
    raw_df   = db_executor.execute(sql, {"ref_date": ref_date})
    final_df = post_processor.apply(raw_df, config)
    return layout_renderer.render(final_df, config, output_path)
```

**ADK integration path:** The `run_report()` function becomes a tool callable from the ADK agent. The agent asks the user for `report_id` and `reference_date`, then calls this function.

---

## File Structure

```
report_template/
├── db/
│   ├── migrations/
│   │   ├── 001_create_semantic_tables.sql
│   │   ├── 002_create_reporting_tables.sql
│   │   ├── 003_create_governance_tables.sql
│   │   ├── 004_seed_time_period_types.sql
│   │   └── 005_seed_semantic_data.sql
│   └── schema.sql                    ← full combined DDL
├── engine/
│   ├── __init__.py
│   ├── models.py                     ← dataclasses
│   ├── excel_parser.py
│   ├── semantic_resolver.py
│   ├── db_executor.py                ← PostgreSQL / BigQuery abstraction
│   ├── sql_generator.py
│   ├── post_processor.py
│   ├── layout_renderer.py
│   ├── validator.py
│   └── report_runner.py
├── tests/
│   ├── conftest.py
│   ├── test_excel_parser.py
│   ├── test_semantic_resolver.py
│   ├── test_sql_generator.py
│   ├── test_post_processor.py
│   ├── test_layout_renderer.py
│   └── test_report_runner.py
├── template/
│   └── hybrid_reporting_template.xlsx
├── sample/
│   ├── semantic_model.yaml
│   └── renderTemplate.py             ← keep as reference
├── excel_orchestrator.py             ← keep as reference (legacy)
├── sql_generator.py                  ← keep as reference (legacy root)
└── prompt/
    ├── report_promt.md
    ├── datamodel_prompt.md
    └── gemini_context.md
```

---

## Execution Sequence

```
Phase A1 → A2 → A3 → A4 → A5 → A6   (DB Schema — complete before any Python)
Phase B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8 → B9  (Python Engine)
```

Each phase has isolated unit tests. The pipeline is validated end-to-end after B9.

---

## Verification Plan

| Phase | Automated Test                            | Success Criterion                                      |
| ----- | ----------------------------------------- | ------------------------------------------------------ |
| A1–A4 | Apply migrations against local PostgreSQL | No DDL errors, constraints enforced                    |
| A5    | Seed script                               | All 3 fact views + measures queryable                  |
| B1    | `test_excel_parser.py`                    | Correct column/row split, all fields parsed            |
| B2    | `test_semantic_resolver.py`               | Metric lookup + join resolution correct                |
| B3    | `test_sql_generator.py`                   | Valid SQL, correct CTE structure per col type          |
| B4    | DB executor test                          | Dev PostgreSQL returns DataFrame                       |
| B5    | `test_post_processor.py`                  | Row/column formulas evaluated correctly                |
| B6    | `test_layout_renderer.py`                 | `.xlsx` file produced, styles applied                  |
| B7    | `test_report_runner.py`                   | End-to-end run with sample report produces valid Excel |
