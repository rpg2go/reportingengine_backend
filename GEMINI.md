# Project Handoff - Report Template Engine Back-End

This document serves as the architecture reference, implementation state, and configuration blueprint for all developer/agent interactions on this backend workspace.

---

## 📌 Context Overview

- **Objective**: Build a metadata-driven orchestration reporting engine.
- **Phase 1 (Completed)**: JPA Database Persistence, migration utilities, and Direct JDBC access optimization.
- **Phase 2 (Completed)**: SQL Compilation, Dynamic Row Filters Assembly, and Excel Rendering.
- **Phase 3 (In Progress)**: Validation, Verification, and Polish. See Phase 3 Roadmap below.

---

## 🛠️ Technology Stack

| Component | Technology | Version / Port / Details |
| :--- | :--- | :--- |
| **Backend** | Spring Boot | v3.2.4 (Java 17), Spring Data JPA, Hibernate, exp4j |
| **Database** | PostgreSQL / Neon | v16 (Local Docker container on `5432`, production on Neon cloud) |

---

## 📐 Key Design Decisions

1. **Semantic Layer Bypassed**:
   We bypassed lookups to `reporting.sem_*` metadata tables.
   - Reports bind directly to physical database tables in the Data Warehouse (e.g., `analytics.fact_sales`).
   - KPIs and metrics on rows do not map to logical measure references. Instead, the raw SQL aggregation expression (e.g., `SUM(amount)`) is typed directly in the row's `source` column.
   - Autocomplete options for dimensional filters are fetched dynamically by scanning tables and columns in the `analytics` schema directly.
2. **State & Cascade Overwrite**:
   Saving configurations replaces all rows and columns. We perform a cascade delete on child tables (`ColumnDef`, `ReportRow`, `RowMetric`, `RowFormula`, `RowColumnMap`) and flush the session before updating the main `Report` header row.
3. **Direct JDBC for Hot-Path Reads**:
   The `loadFromDb()` method in `ReportConfigService.java` was rewritten to use direct JDBC queries with `RowCallbackHandler` instead of 6 sequential JPA repository calls. This eliminated Hibernate entity-hydration overhead and unnecessary JOINs (e.g., `rpt_column_def` joining back to `rpt_report`), reducing the report config endpoint latency from ~163ms to ~59ms.
4. **Database Migrations and Tracking**:
   Database migration execution is managed by a custom utility `MigrationRunner.java`. It tracks applied SQL files (`000_*.sql` to `008_*.sql`) in the `reporting.schema_migrations` table.
   - **Pre-Population Logic**: If database tables already exist (e.g. from local runs or Hibernate initialization), the runner automatically registers migrations `000` to `007` to skip execution, avoiding schema duplication errors.
   - **Report Seeds**: The migration `008_seed_report_templates.sql` seeds the system with 14 production report configurations.

---

## 🗄️ Database Schemas

### 1. `reporting` Schema
Stores metadata and report configuration templates.

| Table Name | Primary Key | Foreign Keys / Description |
| :--- | :--- | :--- |
| `rpt_report` | `report_id` | Stores report header: `name`, `version`, `status`, `source_table`, `granularity`, `timeframe_start`, `timeframe_end`, `timeframe_today`, `quick_filters`, `general_filters` (JSON). |
| `rpt_column_def` | `col_id` | References `report_id`. Defines time columns with offset, rolling period, and formulas. |
| `rpt_row` | `row_id` | References `report_id`. Defines rows with labels, indent levels, row types (`section`, `data`, `calc`, `blank`), styles, and row-level `filter_expr` strings. |
| `rpt_row_metric` | `row_id` | References `rpt_row.row_id`. Links `data` row to `sql_expr` (the SQL aggregation expression). |
| `rpt_row_formula` | `row_id` | References `rpt_row.row_id`. Links `calc` row to `formula_expr` (e.g. `R2/R3`). |
| `rpt_row_column_map` | `(row_id, col_id)`| References `rpt_row` and `rpt_column_def`. Grid intersections showing active columns for each row. |
| `schema_migrations` | `migration_name`| Tracks applied database migration filenames. |

### 2. `analytics` Schema
Represents the physical Data Warehouse (DWH) containing dimension and fact tables (seeding transaction, performance, investment, and sales data for 2024–2026).

---

## 🔌 API Endpoints (`ReportController.java`)

