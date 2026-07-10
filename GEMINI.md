# Project Handoff - Report Template Engine Back-End

This document serves as the architecture reference, implementation state, and configuration blueprint for all developer/agent interactions on this backend workspace.

---

## 📌 Context Overview

- **Objective**: Build a metadata-driven orchestration reporting engine.
- **Phase 1 (Completed)**: JPA Database Persistence, migration utilities, and Direct JDBC access optimization.
- **Phase 2 (Completed)**: SQL Compilation, Dynamic Row Filters Assembly, and Excel Rendering.
- **Phase 3 (Completed)**: Validation, Verification, and Polish.

---

## 🛠️ Technology Stack

| Component | Technology | Version / Port / Details |
| :--- | :--- | :--- |
| **Backend** | Spring Boot | v3.5.0-SNAPSHOT (Java 21), Spring Data JPA, Hibernate, exp4j, virtual threads enabled |
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
   We transitioned from custom Java-based migration scripts to a pure-SQL Liquibase migration architecture.
   - **Changelog ledger**: Changesets are declared in native SQL files under `db/liquibase/sql/` and loaded dynamically via `db/liquibase/db.changelog-master.xml`.
   - **Automatic execution**: Applied automatically at application startup via Spring Boot's integrated Liquibase auto-configuration (which executes before Hibernate validation runs).
   - **Manual execution**: Managed via `./scripts/deploy-liquibase.sh [local|neon|url]` sourcing connection parameters from `.env`.
   - **Clean rebuild step**: If developers encounter checksum mismatches (from updating seeding files) or want to reset their local schemas, they can clean the database with:
     ```sql
     DROP SCHEMA IF EXISTS reporting CASCADE;
     DROP SCHEMA IF EXISTS analytics CASCADE;
     DROP TABLE IF EXISTS public.databasechangelog;
     DROP TABLE IF EXISTS public.databasechangeloglock;
     ```
     and re-run `./scripts/deploy-liquibase.sh local`.
5. **Catalog-Driven Join Graph (SchemaGraphRouter)**:
   The `catalog` package (`SchemaCatalogLoader`, `SchemaGraphRouter`, `MetaTable`, `MetaColumn`, `MetaRelationship`) loads the `meta_*` schema catalog at startup into an in-memory graph and resolves multi-hop LEFT JOIN chains using a weighted Dijkstra BFS. Edge cost 1 = conformed dimension key, cost 2 = non-conformed FK. This replaces hardcoded join strings from the `sem_*` era.
6. **Report Version Lifecycle**:
   `ReportVersionController` manages a draft → in_review → published → (fork) lifecycle. Publishing a version auto-creates the next draft by cloning all child rows, columns, metrics, formulas, and column maps via direct JDBC `INSERT … SELECT` statements.
7. **Unified Package Structure**:
   Consolidated all backend source files under the unified package `com.reporting.*`. The legacy package structure under `com.banking.reporting` (including `HierarchicalColumnDto` and `ExcelExporterService`) was completely removed to avoid split packages and simplify import maps.
8. **Sealed AST Filter Compiler**:
   Introduced a dedicated `FilterCompilerService` using modern Java 21 Record structures and a sealed hierarchy (`FilterNode` permitting `RuleNode` and `GroupNode`) to represent row-level dynamic filter configurations, performing AST-to-SQL compilation via switch pattern matching.
9. **Conditional Soft / Hard Deletion**:
   Configured a database column `deleted` and custom logic in `ReportConfigService`. If a report has at least one version with status `PUBLISHED`, deletion soft-deletes the record (marking `deleted = true` across all versions and filtering them out of all repository queries). If the report has never been published (only `DRAFT` or `IN_REVIEW`), it performs a physical cascade delete.
10. **Lombok Dependency Upgrade**:
    Upgraded `lombok` dependency in `pom.xml` to `1.18.38` to resolve internal javac compatibility issues under JDK 21 compiler updates.

---

## 🗄️ Database Schemas

### 1. `reporting` Schema

Stores metadata and report configuration templates.

