# Project Handoff - Report Template Engine & Builder UI

This document serves as the architecture reference, implementation state, and configuration blueprint for all developer/agent interactions on this workspace.

---

## 📌 Context Overview

- **Objective**: Build a metadata-driven orchestration reporting engine and visual layout builder.
- **Phase 1 (Completed)**: Visual Report Builder UI & JPA Database Persistence (direct schema editing).
- **Phase 2 (Pending)**: SQL Compilation, Dynamic Row Filters Assembly, and Excel Rendering. See Phase 2 Roadmap below.

---

## 🛠️ Technology Stack

| Component | Technology | Version / Port / Details |
| :--- | :--- | :--- |
| **Backend** | Spring Boot | v3.2.4 (Java 17), Spring Data JPA, Hibernate, exp4j |
| **Frontend** | Angular | v21.2.0 (Standalone components, TypeScript) |
| **Styling** | CSS | Vanilla CSS deep-slate dark-mode design |
| **Database** | PostgreSQL | v16 (Running in Docker container `report_template_db` on port `5432`) |

---

## 📐 Key Design Decisions (Phase 1)

1. **Semantic Layer Bypassed**:
   We completely bypassed the lookups to `reporting.sem_*` metadata tables.
   - Reports bind directly to physical database tables in the Data Warehouse (e.g., `analytics.fact_sales`).
   - KPIs and metrics on rows do not map to logical measure references. Instead, the raw SQL aggregation expression (e.g., `SUM(amount)`) is typed directly in the row's `source` column.
   - Autocomplete options for dimensional filters are fetched dynamically by scanning tables and columns in the `analytics` schema directly.
2. **State & Cascade Overwrite**:
   Saving configurations replaces all rows and columns. We perform a cascade delete on child tables (`ColumnDef`, `ReportRow`, `RowMetric`, `RowFormula`, `RowColumnMap`) and flush the session before updating the main `Report` header row.
3. **Direct JDBC for Hot-Path Reads**:
   The `loadFromDb()` method in `ReportConfigService.java` was rewritten to use direct JDBC queries with `RowCallbackHandler` instead of 6 sequential JPA repository calls. This eliminated Hibernate entity-hydration overhead and unnecessary JOINs (e.g., `rpt_column_def` joining back to `rpt_report`), reducing the report config endpoint latency from ~163ms to ~59ms.
4. **Parallel Frontend Loading**:
   Angular's `ngOnInit` in `report-builder.ts` uses RxJS `forkJoin` to send `/api/reports/tables` and `/api/reports/{id}` requests concurrently. The perceived load time equals `max(t_tables, t_config)` instead of their sum.
5. **127.0.0.1 over localhost**:
   All frontend API calls use `http://127.0.0.1:8101` instead of `http://localhost:8101`. On Windows, `localhost` triggers IPv6 DNS resolution before IPv4 fallback, adding 1-2 seconds of latency per request in the browser.

---

## 🗄️ Database Schemas

### 1. `reporting` Schema

Stores metadata and report configuration templates.

| Table Name | Primary Key | Foreign Keys / Description |
| :--- | :--- | :--- |
| `rpt_report` | `report_id` | Stores report header: `name`, `version`, `status`, `source_table` (fact table), `granularity` (grouping column), `timeframe_start`, `timeframe_end`, `timeframe_today`, `quick_filters`, `general_filters` (JSON string representation). |
| `rpt_column_def` | `col_id` | References `report_id`. Defines C1..C7 time columns with offset, rolling period, and formulas. |
| `rpt_row` | `row_id` | References `report_id`. Defines rows with labels, indent levels, row types (`section`, `data`, `calc`, `blank`), styles, and row-level `filter_expr` strings. |
| `rpt_row_metric` | `row_id` | References `rpt_row.row_id`. Links `data` row to `sql_expr` (the SQL aggregation expression). |
| `rpt_row_formula` | `row_id` | References `rpt_row.row_id`. Links `calc` row to `formula_expr` (e.g. `R2/R3`). |
| `rpt_row_column_map` | `(row_id, col_id)`| References `rpt_row` and `rpt_column_def`. Grid intersections showing active columns for each row. |

### 2. `analytics` Schema

Represents the physical Data Warehouse (DWH) containing dimension and fact tables (seeding transaction, performance, investment, and sales data for 2024–2026).

---

## 🔌 API Endpoints (`ReportController.java`)

