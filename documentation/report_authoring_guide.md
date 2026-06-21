# Business User Guide: Authoring & Managing Reports

This guide explains how to create, configure, and manage financial reports using the **DWH Reporting Engine** web interface.

> [!NOTE]
> All report authoring is done through the Angular UI at **http://127.0.0.1:4200**. No manual Excel editing or Python scripts are required.

---

## 🔑 Signing In

1. Open **http://127.0.0.1:4200** in your browser.
2. Enter credentials (`admin` / `password`) on the login screen.
3. You will land on the **Report Catalog** dashboard.

---

## 📋 Report Catalog (Dashboard)

The Dashboard lists all report templates. From here you can:
- **Search** reports using the quick filter bar at the top.
- **Click a report card** to open it in the Report Builder for editing.
- **Create a new report** using the **+ New Report** button.

---

## 🏗️ Report Builder: Step-by-Step

The Report Builder opens when you click a report or create a new one. It is organized into four steps.

### Step 1 — Report Setup
Configure the report header:
| Field | Description |
| :--- | :--- |
| **Report Name** | Display name shown in the catalog. |
| **Source Table** | The fact table to query (e.g., `analytics.fact_sales`). |
| **Granularity** | The grouping/time column (e.g., `transaction_date`). |
| **Status** | `draft` or `active`. |
| **Timeframe** | Start date, end date, or tick "Use Today" for rolling periods. |
| **Quick Filters** | Comma-separated tags shown as filter pills in the header. |

### Step 2 — Column Setup
Define the time columns (C1 through C7) that appear as headers in the output:
| Field | Description |
| :--- | :--- |
| **Label** | Column header text (e.g., `MTD`, `YTD`, `Prior Year`). |
| **Type** | `mtd`, `ytd`, `week`, `rolling`, or `formula`. |
| **Period Offset** | Number of periods to shift back (0 = current period). |
| **Rolling N** | For `rolling` type: number of periods to roll over. |
| **Formula Expr** | For `formula` type: cross-column expression (e.g., `C1 - C2`). |

Use the **🗑 Delete** icon to remove any column. Add a new column with the **+ Add Column** button.

### Step 3 — Row Setup
Define each row of the output grid:
| Field | Description |
| :--- | :--- |
| **Label** | Text shown in the output row. |
| **Type** | `section`, `data`, `calc`, or `blank` (see below). |
| **Source / Formula** | For `data` rows: SQL aggregation (e.g., `SUM(amount)`). For `calc` rows: row formula (e.g., `R2 / R3`). |
| **Style** | `normal`, `bold`, `section`, `highlight`, etc. |
| **Indent** | Indent depth level (0–3). |
| **Row Filter** | Optional SQL `WHERE` clause fragment applied only to this row (e.g., `region = 'North'`). |

#### Row Types
- **`section`**: Styled heading row with background color — no data.
- **`data`**: Fetches aggregated values from the database using the `Source` SQL expression.
- **`calc`**: Computes a math formula referencing other row IDs (e.g., `R1 + R2`).
- **`blank`**: Visual spacing row.

Use the **🗑 Delete** icon to remove any row.

### Step 4 — Column Mapping
A grid of checkboxes lets you enable or disable which columns (C1–C7) are active for each row. Unchecked intersections produce empty cells in the output.

---

## 🔍 General Filters

General filters apply a `WHERE` clause to **all** data rows in the report. You can configure them in the Report Builder header section. Supported operators:

| Operator | Example |
| :--- | :--- |
| `=` (equals) | `region = 'North'` |
| `!=` (not equal) | `status != 'cancelled'` |
| `>` / `>=` | `amount > 10000` |
| `<` / `<=` | `quantity < 5` |
| `IN` | `product_category IN ('Electronics', 'Apparel')` |
| `NOT IN` | `region NOT IN ('South')` |
| `LIKE` | `description LIKE '%sale%'` |

The filter attribute and value autocomplete from scanning the selected source table columns.

---

## 🌐 Semantic Layer Viewer

Navigate to the **Semantic** tab in the top menu to inspect the DWH metadata:
- **Explores**: Top-level query entry points (fact tables with their join paths).
- **Joins**: Foreign key relationships and join conditions between tables.
- **Views**: Logical view definitions built on top of base tables.
- **Schema Mapping**: Column-level dimension and measure definitions.

Use the **quick filter** bar on each page to search by name.

---

## 📊 Running a Report

1. From the catalog, click a report card and navigate to **Report Detail** (the ▶ icon).
2. Enter a **Reference Date** (e.g., `2025-12-31`) — all time column offsets are relative to this date.
3. Click **Run** — the backend generates SQL queries, evaluates formulas, renders an Excel workbook, and initiates a download.
