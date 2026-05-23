# Business User Guide: Defining New Financial Reports

This guide explains how to define, modify, and deploy new financial reports using the **DWH Reporting Engine** Excel template.

## 📁 The Primary File
All report definitions are stored in:
`report_template/template/dwh_reports_showcase.xlsx`

---

## 🔍 How to Find Available Data
Before defining a report, you need to know which **Metric Keys** (Total Revenue, Net Flow, etc.) and **Dimensions** (Region, Product Category) are currently active in the warehouse.

1.  Open `dwh_reports_showcase.xlsx`.
2.  Navigate to the **`DATA_CATALOG`** worksheet (the first tab).
3.  **Metrics**: Use the keys in the `Source Key` column for your `data` rows.
4.  **Dimensions**: Use the `Dimension Column` names if you need to specify filters in the semantic layer (advanced).

> [!TIP]
> The `DATA_CATALOG` is refreshed automatically from the database. If you add a new metric to the warehouse, just run the refresher script: `python refresh_data_catalog.py`.

---

## 🎯 Data Filtering (Advanced)
You can filter any `data` row to only include a specific subset of records. This is done by adding a standard SQL condition to the **`filter`** column (Column **O** or 15 in the template).

**Examples:**
- **Ticker Filter**: `ticker_symbol IN ('META', 'MSFT')`
- **Loan Type**: `loan_type = 'personal'`
- **Amount Limit**: `amount > 10000`

### 🔗 Multi-Condition Logic
You can combine multiple criteria using `AND` or `OR` for more precise segmentation.

**Advanced Examples:**
- **Regional Segment**: `region = 'North' AND segment = 'Corporate'`
- **High-Value Risk**: `loan_type = 'personal' AND amount > 50000`
- **Territory Split**: `region IN ('North', 'East') OR segment = 'SME'`

> [!IMPORTANT]
> The filter must be a valid SQL `WHERE` clause fragment. Check the `DATA_CATALOG` for valid column names.

---

## 📈 Row Definition Structure (Section B)
Navigate to row **10** of the `REPORT_DEFINITION` sheet. Each new report is defined by a block of rows sharing a unique **Report ID**.

### 1. Mandatory Columns
| Column | Name | Description |
| :--- | :--- | :--- |
| **A** | `report_id` | Descriptive unique ID (e.g., `SALES_KPI`). |
| **B** | `row_id` | Short ID (e.g., `R1`) used for calculations. |
| **D** | `label` | The text displayed in the final Excel output. |
| **E** | `type` | The row behavior (Section, Data, Calc, etc.). |
| **F** | `source` | The measure name OR the calculation formula. |
| **I - N** | `C1 - C6` | Place an **'X'** to enable this row for specific columns. |
| **O** | `filter` | **[NEW]** SQL filter (e.g. `loan_type='personal'`). |

### 2. Valid Row Types
*   **`section`**: Heading row (styled with background color).
*   **`data`**: Fetches results from the database. Use a **Metric Key** as the source.
*   **`calc`**: High-performance vertical math. References other `row_id`s (e.g., `R1 + R2`).
*   **`blank`**: Adds a visual spacing row.
*   **`header`**: Global report title.

---

## 🏗️ Available Metadata Keys (Source Metrics)
When creating a **`data`** row, use one of these verified keys in the **Source** column:

### Sales Domain
- `total_revenue`: Total amount of sales.
- `total_quantity_sold`: Count of units sold.
- `avg_order_value`: Mean revenue per transaction.

### Banking & Cash Flow
- `total_credits`: Sum of all deposit transactions.
- `total_debits`: Sum of all withdrawal transactions.
- `net_cash_flow`: Credits minus Debits (Calculated).
- `transaction_count`: Volume of banking activity.

---

## 🚀 Deployment Process
1.  **Modify**: Add your new rows to `dwh_reports_showcase.xlsx`.
2.  **Save**: Ensure Excel is saved and closed.
3.  **Sync**: Run this command to update the database:
    ```bash
    python report_template/sample/import_showcase.py
    ```
4.  **Execute**: Run the report generation:
    ```bash
    python report_template/sample/run_report.py
    ```
