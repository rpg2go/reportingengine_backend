# Software Requirements Specification (SRS) — Metadata-Driven Reporting Engine

This document details the original and compiled software requirements used to design and build the metadata-driven report configuration and execution engine.

---

## 1. FUNCTIONAL REQUIREMENTS

### 1.1 Template Ingestion & Metadata Mapping

*   **REQ-001**: The system must accept binary Excel `.xlsx` templates uploaded via a multipart REST endpoint (`POST /api/reports/import`).
*   **REQ-002**: The parser must extract:
    *   **Report Metadata**: Name, ID, granularity, and general filters.
    *   **Column Definitions**: Column ID (C1, C2, etc.), label, type (`WEEK`, `MTD`, `YTD`, `ROLLING`, `CALC`), offset, and calculated formulas.
    *   **Row Definitions**: Row ID (R1, R2, etc.), label, hierarchy level (`indent_level`), formatting style (`header`, `section`, `normal`, `total`, `highlight`), type (`section`, `data`, `calc`, `blank`), row filter expression, and SQL or algebraic source expressions.
*   **REQ-003**: The ingestion engine must support transaction rollback. Ingesting a new file must perform a cascade overwrite: deleting existing metadata configurations for that `report_id` and writing new ones.

### 1.2 Data Warehouse & Schema Binding

*   **REQ-004**: The database must follow a decoupled schema architecture:
    *   `reporting` schema: Holds system configuration and report metadata tables.
    *   `analytics` schema: Stores physical dimension and fact tables representing the Data Warehouse.
*   **REQ-005**: The engine must bind metrics directly to physical tables in the `analytics` schema (e.g., `analytics.fact_sales`). The logical measure mapping layer is bypassed; metrics are written directly as physical SQL aggregation expressions (e.g. `SUM(amount)`).
*   **REQ-006**: The system must expose helper metadata endpoints to list physical tables, list columns of a selected table, and dynamically fetch unique values of a selected column (to support builder catalog autocompletes).

### 1.3 Dynamic SQL Compilation

*   **REQ-007**: The SQL compiler must assemble fact table queries into modular Common Table Expressions (CTEs) grouped by Fact/Explore ID.
*   **REQ-008**: The SQL compiler must automatically compute date boundaries (start date and end date) for each time-based column relative to a given `referenceDate` using calendar intelligence:
    *   `WEEK`: Limits date ranges to Monday through Sunday of the offset week.
    *   `MTD`: Limits ranges from the 1st of the offset month to the month's final day.
    *   `YTD`: Limits ranges from January 1st to December 31st of the offset year.
    *   `ROLLING`: Limits ranges starting from `referenceDate - N` periods to the current reference date.
*   **REQ-009**: The compilation engine must wrap metric sql expressions in conditional aggregations using CASE WHEN templates:
    `SUM(CASE WHEN date >= :start AND date <= :end AND (row_filter) THEN metric_column ELSE 0 END)`
*   **REQ-010**: Report-level general filters must compile into standard SQL conditions and be injected into the `WHERE` clauses of the respective CTEs.

### 1.4 Post-Processing Calculation Engine

*   **REQ-011**: Calculations must resolve grid cell intersections in-memory using `exp4j` following a strict order of operations:
    1.  **Hydration**: Retrieve data values from SQL query results.
    2.  **Horizontal Calculations**: Evaluate formulas for calculated columns (`CALC`) row-by-row (e.g., `C3 = (C1 - C2) / C2`).
    3.  **Vertical Calculations**: Evaluate formulas for calculated rows (`calc`) column-by-column (e.g., `R4 = R2 - R3`).
*   **REQ-012**: Vertical row calculations must execute in **3 iterative loops** to resolve nested/multi-level cell dependencies (e.g., cell formula referencing a cell that contains another formula).
*   **REQ-013**: The engine must implement division-by-zero safeguards. Any math operation resulting in division-by-zero, NaN, infinite values, or syntax errors must gracefully assign a fallback value of `0.0`.
*   **REQ-014**: Variable lookups inside formulas must be case-insensitive, standardizing row/column IDs in uppercase (e.g. `R1`, `C1`).

### 1.5 Excel Layout & Style Rendering

*   **REQ-015**: The rendering service must compile post-processed cell matrices into formatted binary Excel streams (.xlsx) using Apache POI.
*   **REQ-016**: The layout engine must apply style overrides dynamically based on row style configurations:
    *   `header`: Dark Slate Blue background (`#1B4F72`), white bold text, thin bottom border.
    *   `section`: Light Blue background (`#D6EAF8`), bold slate blue text, thin top and bottom borders.
    *   `total`: Very light blue background (`#EBF5FB`), bold text, thin top border and double bottom border.
    *   `highlight`: Yellow background (`#FFDC00`), dark red text (`#85144b`), bold, thin top and bottom borders.
*   **REQ-017**: All numeric values must format dynamically as currency using the pattern `#,##0.00_);(#,##0.00)`.
*   **REQ-018**: Text formatting must apply spaces prepended to labels matching the row `indent_level` (e.g., two spaces per indent unit).
*   **REQ-019**: Columns must automatically size to fit their values.

---

## 2. NON-FUNCTIONAL REQUIREMENTS

### 2.1 Performance & Latency

*   **NFR-001**: Database metadata loading (`loadFromDb`) latency must be optimized to execute under **60ms** by using direct JDBC queries with `RowCallbackHandler` to bypass Hibernate entity-hydration and relationship joint overhead.
*   **NFR-002**: The frontend must load catalog parameters in parallel (e.g. combining catalog endpoints with RxJS `forkJoin`) to optimize perceived client load time.
*   **NFR-003**: Verbose Hibernate logging must be disabled in production configurations (`show-sql=false`, logging level set to `WARN` or `ERROR`) to reduce server execution latency.

### 2.2 Security & Validation

*   **NFR-004**: Access to REST controller endpoints must be protected using a Spring Security Basic Authentication filter verifying credentials against database-stored usernames and passwords.
*   **NFR-005**: Cross-Origin Resource Sharing (CORS) configurations must whitelist the active development port origin (`http://127.0.0.1:4200`).
*   **NFR-006**: The engine must protect against SQL injection vulnerabilities:
    *   Input table and column parameters must match whitelists (`^[a-zA-Z0-9_]+$`).
    *   Row and column filter expressions must pass a parenthetical balance validation.
    *   Unsafe SQL sequences must trigger `IllegalArgumentException` (banned characters and keywords include: `;`, `--`, `/*`, `UNION`, `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `TRUNCATE`).

### 2.3 Quality Assurance & Verification

*   **NFR-007**: Unit testing coverage must exceed 90% for core calculation services and 85% for REST controllers.
*   **NFR-008**: The verification environment must isolate unit tests from Spring Boot container bootstraps.
*   **NFR-009**: Integration tests must execute against a running local PostgreSQL instance and use transactional annotations to auto-rollback changes.