| Table Name | Primary Key | Foreign Keys / Description |
| :--- | :--- | :--- |
| `rpt_report` | `(report_id, version)` | Stores report header: `name`, `version`, `status`, `source_table`, `granularity`, `timeframe_start`, `timeframe_end`, `timeframe_today`, `quick_filters`, `general_filters` (JSON), and `deleted` (boolean, soft-delete flag). |
| `rpt_column_def` | `column_def_id` | References `(report_id, version)`. Defines time columns with offset, rolling period, and formulas. Unique constraint on `(report_id, version, col_id)`. |
| `rpt_row` | `(report_id, version, row_id)` | References `(report_id, version)`. Defines rows with labels, indent levels, row types (`section`, `data`, `calc`, `blank`), styles, and row-level `filter_expr` strings. |
| `rpt_row_metric` | `row_metric_id` | References `(report_id, version, row_id)`. Links `data` row to `sql_expr` (the SQL aggregation expression). Unique constraint on `(report_id, version, row_id, measure_id)`. |
| `rpt_row_formula` | `row_formula_id` | References `(report_id, version, row_id)`. Links `calc` row to `formula_expr` (e.g. `R2/R3`). Unique constraint on `(report_id, version, row_id)`. |
| `rpt_row_column_map` | `(report_id, version, row_id, col_id)`| References `(report_id, version, row_id)` and `(report_id, version, col_id)`. Grid intersections showing active columns for each row. |

### 2. `analytics` Schema

Represents the physical Data Warehouse (DWH) containing dimension and fact tables (seeding transaction, performance, investment, and sales data for 2024–2026).

---

## 🔌 API Endpoints

All backend APIs are prefixed with `/api` and listen on port `8101`.

### Report CRUD & Execution (`ReportController.java`)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/reports` | Lists latest version per report (catalog view). |
| **GET** | `/api/reports/{id}` | Loads a single report definition config DTO. Optional `?version=` and `?date=` params. |
| **POST** | `/api/reports` | Creates a new report config. |
| **PUT** | `/api/reports/{id}` | Updates (cascade-overwrites) an existing report config. |
| **POST** | `/api/reports/{id}/run` | Executes report generation and returns direct `.xlsx` download. Optional `?version=` and `?date=`. |
| **POST** | `/api/reports/validate` | Runs structural and semantic analysis to find configuration issues. |
| **GET** | `/api/reports/tables` | Returns list of physical tables in the `analytics` schema. |
| **GET** | `/api/reports/table-columns` | Returns column list for a table (e.g., `?table=fact_sales`). |
| **GET** | `/api/reports/column-types` | Returns database column types map for a given table. |
| **GET** | `/api/reports/dimensions/values` | Autocomplete distinct values for a table column. |
| **GET** | `/api/reports/dimension-joins` | Fetches join metadata from `meta_relationship` for a fact table. |
| **GET** | `/api/reports/schema-catalog` | Fetches the complete explore/view/dimension/measure model from `meta_*` tables. |

### Report Execution (`ReportExecutionController.java`)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **POST** | `/api/reports/{reportId}/execute` | Executes report and returns raw unpivoted grid coordinate values. Validates date against `dim_date`. Optional `?version=` and runtime filters in request body. |

### Report Version Lifecycle (`ReportVersionController.java`)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/reports/{id}/version/list` | Lists all versions of a report, ordered by version descending. |
| **POST** | `/api/reports/{id}/version/submit-review` | Transitions status `draft → in_review`. |
| **POST** | `/api/reports/{id}/version/reject` | Transitions status `in_review → draft`. |
| **POST** | `/api/reports/{id}/version/publish` | Publishes current version and auto-creates next draft (clones all child records). |
| **POST** | `/api/reports/{id}/version/fork` | Manually forks a published version into a new draft. |

### SQL Preview (`ReportPreviewController.java`)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **POST** | `/api/reports/preview-sql` | Dry-run query compilation to preview the generated SQL structure. |

### Authentication (`AuthController.java`)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/auth/login` | Validates Basic Auth and returns authenticated user info. |

### Metadata (`MetadataController.java`)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/metadata/distinct-values` | Security-validated distinct values search for dimension columns. |

---

## 📂 Codebase Tour

Use the links below to navigate directly to the primary components:

### Backend Services & Controllers