All backend APIs are prefixed with `/api` and listen on port `8101`.

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/reports` | Lists all catalog reports. |
| **GET** | `/api/reports/{id}` | Loads a single report definition config DTO. |
| **POST** | `/api/reports` | Creates a new report config. |
| **PUT** | `/api/reports/{id}` | Updates an existing report config. |
| **GET** | `/api/reports/tables` | Returns list of physical tables in the `analytics` schema. |
| **GET** | `/api/reports/table-columns` | Returns column list for a table (e.g., `?table=fact_sales`). |
| **GET** | `/api/reports/dimensions/values` | Performs lookups for unique autocomplete value lists: `SELECT DISTINCT <column> FROM <table> WHERE <column> IS NOT NULL ORDER BY <column> LIMIT 100` (e.g., `?table=fact_sales&column=region`). |
| **DELETE** | `/api/reports/{id}` | Deletes a report and all child rows/columns. |
| **POST** | `/api/reports/import` | Imports an Excel `.xlsx` template file (multipart). |
| **POST** | `/api/reports/{id}/run` | Executes report generation and returns `.xlsx` download. |

---

## 📂 Codebase Tour

Use the links below to navigate directly to the primary components:

### Backend Services & Controllers

- **Controllers**:
  - [ReportController.java](file:///G:/workspace/ReportTemplate/src/main/java/com/reporting/controller/ReportController.java) — Exposes metadata, table lists, columns, and autocomplete endpoint endpoints.
  - [AuthController.java](file:///G:/workspace/ReportTemplate/src/main/java/com/reporting/controller/AuthController.java) — Manages login validation.
- **Core Engine Services**:
  - [SqlGeneratorService.java](file:///G:/workspace/ReportTemplate/src/main/java/com/reporting/service/SqlGeneratorService.java) — Compiles report filters, fact tables, and rolling date boundaries into dynamic CTE queries.
  - [PostProcessorService.java](file:///G:/workspace/ReportTemplate/src/main/java/com/reporting/service/PostProcessorService.java) — Evaluates mathematical formulas at row/column intersections using `exp4j`.
  - [LayoutRendererService.java](file:///G:/workspace/ReportTemplate/src/main/java/com/reporting/service/LayoutRendererService.java) — Renders POI styles, grid alignments, fonts, colors, and formatting into downloading templates.
  - [ExcelParserService.java](file:///G:/workspace/ReportTemplate/src/main/java/com/reporting/service/ExcelParserService.java) — Handles the extraction and ingestion of spreadsheet configurations.

### Frontend Component Views

- **Views (`frontend/src/app/components/`)**:
  - [login.ts](file:///G:/workspace/ReportTemplate/frontend/src/app/components/login.ts) — Authentication page login.
  - [dashboard.ts](file:///G:/workspace/ReportTemplate/frontend/src/app/components/dashboard.ts) — Main catalog page and spreadsheet template uploader.
  - [report-builder.ts](file:///G:/workspace/ReportTemplate/frontend/src/app/components/report-builder.ts) — Interactive drag-and-drop report layout editor.
  - [report-detail.ts](file:///G:/workspace/ReportTemplate/frontend/src/app/components/report-detail.ts) — Layout inspector and run generator execution view.
  - [semantic.ts](file:///G:/workspace/ReportTemplate/frontend/src/app/components/semantic.ts) — Explores, joins, and measures viewer.

---

## 🚀 How to Bootstrap the Dev Environment

### 1. Database (Docker)

Spin up the container from the project root:
```bash
docker-compose down -v
docker-compose up --build -d
```
*Port: `5432` | DB: `agentic_ai` | User: `user` | Pass: `password`*

### 2. Spring Boot Backend

Start the backend on port `8101` using the Maven wrapper:
- **Windows**:
  ```cmd
  maven\apache-maven-3.9.6\bin\mvn.cmd clean compile
  maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
  ```
- **macOS/Linux**:
  ```bash
  ./maven/apache-maven-3.9.6/bin/mvn clean compile
  ./maven/apache-maven-3.9.6/bin/mvn spring-boot:run
  ```

### 3. Angular SPA Frontend

Start the frontend on port `4200`:
```bash
cd frontend
npm install
npm start
```

---

## 🧭 Phase 2 Implementation Roadmap

When you are tasked with starting Phase 2, follow this development sequence:

1. **SQL Compilation (`SqlGeneratorService.java`)**:
   - Ingest the report's `source_table`.
   - Read the row's custom SQL aggregation (`sql_expr` e.g., `SUM(amount)`).
   - Apply row-level `filter_expr` as well as report-level `general_filters` (from `rpt_report.general_filters` JSON list).
   - Build date boundaries for columns based on column offsets/rolling settings relative to the reference date, and combine them all inside query CTE definitions.
2. **Calculation Evaluation (`PostProcessorService.java`)**:
   - Evaluate row-level formulas (e.g., `R2 / R3`) and column-level formulas (e.g., `C2-C3`) using a post-processor parser (like `exp4j` which is already in `pom.xml`).
3. **Excel Rendering (`LayoutRendererService.java`)**:
   - Format, style, and print the finished dataset into the download spreadsheet templates.