All backend APIs are prefixed with `/api` and listen on port `8101`.

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/auth/login` | Validates Basic Auth and returns authenticated user info. |
| **GET** | `/api/reports` | Lists all catalog reports. |
| **GET** | `/api/reports/{id}` | Loads a single report definition config DTO. |
| **POST** | `/api/reports` | Creates a new report config. |
| **PUT** | `/api/reports/{id}` | Updates an existing report config. |
| **POST** | `/api/reports/import` | Imports an Excel `.xlsx` template file (multipart). |
| **POST** | `/api/reports/{id}/run` | Executes report generation and returns direct `.xlsx` download. |
| **POST** | `/api/reports/{reportId}/execute` | Executes report and returns raw unpivoted grid coordinate values. |
| **POST** | `/api/reports/preview-sql` | Dry-run query compilation to preview the generated SQL structure. |
| **POST** | `/api/reports/validate` | Runs structural and semantic analysis to find configuration issues. |
| **GET** | `/api/reports/tables` | Returns list of physical tables in the `analytics` schema. |
| **GET** | `/api/reports/table-columns` | Returns column list for a table (e.g., `?table=fact_sales`). |
| **GET** | `/api/reports/column-types` | Returns database column types map for a given table. |
| **GET** | `/api/reports/dimensions/values` | Autocomplete distinct values for a table column. |
| **GET** | `/api/reports/dimension-joins` | Fetches join metadata from explore structures for a fact table. |
| **GET** | `/api/reports/semantic-model` | Fetches the complete metadata explore/view/dimension/measure model. |
| **GET** | `/api/metadata/distinct-values` | Security-validated distinct values search for dimension columns. |

---

## 📂 Codebase Tour

Use the links below to navigate directly to the primary components:

### Backend Services & Controllers

- **Controllers**:
  - [ReportController.java](src/main/java/com/reporting/controller/ReportController.java) — Exposes metadata, table lists, columns, and autocomplete endpoints.
  - [AuthController.java](src/main/java/com/reporting/controller/AuthController.java) — Manages login validation.
  - [ReportExecutionController.java](src/main/java/com/reporting/controller/ReportExecutionController.java) — Orchestrates raw cell query executions.
  - [ReportPreviewController.java](src/main/java/com/reporting/controller/ReportPreviewController.java) — Previews dry-run generated SQL queries.
  - [MetadataController.java](src/main/java/com/reporting/controller/MetadataController.java) — Provides security-sanitized distinct autocomplete values.
- **Core Engine Services**:
  - [SqlGeneratorService.java](src/main/java/com/reporting/service/SqlGeneratorService.java) — Compiles report filters, fact tables, and rolling date boundaries into dynamic CTE queries.
  - [PostProcessorService.java](src/main/java/com/reporting/service/PostProcessorService.java) — Evaluates mathematical formulas at row/column intersections using `exp4j`.
  - [LayoutRendererService.java](src/main/java/com/reporting/service/LayoutRendererService.java) — Renders POI styles, grid alignments, fonts, colors, and formatting into downloading templates.
  - [ExcelParserService.java](src/main/java/com/reporting/service/ExcelParserService.java) — Handles extraction and ingestion of spreadsheet configurations.
  - [ReportValidationService.java](src/main/java/com/reporting/service/ReportValidationService.java) — Validates cycle detections, schema checks, and expressions.
- **Database Utilities**:
  - [MigrationRunner.java](src/main/java/com/reporting/util/MigrationRunner.java) — Custom Java runner applying SQL migrations with tracking.
  - [DbDumper.java](src/main/java/com/reporting/util/DbDumper.java) — Helper utility to dump local reporting templates into migration files.

---

## 🚀 How to Bootstrap the Dev Environment

### 1. Database (Docker)
Spin up the local container from the project root:
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

### 3. Run Database Migrations Manually
To apply migrations against a Postgres database using the compiled runner:
```cmd
maven\apache-maven-3.9.6\bin\mvn.cmd compile exec:java "-Dexec.mainClass=com.reporting.util.MigrationRunner" "-Dexec.args=YOUR_DATABASE_URL"
```

---

## 🧭 Phase 3 Validation & Polish Roadmap

When you are tasked with starting Phase 3, follow this development sequence:

1. **Frontend Integration & UI Enhancements**:
   - Update report detail view to show a loading spinner and live status badges during execution runs.
   - Fix "Rolling In" field behavior in the Step 2 column setup wizard.
2. **Edge Case Validation**:
   - Test handling of mathematical divide-by-zero, cyclic formula references, and missing metrics.
   - Refine unit tests to expand coverage metrics.
3. **Advanced Filtering**:
   - Extend general filters builder to support free-text SQL conditions (e.g., `amount > 1000`).
