# Report Builder — Feature Reference

> **Last updated:** 2026-07-10  
> **Scope:** Angular front-end `ReportBuilderComponent` (`src/app/components/report-builder.ts`)  
> **Phase:** Phase 1, Phase 2, and Phase 3 completed & polished

---

## Table of Contents

1. [Overview](#1-overview)
2. [Navigation & Layout](#2-navigation--layout)
3. [Section 1 — Core Report Details](#3-section-1--core-report-details)
   - 3.1 Identity Fields
   - 3.2 Reporting Date
   - 3.3 Timeframe Limit
   - 3.4 Linked Dimensions
   - 3.5 Quick Filters
   - 3.6 General Filters
4. [Section 2 — Rows Setup](#4-section-2--rows-setup)
   - 4.1 Row Grid Actions
   - 4.2 Row Fields
   - 4.3 Measure Definition Builder
   - 4.4 Row-Level Filter Builder
   - 4.5 Active Columns Toggle
5. [Section 3 — Columns Setup](#5-section-3--columns-setup)
   - 5.1 Column Grid Actions
   - 5.2 Column Fields
6. [Live Layout Preview](#6-live-layout-preview)
7. [Save & Persistence](#7-save--persistence)
8. [Data Loading & API Calls](#8-data-loading--api-calls)
9. [Payload / Serialization Reference](#9-payload--serialization-reference)

---

## 1. Overview

The Report Builder is a metadata-driven, visual layout editor that lets analysts define the full structure of a parameterized report — without writing SQL. It operates in two modes:

| Mode | Route | Behaviour |
|------|-------|-----------|
| **Create new** | `/builder/new` | Initialises a blank template with sensible defaults |
| **Edit existing** | `/builder/:id` | Loads a saved report definition from the backend and restores all state |

On load the builder fires two backend requests **in parallel** (`forkJoin`) — `GET /api/reports/tables` and `GET /api/reports/:id` — so the perceived wait equals `max(t_tables, t_config)`.

---

## 2. Navigation & Layout

| Element | Description |
|---------|-------------|
| **Sidebar** | Persistent left panel with links to *Reports Catalog* and *Semantic Layer*. On mobile (< 1024 px) the sidebar is hidden and toggled via a hamburger button. |
| **Sidebar overlay** | Semi-transparent backdrop appears on mobile when the sidebar is open; clicking it closes the sidebar. |
| **Breadcrumbs** | `Reports / Builder` — the `Reports` link navigates to `/dashboard`. |
| **Header action bar** | Sticky top bar with **Preview Layout** toggle and **Save Definition** button. |
| **Success / error alerts** | Inline alert banners that appear below the header after a save attempt; success auto-redirects to the report detail page after 1.2 s. |
| **Cancel & Exit** | Prompts the user for confirmation before navigating back to the dashboard (new report) or the report detail page (existing report). |

---

## 3. Section 1 — Core Report Details

### 3.1 Identity Fields

| Field | Type | Notes |
|-------|------|-------|
| **Report ID*** | Text input | Unique identifier (e.g. `RPT_001`). Locked (read-only) when editing an existing report. |
| **Report Title*** | Text input | Human-readable display name. |
| **Version** | Number input | Integer version counter; defaults to `1`. |
| **Report Status** | Select | `draft` or `published`. |
| **Source Table (Fact)*** | Select | Populated from `GET /api/reports/tables`. Selecting a table triggers lazy-loading of its columns and available dimension joins. |
| **Report Granularity*** | Select | Populated from the columns of the selected fact table; defines the SQL `GROUP BY` key. |

---

### 3.2 Reporting Date

- **Source:** `dim_date.reporting_date` — fetched via `GET /api/reports/dimensions/values?table=dim_date&column=reporting_date`.
- **Default value:** today − 1 (applied at runtime when creating a new report or when loading a saved report that has no stored date).
- **UI behaviour:**
  - While `dim_date` dates are loading → shows a native `<input type="date">` fallback.
  - Once dates are available → shows a `<select>` dropdown populated with all distinct `reporting_date` values.
- **Serialized as:** plain `YYYY-MM-DD` string in `reportingDate`.

---

### 3.3 Timeframe Limit

Defines the date window used to scope the data query. Consists of a **start date** and an **end date**.

**Start date:** `<input type="date">` — free calendar/keyboard entry, stored as `YYYY-MM-DD`.

**End date** — four mutually exclusive modes selected via a pill-button group:

| Mode | Button label | Stored as (`timeframeTodayOffset`) | Behaviour |
|------|--------------|------------------------------------|-----------|
| `today_minus_2` | **Today − 2** | `-2` | End date = execution date minus 2 calendar days |
| `today_minus_1` | **Today − 1** | `-1` | End date = execution date minus 1 calendar day |
| `today` | **Today** | `0` | End date = execution date |
| `custom` | **Custom ▾** | `null` | User picks an explicit end date from `dim_date` |

In the three relative modes the computed date is displayed as a read-only badge. In **Custom** mode:
- While `dim_date` dates are loading → shows a native `<input type="date">` fallback.
- Once loaded → shows a `<select>` dropdown populated from `dim_date.reporting_date`.

**Backward compatibility:** The boolean field `timeframeToday` (legacy) is also written to the payload alongside `timeframeTodayOffset` so older backend versions continue to work.

---

### 3.4 Linked Dimensions

- **Shown when:** a fact table is selected AND the semantic layer has dimension join definitions for it.
- **Source:** `GET /api/reports/dimension-joins?factTable=<table>` — returns an array of `{ dimView, joinType, joinSql }`.
- Each dimension appears as a **clickable chip** showing the join type badge (`LEFT`, `INNER`, etc.).
- **Toggling** a chip activates / deactivates that dimension view. Active dimensions:
  - Add their columns to the table selector in Quick Filters, General Filters, and Row-Level Filter builders.
  - Are serialized as a comma-separated list in `linkedDimensions`.
- Dimension columns are **lazy-loaded on first activation** and cached for the session.

---

### 3.5 Quick Filters

Runtime-exposed filter conditions that users can tune when they execute the report.

- **Add** new conditions via the **+ Add Filter Condition** button.
- Each condition row contains:

| Control | Options |
|---------|---------|
| **Table** | Fact table or any activated linked dimension |
| **Column** | All columns of the selected table (lazy-loaded) |
| **Operator** | `is`, `is not`, `in`, `contains`, `=`, `!=`, `>`, `>=`, `<`, `<=` |
| **Value** | Free-text input with autocomplete suggestions fetched from `GET /api/reports/dimensions/values` |
| **Remove (✕)** | Deletes the condition |

- Between consecutive conditions an **AND / OR pill-toggle** appears, letting the user define how conditions are chained.
- **Serialized as:** `JSON.stringify(QuickFilterCondition[])` in `quickFilters`.
- **Backward compatibility on load:** if the stored value is the old comma-separated column-name format, it is parsed and converted to stub conditions (operator `is`, no value) so existing reports are not broken.

---

### 3.6 General Filters

Hard-coded scope constraints applied to every query execution (users cannot change these at runtime).

- Identical UI to Quick Filters (table / column / operator / value / remove) **without** the AND/OR conjunction toggle — all conditions are implicitly AND-combined by the engine.
- **Autocomplete suggestions:** distinct column values are fetched on column selection and cached in `distinctValues`.
- **Serialized as:** `JSON.stringify(FilterCondition[])` in `generalFilters`.

---

## 4. Section 2 — Rows Setup

### 4.1 Row Grid Actions

| Button | Action |
|--------|--------|
| **+ Add Row** | Appends a new `data` row with a `SUM` measure on the first available fact column. |
| **🗑️ Delete Selected** | Bulk-deletes all checked rows after confirmation. |
| **↻ Reset** | Clears all rows after confirmation. |
| **📄 Duplicate** | Appends copies of all checked rows with new IDs and `(Copy)` label suffix. |
| **➔ Reorder** | Sorts all rows by their numeric Row ID suffix (e.g. `R1 < R2 < R10`). |
| **Select-all checkbox** | Checks / unchecks every row simultaneously. |

---

### 4.2 Row Fields

| Column | Description |
|--------|-------------|
| **Checkbox** | Selects the row for bulk operations. |
| **Row ID** | Short alphanumeric key (e.g. `R1`). Editable inline; used as a reference in `calc` row formulas. |
| **Row Name (Label)** | Display label shown in the rendered report. Indent controls (`«` / `»`) shift the visual indent level. |
| **Row Type** | `data` · `calc` · `section` · `blank` — determines which other columns are active. |
| **Visual Style** | `Normal` · `Header` · `Section` · `Total` · `Highlight` · `Blank` — controls the output cell formatting. |
| **Measure Definition** | See §4.3 below. |
| **Row Conditions / Filters** | See §4.4 below. |
| **Active Columns** | See §4.5 below. |
| **🗑️ (row delete)** | Deletes that single row after confirmation. |

---

### 4.3 Measure Definition Builder

Applies only to **`data`** and **`calc`** row types.

**`data` rows** — two input modes, switchable per row:

| Mode | UI | Example output |
|------|----|---------------|
| **Visual builder** | Aggregation dropdown (`SUM`, `COUNT`, `COUNT DISTINCT`, `AVG`, `MIN`, `MAX`) + column picker from the fact table. | `SUM(amount)` |
| **SQL mode** | Free-text code input accepting any valid SQL aggregate expression. | `SUM(CASE WHEN status='A' THEN amount ELSE 0 END)` |

Switching between modes is done with the **SQL** / **⬡ Visual** toggle button on the row.

**`calc` rows** — single free-text input accepting a row-ID arithmetic expression evaluated post-aggregation by the engine.  Example: `R2 / R3`, `(R4 - R5) / R5 * 100`.

**`section`** and **`blank`** rows have no measure definition.

---

### 4.4 Row-Level Filter Builder

Applies only to **`data`** rows. Each row independently carries zero or more filter conditions that narrow **which records** contribute to that row's aggregate.

**Existing conditions** are displayed as inline mini-tags showing `[table.]column operator value`.  Each tag has an ✕ button to remove it.

**Adding a new condition** via **+ Add Condition** opens an inline builder panel containing:

| Control | Options |
|---------|---------|
| **Table** | Fact table or any activated linked dimension |
| **Column** | Columns of the selected table |
| **Operator** | `=`, `!=`, `is`, `is not`, `in`, `contains`, `>`, `>=`, `<`, `<=` |
| **Value** | Free text with autocomplete suggestions fetched from the backend |

Clicking **✓ Add Condition** commits the condition and closes the builder. **Cancel** discards it.

**Legacy badge:** if a row was originally saved with a raw SQL `filterExpr` string (pre-structured format), it is displayed as an amber ⚠️ badge alongside the new structured conditions for backward compatibility.

---

### 4.5 Active Columns Toggle

Each row displays a compact badge for every defined column (`C1`, `C2`, …). Clicking a badge toggles whether that column is active for the row. Active columns render with a green highlight; inactive ones are dimmed. This matrix controls which cells are populated in the output spreadsheet.

---

## 5. Section 3 — Columns Setup

### 5.1 Column Grid Actions

| Button | Action |
|--------|--------|
| **+ Add Col** | Appends a new `WEEK` column with default settings. |
| **🗑️ Delete Selected** | Bulk-deletes checked columns; also removes those column IDs from all rows' `activeCols`. |
| **↻ Reset** | Clears all columns after confirmation. |
| **📄 Duplicate** | Appends copies of checked columns with new IDs and `(Copy)` label suffix. |
| **➔ Reorder** | Sorts columns numerically by their ID suffix. |
| **Select-all checkbox** | Checks / unchecks every column. |

---

### 5.2 Column Fields

| Field | Description |
|-------|-------------|
| **Col ID** | Short alphanumeric key (e.g. `C1`). Referenced by rows' `activeCols` and `calc` row formulas. |
| **Column Name / Header Label** | Display text shown as the column header in the output. |
| **Col Type** | Defines the time-window semantics the engine uses to scope the query for this column: `WEEK`, `MTD`, `YTD`, `ROLLING`, `CALC`. |
| **Header Style** | Visual formatting of the header cell in the output: `Normal`, `Bold Center`, `Bold Border`. |
| **Period Offset** | Integer: how many periods back from the reporting date this column's window is anchored (disabled for `CALC` columns). |
| **Rolling N** | Number of periods in the rolling window (enabled only for `ROLLING` columns). |
| **Formula / Expression** | Cross-column arithmetic evaluated post-aggregation (enabled only for `CALC` columns). Example: `(C1 - C2) / C2`. |

---

## 6. Live Layout Preview

Toggled by the **👁️ Preview Layout** button in the header. When active, a read-only spreadsheet-style table is rendered:

- **Columns:** Label · Row ID · Type · one column per defined column definition.
- **Rows:** all defined rows, styled by their `style` property (header, section, total, highlight, blank, normal).
- **Cell flags:** ✓ (green dot) if the column is active for that row; `-` (grey dash) if not.
- **Indent:** the Label cell is padded left proportionally to `indentLevel`.
- Row type icons: 📂 section · 🧮 calc · 📊 data.

> Note: formula evaluations and actual data values are **not** computed in this preview. They run during Phase 2 (engine compilation).

---

## 7. Save & Persistence

- **Save Definition** button calls `PUT /api/reports/:id` (existing) or `POST /api/reports` (new).
- Mandatory fields validated client-side before the request fires: **Report ID**, **Report Title**, **Source Table**.
- During the save a spinner is shown on the button and it is disabled to prevent double-submission.
- On success: a success alert is shown, then the user is redirected to `/reports/:id` after 1.2 seconds.
- On failure: the backend error message (or a generic fallback) is shown in an error alert.

---

## 8. Data Loading & API Calls

| Trigger | Endpoint | Purpose |
|---------|----------|---------|
| Component init | `GET /api/reports/dimensions/values?table=dim_date&column=reporting_date` | Populate Reporting Date dropdown |
| Component init (edit mode) | `GET /api/reports/tables` + `GET /api/reports/:id` *(parallel)* | Load all table names and the saved report config simultaneously |
| Component init (new mode) | `GET /api/reports/tables` | Load table names for the source-table picker |
| Source table selected | `GET /api/reports/table-columns?table=<name>` | Load fact table columns |
| Source table selected | `GET /api/reports/dimension-joins?factTable=<name>` | Load available dimension join definitions |
| Dimension activated | `GET /api/reports/table-columns?table=<dimView>` | Lazy-load dimension columns (cached) |
| Filter column selected | `GET /api/reports/dimensions/values?table=<t>&column=<c>` | Autocomplete distinct values (cached) |
| Save | `POST /api/reports` or `PUT /api/reports/:id` | Persist the full report definition |

All subscriptions use `takeUntilDestroyed(destroyRef)` to prevent memory leaks on component teardown.

---

## 9. Payload / Serialization Reference

The JSON body sent on save:

```jsonc
{
  "reportId":    "RPT_001",
  "name":        "Weekly Sales Report",
  "version":     1,
  "exploreId":   1,
  "status":      "draft" | "published",
  "sourceTable": "analytics.fact_investments",
  "granularity": "reporting_date",
  "reportingDate": "2026-05-26",          // YYYY-MM-DD

  // Timeframe
  "timeframeStart":       "2022-01-01",   // YYYY-MM-DD
  "timeframeEnd":         "2026-05-26",   // resolved absolute date
  "timeframeTodayOffset": -2 | -1 | 0 | null,  // null = custom absolute date
  "timeframeToday":       false,          // legacy boolean, backward-compat

  // Filters (JSON strings)
  "quickFilters":    "[{\"dimTable\":\"\",\"attribute\":\"rm_id\",\"operator\":\"is\",\"value\":\"42\",\"conjunction\":\"AND\"}]",
  "generalFilters":  "[{\"dimTable\":\"\",\"attribute\":\"status\",\"operator\":\"is\",\"value\":\"active\"}]",
  "linkedDimensions": "dim_relationship_manager,dim_location",  // CSV

  "columns": [
    {
      "colId":        "C1",
      "label":        "Previous Week",
      "colType":      "WEEK" | "MTD" | "YTD" | "ROLLING" | "CALC",
      "periodOffset": -1,
      "rollingN":     null,
      "formulaExpr":  "",
      "displayOrder": 1
    }
  ],

  "rows": [
    {
      "rowId":        "R1",
      "reportId":     "RPT_001",
      "label":        "GBS gross",
      "rowType":      "data" | "calc" | "section" | "blank",
      "source":       "SUM(amount)",        // serialized measure or calc formula
      "parentRowId":  null,
      "style":        "normal" | "header" | "section" | "total" | "highlight" | "blank",
      "indentLevel":  1,
      "displayOrder": 2,
      "activeCols":   ["C1", "C2"],
      "filterExpr":   "[{\"dimTable\":\"\",\"attribute\":\"lifecycle\",\"operator\":\"=\",\"value\":\"2\"}]"
    }
  ]
}
```

### Filter Expression Format (`filterExpr` / `quickFilters` / `generalFilters`)

All filter arrays are serialized as JSON strings. Each condition object:

```jsonc
{
  "dimTable":    "",           // empty = fact table; otherwise dim view name
  "attribute":   "lifecycle",  // column name
  "operator":    "=",          // is | is not | in | contains | = | != | > | >= | < | <=
  "value":       "2",
  "conjunction": "AND"         // AND | OR — present on QuickFilterCondition only
}
```

Legacy rows may carry a raw SQL string in `filterExpr` (pre-structured format). The front-end detects this on load and displays it as a read-only ⚠️ badge alongside any structured conditions.
