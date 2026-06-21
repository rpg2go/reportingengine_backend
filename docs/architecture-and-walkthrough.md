# Architecture and Walkthrough

## Design Decisions (ADRs)

| Decision | Rationale |
| :--- | :--- |
| **Bypassed Semantic Layer** | Completely bypassed lookups to `reporting.sem_*` tables. Reports bind directly to physical database tables (e.g. `analytics.fact_sales`) and measure aggregation expressions are entered directly in row configurations. This simplifies query compilation, increases execution speed, and reduces complex joins. |
| **Catalog-Driven Join Graph** | Introduced `SchemaCatalogLoader` (reads `meta_table`, `meta_column`, `meta_relationship` at startup) and `SchemaGraphRouter` (Dijkstra BFS, edge cost 1 for conformed keys, cost 2 for non-conformed FKs). `SqlGeneratorService` delegates all multi-hop LEFT JOIN resolution to this router, eliminating any hardcoded join strings. If the `010` migration has not been applied, the router returns an empty list and the service generates joins-free SQL gracefully. |
| **State & Cascade Overwrite** | Implemented a cascade-delete strategy on saving report definitions. It clears child configurations (`ColumnDef`, `ReportRow`, `RowMetric`, `RowFormula`, `RowColumnMap`) in a flushed session transaction before saving the header. This eliminates "orphan" rows or column records. |
| **Report Version Lifecycle** | `ReportVersionController` enforces a `draft → in_review → published` state machine. Publishing auto-creates the next draft by cloning all child configurations via direct JDBC `INSERT … SELECT`. Manual `fork` is permitted only from a published version and only if no higher version already exists. |
| **Angular Standalone Architecture** | Frontend utilizes Angular 21 standalone components without `NgModules`. Each component declares its imports directly, making code cleaner and improving module load speeds. |
| **exp4j Math Evaluation** | Used `exp4j` for mathematical calculations in formula cells. It performs rapid, safe evaluations of mathematical expressions (e.g., `R2 / R3`) without executing raw JavaScript or SQL-injection-prone scripts. |
| **Direct JDBC over JPA for Hot Paths** | `loadFromDb()` uses raw JDBC queries with `RowCallbackHandler` instead of JPA repository calls. This eliminates entity hydration overhead and JOIN inflation from Hibernate, reducing report load latency from ~163ms to ~59ms. |
| **Direct JDBC Save Path** | Refactored row, column, and metric persistence in `ReportConfigService` to use raw `JdbcTemplate` updates. This resolves Hibernate cascade overhead, eliminates hydrations, and prevents orphan rows during report saves. |
| **Pushed-Down SQL Filters** | Refactored `SqlGeneratorService` to push general and quick filters directly down into individual fact CTE subqueries instead of applying them at the `unified_spine` level. This allows PostgreSQL to filter rows early, optimizing query execution plans. |
| **Security-Validated Autocomplete** | Implemented `/api/metadata/distinct-values` with direct JDBC queries and regex sanitization (`^[a-zA-Z0-9_]+$`) on dynamic table/column parameters to block SQL injection while supporting dynamic dimension autocomplete lookups. |
| **Centralized Exception Handling** | `GlobalExceptionHandler` (`@ControllerAdvice`) maps common exceptions (validation errors, illegal arguments, not-found) to structured JSON HTTP responses, preventing raw stack traces from leaking to clients. |
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
     - **`SqlGeneratorService`**: Constructs dynamic PostgreSQL queries using Common Table Expressions (CTEs) and conditional date boundaries. Delegates multi-hop JOIN resolution to `SchemaGraphRouter`.
     - **`PostProcessorService`**: Solves math formulas in grid cells using `exp4j`.
     - **`LayoutRendererService`**: Writes styled workbook data sheets back to POI cells.
     - **`ReportRunnerService`**: Orchestrates the full pipeline: Load Config → Resolve Metrics → Generate SQL → Execute → Post-Process → Render.
   - Catalog Package:
     - **`SchemaCatalogLoader`**: Reads `meta_*` registry tables at startup and assembles an in-memory graph.
     - **`SchemaGraphRouter`**: Weighted Dijkstra BFS pathfinder for dynamic LEFT JOIN resolution.

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
- **Backend**: Configured via [application.properties](../src/main/resources/application.properties) mapping ports, database URLs, usernames, passwords, and Hibernate logging levels.
- **Frontend**: Configured in standard typescript providers ([app.config.ts](../../reportingengine_frontend/src/app/app.config.ts)) and routing boundaries ([app.routes.ts](../../reportingengine_frontend/src/app/app.routes.ts)).

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

### Journey 2: Visual Layout Modification

1. The user clicks a report card to open the **Report Builder**.
2. The screen fetches current rows/columns via `GET /api/reports/{id}`.
3. The user re-orders rows, modifies a cell's custom SQL aggregation expression (e.g. `SUM(quantity)`), or updates filters.
4. The user clicks **Save**, executing a `PUT /api/reports/{id}` request that cascade-overwrites old parameters.

### Journey 3: Report Generation & Download

1. The user navigates to **Report Detail**, inputs a reference date (e.g. `2025-12-31`), and clicks **Run**.
2. `ReportRunnerService` loads the config, then calls `SqlGeneratorService` which uses `SchemaGraphRouter` to resolve joins and assemble the CTE query.
3. The query is executed against PostgreSQL; `PostProcessorService` evaluates algebraic formulas on returned values.
4. `LayoutRendererService` writes numbers, applies POI borders, backgrounds, and fonts, outputting an Excel stream directly to the user's downloads folder.

### Journey 4: Report Version Lifecycle

1. After editing, the author submits the report for review via `POST /api/reports/{id}/version/submit-review` (status transitions to `in_review`).
2. A reviewer either rejects it back to `draft` via `/reject` or approves it via `POST /api/reports/{id}/version/publish`.
3. Publishing locks the current version permanently and automatically creates the next draft (version+1) by cloning all child records via JDBC `INSERT … SELECT`.
4. If further changes are needed on a published version, `/fork` creates a new editable draft without modifying the published record.
