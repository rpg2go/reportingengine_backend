# Report Template Reference: REGIONAL_DISTRIBUTION

This document details the configuration layout, column parameters, row-level SQL metrics, and cell mapping flags for the seeded **Regional Distribution** (`REGIONAL_DISTRIBUTION`) report.

---

## 📋 Overview & Header Configuration

The `REGIONAL_DISTRIBUTION` report aggregates and analyzes investment positions, average holdings, asset counts, and unique ticker distributions.

* **Report ID**: `REGIONAL_DISTRIBUTION`
* **Logical Name**: `Regional Distribution`
* **Status**: `published`
* **Version**: `1`
* **Default Explore**: `NULL` (Uses physical data binding)
* **Inferred Source Table**: `analytics.fact_investments` (Derived dynamically from row metric configurations)

---

## 📊 1. Column Definitions

The report defines seven columns (`C1` to `C7`) spanning MTD, YTD, Rolling, and Calculated growth timeframes:

| Column ID | Label | Column Type | Period Offset | Rolling N | Rolling Grain | Formula / Expression | Display Order |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **C1** | Current Month | `MTD` | `0` | *NULL* | *NULL* | *None* | 1 |
| **C2** | Prior Month | `MTD` | `-1` | *NULL* | *NULL* | *None* | 2 |
| **C3** | MoM % | `CALC` | `0` | *NULL* | *NULL* | `(C1 - C2) / C2` | 3 |
| **C4** | YTD Current | `YTD` | `0` | *NULL* | *NULL* | *None* | 4 |
| **C5** | YTD Prior Year | `YTD` | `-12` | *NULL* | *NULL* | *None* | 5 |
| **C6** | YoY Growth | `CALC` | `0` | *NULL* | *NULL* | `(C4 - C5) / C5` | 6 |
| **C7** | 3-Mo Rolling | `ROLLING` | `0` | `3` | `MONTH` | *None* | 7 |

---

## 📑 2. Row Definitions & SQL Metrics

The report layout consists of one section header row and four metric data rows.

| Row ID | Parent Row | Label | Row Type | Indent Level | Style ID | SQL Aggregation Expression (`sql_expr`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **R1** | *NULL* | Investments Overview | `section` | 0 | 1 (Section) | *None* |
| **R2** | `R1` | Total AUM | `data` | 1 | 3 (Bold) | `SUM(analytics.fact_investments.current_value)` |
| **R3** | `R1` | Avg Position Size | `data` | 1 | 4 (Normal) | `AVG(analytics.fact_investments.current_value)` |
| **R4** | `R1` | Investment Count | `data` | 1 | 4 (Normal) | `COUNT(DISTINCT analytics.fact_investments.id)` |
| **R5** | `R1` | Unique Tickers | `data` | 1 | 4 (Normal) | `COUNT(DISTINCT analytics.fact_investments.ticker_symbol)` |

---

## 🏁 3. Cell Intersect Map (Grid Enablement)

The grid map determines which cells are calculated and rendered. Intersections marked as **Disabled** are left empty in the output spreadsheet, skipping query compilation for that cell.

| Row ID / Column ID | C1 (MTD Current) | C2 (MTD Prior) | C3 (MoM %) | C4 (YTD Current) | C5 (YTD Prior) | C6 (YoY %) | C7 (3-Mo Roll) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R1 (Header)** | **Enabled** | **Enabled** | *Disabled* | **Enabled** | **Enabled** | *Disabled* | *Disabled* |
| **R2 (Total AUM)** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | *Disabled* |
| **R3 (Avg Size)** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | *Disabled* |
| **R4 (Count)** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | *Disabled* |
| **R5 (Tickers)** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | **Enabled** | *Disabled* |

---

## ⚙️ 4. Execution & Computation Walkthrough

When this report is executed with a reference date of **`2025-12-31`**, the following sequence takes place in the engine:

### A. SQL Query Compilation (`SqlGeneratorService`)
1. **Boundary Resolution**: The engine translates column offsets into date boundaries:
   - `C1` (MTD Current, offset `0`): `2025-12-01` to `2025-12-31`.
   - `C2` (MTD Prior, offset `-1`): `2025-11-01` to `2025-11-30`.
   - `C4` (YTD Current, offset `0`): `2025-01-01` to `2025-12-31`.
   - `C5` (YTD Prior Year, offset `-12`): `2024-01-01` to `2024-12-31`.
2. **CTE Query Assembly**: A Common Table Expression (CTE) is generated to run the SQL expressions against the DWH table `analytics.fact_investments` using conditional aggregation filters:
   ```sql
   WITH cte_fact_investments AS (
     SELECT
       CAST(NULL AS VARCHAR) AS granularity_col,
       -- C1 Current Month AUM
       CAST(SUM(CASE WHEN date_key >= '2025-12-01' AND date_key <= '2025-12-31' THEN current_value ELSE 0 END) AS DOUBLE PRECISION) AS val_r2_c1,
       -- C2 Prior Month AUM
       CAST(SUM(CASE WHEN date_key >= '2025-11-01' AND date_key <= '2025-11-30' THEN current_value ELSE 0 END) AS DOUBLE PRECISION) AS val_r2_c2,
       ...
     FROM analytics.fact_investments
     GROUP BY 1
   )
   ...
   ```

### B. Mathematical Formula Evaluation (`PostProcessorService`)
Once the raw query results are fetched, the post-processor evaluates calculations using `exp4j`:
- **MoM %** (`C3`) is calculated for each row: `(C1 - C2) / C2`.
- **YoY Growth** (`C6`) is calculated for each row: `(C4 - C5) / C5`.
- Boundary divisions (e.g. if `C2` or `C5` is `0`) are automatically intercepted to prevent divide-by-zero errors (returning `0.0` or `null`).

### C. Layout Rendering (`LayoutRendererService`)
The finalized grid intersection value map is styled and written to cells using Apache POI, applying section headers, border styles, bold fonts, and number formatting to cells.
