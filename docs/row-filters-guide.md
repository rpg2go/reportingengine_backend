# Row Conditions & Filters Reference Guide

In the Report Template Engine, Row Conditions / Filters are designed to be extremely flexible and are not restricted to just the selected measure field.

Here is exactly how they work:

### 1. Scope of Selectable Tables

When you select a table and a field under the Measure Definition (which sets the active Fact Table, e.g., `fact_sales`), you are allowed to build filters using:
*   **The Selected Fact Table:** (e.g., `fact_sales`).
*   **Any Linked Dimension Tables:** Any dimensional tables that have active joins configured to that Fact Table in the Data Warehouse (DWH) metadata catalog (e.g., `dim_accounts`, `dim_countries`, `dim_relationship_manager`).

### 2. Scope of Selectable Columns

Within any of the allowed tables (the Fact table or its linked Dimension tables), you can add filters on **any columns that exist in that table** (e.g., `iso_code`, `account_type`, `rm_code`, etc.), not just the metric column itself.

---

### Summary Diagram

```text
[Measure Definition]
    ── Selected Fact Table: fact_sales (Metric: amount)

[Row Filters / Conditions]
    ├── Select Table dropdown:
    │     ├── fact_sales (Fact Table) ──> Select ANY Column in fact_sales (e.g. quantity, currency, branch_id)
    │     ├── dim_accounts (Linked Dim) ──> Select ANY Column in dim_accounts (e.g. account_type, status)
    │     └── dim_countries (Linked Dim) ──> Select ANY Column in dim_countries (e.g. region, iso_code)
```

This allows you to slice, dice, and apply complex logical constraints (using recursive `AND` / `OR` groups) across any mapped attributes in the transaction table or its linked dimensions.
