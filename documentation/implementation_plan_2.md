# Meaningful Report Identifiers (Phase B4)

The goal is to transition from generic report IDs (`RPT_01`, `RPT_02`) to descriptive, business-oriented names across the entire reporting pipeline. This includes updating the Excel template and performing a destructive reset of the `reporting` metadata in the database to prevent duplicate entries.

## User Review Required

> [!WARNING]
> - **Destructive Reset**: I will `TRUNCATE` the `reporting.rpt_report` table (cascade). This will delete all existing report definitions. This is necessary because `report_id` is the primary key, and changing it causes "ghost" old reports to remain in the database unless cleared.
> - **Excel IDs**: All 11 reports in `10_reports_showcase.xlsx` will be renamed.

### Proposed Mapping (Showcase)
| Original ID | New Meaningful ID |
| :--- | :--- |
| RPT_01 | **SALES_OVERVIEW** |
| RPT_02 | **CATEGORY_ANALYSIS** |
| RPT_03 | **CUSTOMER_INSIGHTS** |
| RPT_04 | **REGIONAL_DISTRIBUTION** |
| RPT_05 | **CASH_FLOW_SUMMARY** |
| RPT_06 | **LOAN_EXPOSURE** |
| RPT_07 | **INVESTMENT_AUM** |
| RPT_08 | **BUDGET_CONTROL** |
| RPT_09 | **BRANCH_PERFORMANCE** |
| RPT_10 | **EXEC_SUMMARY** |
| RPT_11 | **FINANCIAL_360_KPI** |

## Proposed Changes

### 1. Spreadsheet Transformation
- Create a Python script (`rename_template_reports.py`) to search and replace `RPT-XX` with the new IDs in **Column A** (Report ID) of the `REPORT_DEFINITION` sheet.

### 2. Database Reset & Re-Import
- Create a script (`reset_and_import.py`) that:
    1. Connects to PostgreSQL.
    2. Executes `TRUNCATE reporting.rpt_report RESTART IDENTITY CASCADE`.
    3. Runs `ExcelParser.run()` on the updated template.

### 3. Execution Verification
- Update `run_report.py` (if hardcoded) to handle the new ID strings. (Actually, `run_report.py` iterates over output from `parse_all()`, so it should be dynamic).

## Open Questions

- **Do you agree with the 11 new IDs listed above?**

## Verification Plan

### Automated Verification
- Run `python reset_and_import.py`.
- Verify the `reporting.rpt_report` table contains `SALES_OVERVIEW` instead of `RPT_01`.
- Run the 11-report showcase using the new IDs.
