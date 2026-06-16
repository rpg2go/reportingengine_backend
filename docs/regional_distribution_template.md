# Report Template Reference: REGIONAL_DISTRIBUTION

This document details the configuration layout, column parameters, row-level metric definitions, active cell grid, filters, and execution walkthrough for the **Regional Distribution** (`REGIONAL_DISTRIBUTION`) report.

> **Live state**: All data reflects version **4 (draft)** fetched directly from `GET /api/reports/REGIONAL_DISTRIBUTION` on 2026-06-16.

---

## 📋 Overview & Header Configuration

The `REGIONAL_DISTRIBUTION` report aggregates investment portfolio metrics broken down by geographic region and country, applying quick filters on customer segments.

| Field | Value |
| :--- | :--- |
| **Report ID** | `REGIONAL_DISTRIBUTION` |
| **Logical Name** | `Regional Distribution` |
| **Version** | `4` |
| **Status** | `draft` |
| **Explore ID** | `1` (semantic join routing active via `SchemaGraphRouter`) |
| **Source Table** | `analytics.fact_investments` (inferred from row metric `sourceTable`) |
| **Granularity** | `dim_location.region, dim_location.country_name` |
| **Timeframe Start** | `2024-06-01` |
| **Timeframe End** | `2026-06-09` |
| **Timeframe Today** | `false` |

> The `granularity` field drives a `GROUP BY dim_location.region, dim_location.country_name` in the generated CTE. The engine uses `SchemaGraphRouter` (Dijkstra BFS over `meta_relationship`) to resolve the LEFT JOIN path from `fact_investments` → `dim_location` automatically.

---

## 📊 1. Column Definitions

The report defines seven columns (`C1` to `C7`) spanning MTD, YTD, Rolling, and Calculated growth timeframes:

| Column ID | Label | Column Type | Period Offset | Rolling N | Rolling Grain | Formula / Expression | Display Order |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **C1** | Current Month | `MTD` | `0` | *NULL* | *NULL* | *None* | 1 |
| **C2** | Prior Month | `MTD` | `-1` | *NULL* | *NULL* | *None* | 2 |
| **C3** | MoM % | `CALC` | `0` | *NULL* | *NULL* | `(C1-C2)/C2` | 3 |
| **C4** | YTD Current | `YTD` | `0` | *NULL* | *NULL* | *None* | 4 |
| **C5** | YTD Prior Year | `YTD` | `-12` | *NULL* | *NULL* | *None* | 5 |
| **C6** | YoY Growth | `CALC` | `0` | *NULL* | *NULL* | `(C4-C5)/C5` | 6 |
| **C7** | 3-Mo Rolling | `ROLLING` | `0` | `3` | **`MONTH`** | *None* | 7 |

- **`CALC` columns** (C3, C6) hold no SQL — they are evaluated post-query by `PostProcessorService` using `exp4j`.
- **`ROLLING` C7** uses `rollingGrain = "MONTH"`, so `DateUtils` computes a trailing 3-calendar-month window boundary.
- All seven columns are **active on every row** in this version.

---

## 📑 2. Row Definitions & Metric Sources

The report layout consists of one section header row and four metric data rows. In version 4, metrics use the **visual builder mode** (UI-picked aggregation + column), except R5 which uses raw SQL.

| Row ID | Parent | Label | Row Type | Indent | Style | Metric Mode | Aggregation | Target Column / Raw SQL |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **R1** | *NULL* | Investments Overview | `section` | 0 | `header` | *None* | — | — |
| **R2** | `R1` | Total AUM | `data` | 1 | `total` | `visual` | `SUM` | `current_value` |
| **R3** | `R1` | Avg Position Size | `data` | 1 | `normal` | `visual` | `AVG` | `quantity` |
| **R4** | `R1` | Investment Count | `data` | 1 | `normal` | `visual` | `AVG` | `quantity` |
| **R5** | `R1` | Unique Tickers | `data` | 1 | `normal` | `raw` | — | `COUNT(ticker_symbol)` |

All data rows bind to `analytics.fact_investments` as the source table.

### Per-Row Filter Expressions (`filterExpr`)

Rows can carry independent filter rules that scope their aggregation inside the CTE:

| Row ID | Filter Summary |
| :--- | :--- |
| **R1** | *None* |
| **R2** | `quantity IS NOT NULL` |
| **R3** | *None* |
| **R4** | `hier_id IN (1, 2)` AND `hier_id IS NOT 3`; nested OR group: `location_id IN (1,2,3,4)` AND `ticker_symbol IN (AAPL, AMZN, GOOGL, META, MSFT)` |
| **R5** | `ticker_symbol IS NOT NULL` |

---

## 🔍 3. Filters

### Quick Filters (runtime-injectable)

Two quick filters pre-configured on `dim_customers`. These inject a JOIN from `fact_investments → dim_customers` (resolved by `SchemaGraphRouter`) and add a scoped `WHERE` clause at execution time:

| Dimension Table | Attribute | Operator | Value | Conjunction |
| :--- | :--- | :--- | :--- | :--- |
| `dim_customers` | `country_code` | `=` | `US` | `OR` |
| `dim_customers` | `segment` | `=` | `Wealth` | `AND` |