- **Controllers**:
  - [ReportController.java](src/main/java/com/reporting/controller/ReportController.java) — Report CRUD, run, validate, table metadata, and semantic-model endpoints.
  - [AuthController.java](src/main/java/com/reporting/controller/AuthController.java) — Manages login validation.
  - [ReportExecutionController.java](src/main/java/com/reporting/controller/ReportExecutionController.java) — Raw cell query execution with date validation against `dim_date`.
  - [ReportPreviewController.java](src/main/java/com/reporting/controller/ReportPreviewController.java) — Previews dry-run generated SQL queries.
  - [ReportVersionController.java](src/main/java/com/reporting/controller/ReportVersionController.java) — Report version lifecycle: submit-review, reject, publish (with auto-fork), and manual fork.
  - [MetadataController.java](src/main/java/com/reporting/controller/MetadataController.java) — Provides security-sanitized distinct autocomplete values.
  - [GlobalExceptionHandler.java](src/main/java/com/reporting/controller/GlobalExceptionHandler.java) — `@ControllerAdvice` mapping common exceptions to structured HTTP error responses.
- **Core Engine Services**:
  - [ReportRunnerService.java](src/main/java/com/reporting/service/ReportRunnerService.java) — Orchestrates the full execution pipeline: Load → Resolve → Generate SQL → Execute → Post-Process → Render.
  - [SqlGeneratorService.java](src/main/java/com/reporting/service/SqlGeneratorService.java) — Compiles report filters, fact tables, and rolling date boundaries into dynamic CTE queries. Delegates join resolution to `SchemaGraphRouter` and delegating row filter expression compiling to `FilterCompilerService`.
  - [FilterCompilerService.java](src/main/java/com/reporting/service/FilterCompilerService.java) — Compiles structured row filter configurations into record-based AST and parses using switch pattern matching to output standard SQL string filters.
  - [FilterNode.java](src/main/java/com/reporting/service/FilterNode.java), [RuleNode.java](src/main/java/com/reporting/service/RuleNode.java), [GroupNode.java](src/main/java/com/reporting/service/GroupNode.java) — Java 21 Sealed interface and Record subclasses for filter AST nodes.
  - [PostProcessorService.java](src/main/java/com/reporting/service/PostProcessorService.java) — Evaluates mathematical formulas at row/column intersections using `exp4j`.
  - [LayoutRendererService.java](src/main/java/com/reporting/service/LayoutRendererService.java) — Renders POI styles, grid alignments, fonts, colors, and formatting into downloading templates.
  - [ReportConfigService.java](src/main/java/com/reporting/service/ReportConfigService.java) — CRUD and config loading (hot-path JDBC read + cascade-delete JDBC write).
  - [ReportValidationService.java](src/main/java/com/reporting/service/ReportValidationService.java) — Validates cycle detections, schema checks, and expressions.
  - [SemanticResolverService.java](src/main/java/com/reporting/service/SemanticResolverService.java) — Resolves metric metadata (legacy; bypassed in current execution flow).
  - [DateUtils.java](src/main/java/com/reporting/service/DateUtils.java) — Period boundary calculations (start/end of week, month, quarter, year) for rolling columns.
- **Catalog Package** (`com.reporting.catalog`):
  - [SchemaCatalogLoader.java](src/main/java/com/reporting/catalog/SchemaCatalogLoader.java) — Loads `meta_table`, `meta_column`, `meta_relationship` into an in-memory graph at startup via `@PostConstruct`.
  - [SchemaGraphRouter.java](src/main/java/com/reporting/catalog/SchemaGraphRouter.java) — Dijkstra BFS pathfinder resolving multi-hop LEFT JOIN chains between fact and dimension tables.
  - [MetaTable.java](src/main/java/com/reporting/catalog/MetaTable.java), [MetaColumn.java](src/main/java/com/reporting/catalog/MetaColumn.java), [MetaRelationship.java](src/main/java/com/reporting/catalog/MetaRelationship.java) — In-memory graph node/edge models.
- **Database Utilities**:
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

## 🧭 Phase 3 Validation & Polish Roadmap (Completed)

All roadmap requirements have been successfully completed:

1. **Frontend Integration & UI Enhancements**:
   - Updated report detail view to show a loading spinner and live status badges during execution runs.
   - Fixed "Rolling In" field behavior in the Step 2 column setup wizard.
2. **Edge Case Validation**:
   - Tested handling of mathematical divide-by-zero, cyclic formula references, and missing metrics.
   - Refined unit tests to expand coverage metrics.
3. **Advanced Filtering**:
   - Extended general filters builder to support free-text SQL conditions (e.g., `amount > 1000`).
