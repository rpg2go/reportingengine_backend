# Architecture and Walkthrough

## Design Decisions (ADRs)

| Decision | Rationale |
| :--- | :--- |
| **Bypassed Semantic Layer** | Completely bypassed lookups to `reporting.sem_*` tables. Reports bind directly to physical database tables (e.g. `analytics.fact_sales`) and measure aggregation expressions are entered directly in row configurations. This simplifies query compilation, increases execution speed, and reduces complex joins. |
| **State & Cascade Overwrite** | Implemented a cascade-delete strategy on saving report definitions. It clears child configurations (`ColumnDef`, `ReportRow`, `RowMetric`, `RowFormula`, `RowColumnMap`) in a flushed session transaction before saving the header. This eliminates "orphan" rows or column records. |
| **Angular Standalone Architecture** | Frontend utilizes Angular 21 standalone components without `NgModules`. Each component declares its imports directly, making code cleaner and improving module load speeds. |
| **exp4j Math Evaluation** | Used `exp4j` for mathematical calculations in formula cells. It performs rapid, safe evaluations of mathematical expressions (e.g., `R2 / R3`) without executing raw JavaScript or SQL-injection-prone scripts. |
| **Direct JDBC over JPA for Hot Paths** | `loadFromDb()` uses raw JDBC queries with `RowCallbackHandler` instead of JPA repository calls. This eliminates entity hydration overhead and JOIN inflation from Hibernate, reducing report load latency from ~163ms to ~59ms. |
| **Parallel Frontend Data Fetching** | Angular `ngOnInit` uses RxJS `forkJoin` to fire `/api/reports/tables` and `/api/reports/{id}` concurrently. Total perceived load time equals the slower of the two requests instead of their sum. |

---

## Solution Architecture Overview

The system follows a classic decoupled 3-tier architecture:

1. **Presentation Layer (Angular SPA)**:
   - Run on port `4200`.
   - Standalone typescript components using reactive signals.
   - Proxies backend requests to `http://127.0.0.1:8101/api`.

2. **Backend Services (Spring Boot)**:
   - Run on port `8101`.
   - Spring Security implements Basic Auth validating credentials against stored users.
   - Core Services:
     - **`ExcelParserService`**: Extracts template headers, variables, and columns using Apache POI.
     - **`SqlGeneratorService`**: Constructs dynamic PostgreSQL queries using Common Table Expressions (CTEs) and conditional date boundaries.
     - **`PostProcessorService`**: Solves math formulas in grid cells.
     - **`LayoutRendererService`**: Writes styled workbook data sheets back to POI cells.

3. **Data Layer (PostgreSQL Container)**:
   - Exposed on port `5432` in Docker container.
   - Separated into `reporting` schema (config metadata) and `analytics` schema (warehouse facts).

---

## Deployment Architecture Overview

The production deployment runs as containerized workloads:
- PostgreSQL operates inside a dedicated stateful Docker container with a persistent volume mount (`pgdata`).
- Spring Boot compiles to a jar file executable inside a Java 17 base image container.
- Angular compiles to static JS/HTML assets served through an NGINX proxy container or standard static web hosting.

---

## Configuration Management

Configuration is handled through:
- **Backend**: Configured via [application.properties](file:///G:/workspace/ReportTemplate_BackEnd/src/main/resources/application.properties) mapping ports, database URLs, usernames, passwords, and Hibernate logging levels.
- **Frontend**: Configured in standard typescript providers ([app.config.ts](file:///G:/workspace/ReportTemplate_FrontEnd/src/app/app.config.ts)) and routing boundaries ([app.routes.ts](file:///G:/workspace/ReportTemplate_FrontEnd/src/app/app.routes.ts)).

---

## Security

Security enforces a simple Basic Auth scheme:
- CORS filters on backend permit origins from `http://127.0.0.1:4200` to prevent access blocks.
- Route guards (`authGuard`) in Angular block routing access to components if the authorization headers/tokens are missing.

---

## Data Layer

The PostgreSQL instance manages two main schemas:
- **`reporting`**: Holds configurations (`rpt_report`, `rpt_column_def`, `rpt_row`, `rpt_row_metric`, `rpt_row_formula`, `rpt_row_column_map`).
- **`analytics`**: Houses dimensional warehouse facts (e.g., `fact_sales`, `dim_dates`, `fact_investments`).

---

## User Journeys

### Journey 1: Template Upload & Parsing
1. A business user selects an Excel template (e.g. `hybrid_reporting_template.xlsx`) and drags it onto the Dashboard.
2. The frontend makes a `POST /api/reports` multipart request uploading the file.
3. The `ExcelParserService` parses sheet ranges, reading report IDs, column offsets, and metric definitions.
4. Database transactions insert records into `rpt_report`, `rpt_row`, etc., and the Dashboard catalog auto-refreshes.

### Journey 2: Visual Layout Modification
1. The user clicks a report card to open the **Report Builder**.
2. The screen fetches current rows/columns via `GET /api/reports/{id}`.
3. The user re-orders rows, modifies a cell's custom SQL aggregation expression (e.g. `SUM(quantity)`), or updates filters.
4. The user clicks **Save**, executing a `PUT /api/reports/{id}` request that cascade-overwrites old parameters.

### Journey 3: Report Generation & Download
1. The user navigates to **Report Detail**, inputs a reference date (e.g. `2025-12-31`), and clicks **Run**.
2. The backend generates queries through `SqlGeneratorService` grouping metrics, calculates periods, and queries PostgreSQL.
3. `PostProcessorService` evaluates algebraic equations on returned values.
4. `LayoutRendererService` writes numbers, applies POI borders, backgrounds, and fonts, outputting an Excel stream directly to the user's downloads folder.