### General Filters

Currently empty (`[]`) — no free-text SQL conditions applied at the report level.

---

## 🏁 4. Cell Intersect Map (Grid Enablement)

In version 4 **all 35 cells are enabled**. Every row × column intersection is compiled and rendered.

| Row ID / Column ID | C1 (MTD) | C2 (MTD-1) | C3 (MoM%) | C4 (YTD) | C5 (YTD-12) | C6 (YoY%) | C7 (3-Mo Roll) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R1 (Header)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **R2 (Total AUM)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **R3 (Avg Size)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **R4 (Count)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **R5 (Tickers)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

> C3 and C6 cells are enabled but **not queried** — their values are filled in by `PostProcessorService` after the SQL result is returned.

---

## ⚙️ 5. Execution & Computation Walkthrough

When this report is executed with a reference date of **`2025-12-31`**, the following sequence takes place:

### A. Join Resolution (`SchemaGraphRouter`)

Because `granularity` references `dim_location` and `quickFilters` references `dim_customers`, the engine resolves **two** JOIN chains before SQL compilation:

1. `analytics.fact_investments` → `analytics.dim_location` (for `GROUP BY region, country_name`)
2. `analytics.fact_investments` → `analytics.dim_customers` (for `WHERE country_code = 'US' AND segment = 'Wealth'`)

Both paths are discovered via weighted Dijkstra BFS over the `meta_relationship` catalog graph loaded by `SchemaCatalogLoader`.

### B. SQL Query Compilation (`SqlGeneratorService`)

1. **Boundary Resolution**: Column offsets are translated to date windows:
   - `C1` (MTD, offset `0`): `2025-12-01` → `2025-12-31`
   - `C2` (MTD, offset `-1`): `2025-11-01` → `2025-11-30`
   - `C4` (YTD, offset `0`): `2025-01-01` → `2025-12-31`
   - `C5` (YTD, offset `-12`): `2024-01-01` → `2024-12-31`
   - `C7` (ROLLING 3 × MONTH): trailing 3-month window ending `2025-12-31`

2. **CTE Query Assembly** with dimensional grouping and row-scoped filters:

   ```sql
   WITH cte_fact_investments AS (
     SELECT
       dim_location.region         AS granularity_col_0,
       dim_location.country_name   AS granularity_col_1,

       -- R2 × C1: Total AUM, Current Month (filterExpr: quantity IS NOT NULL)
       CAST(SUM(CASE WHEN fi.date_key >= '2025-12-01' AND fi.date_key <= '2025-12-31'
                     AND fi.quantity IS NOT NULL
                     THEN fi.current_value ELSE 0 END) AS DOUBLE PRECISION) AS val_r2_c1,

       -- R2 × C2: Total AUM, Prior Month
       CAST(SUM(CASE WHEN fi.date_key >= '2025-11-01' AND fi.date_key <= '2025-11-30'
                     AND fi.quantity IS NOT NULL
                     THEN fi.current_value ELSE 0 END) AS DOUBLE PRECISION) AS val_r2_c2,

       -- R3 × C1: Avg Position Size, Current Month (no row filter)
       CAST(AVG(CASE WHEN fi.date_key >= '2025-12-01' AND fi.date_key <= '2025-12-31'
                     THEN fi.quantity END) AS DOUBLE PRECISION) AS val_r3_c1,

       -- R5 × C1: Unique Tickers, raw SQL (filterExpr: ticker_symbol IS NOT NULL)
       CAST(COUNT(CASE WHEN fi.date_key >= '2025-12-01' AND fi.date_key <= '2025-12-31'
                       AND fi.ticker_symbol IS NOT NULL
                       THEN fi.ticker_symbol END) AS DOUBLE PRECISION) AS val_r5_c1,
       -- ... all enabled (row, col) pairs
     FROM analytics.fact_investments fi
     LEFT JOIN analytics.dim_location dl ON fi.location_id = dl.id
     LEFT JOIN analytics.dim_customers dc ON fi.customer_id = dc.id
     WHERE dc.country_code = 'US'
       AND dc.segment = 'Wealth'
     GROUP BY dim_location.region, dim_location.country_name
   )
   SELECT 'R2' AS row_id, 'C1' AS col_id, region, country_name, val_r2_c1 AS val
   FROM cte_fact_investments
   UNION ALL
   SELECT 'R2', 'C2', region, country_name, val_r2_c2 FROM cte_fact_investments
   -- ... all active (row, col) pairs
   ```

### C. Mathematical Formula Evaluation (`PostProcessorService`)

Once raw SQL results are returned (one record set per `(region, country_name)` group), `exp4j` evaluates the CALC columns **per granularity group**:

- **MoM %** (`C3`): `(C1 - C2) / C2` — for each row, for each region/country pair.
- **YoY Growth** (`C6`): `(C4 - C5) / C5` — same.
- Divide-by-zero (C2 or C5 = `0`) is intercepted → returns `0.0` or `null`.

### D. Layout Rendering (`LayoutRendererService`)

The finalized cell value map is written to an `.xlsx` file using Apache POI. Rows are grouped by `(region, country_name)` — each geographic group renders as a separate section of the spreadsheet with `header`, `total`, and `normal` styles applied per row type.
