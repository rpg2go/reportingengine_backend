# Project Plan: Reporting Engine Platform

## ✅ Phase 1: Foundation, UI Builder & Persistence (Completed 2026-05-25)
- [x] Database migrations 000–007 (Schema setup for `reporting` and `analytics` schemas).
- [x] Spring Boot backend structure, JPA entity mappings, CORS, and Basic Auth security.
- [x] Angular 21 standalone frontend components (Login, Dashboard, Report Builder, Detail, Semantic Viewer).
- [x] Removed Excel template parsing and import via Apache POI (completed).
- [x] Visual Report Builder: drag-and-drop row reordering, step-based column/row setup wizards.
- [x] Row and column delete actions in builder setup steps.
- [x] Quick filter search bars on Dashboard catalog and Semantic Layer pages.
- [x] General filters with comparison operator support (`=`, `!=`, `>`, `<`, `>=`, `<=`, `IN`, `NOT IN`, `LIKE`).
- [x] Dynamic DWH table and column autocomplete in builder (scanned from `analytics` schema via `pg_catalog`).
- [x] Semantic Layer viewer: Explores, Joins, Views, and Schema Mapping pages with quick filters.
- [x] Performance optimizations:
  - [x] `loadFromDb()` rewritten with direct JDBC queries (eliminated 6 sequential JPA round-trips).
  - [x] Verbose SQL logging disabled (`show-sql=false`, Hibernate SQL at WARN level).
  - [x] Frontend parallel API loading with `forkJoin` (tables + report config loaded concurrently).
  - [x] API base URL changed to `127.0.0.1` to bypass Windows DNS resolution lag.

## ✅ Phase 2: Engine Compilation & Rendering (Completed 2026-05-30)
- [x] Dynamic SQL query compilation (`SqlGeneratorService.java`) — assemble fact table queries with CTE date boundaries and conditional aggregations from row `sql_expr` and `filter_expr`.
- [x] Row and column math formula evaluation (`PostProcessorService.java`) with `exp4j`.
- [x] Excel layout rendering (`LayoutRendererService.java`) — POI styling with borders, alignment, fonts, and colors.
- [x] Full run orchestration (`ReportRunnerService.java`) — chain SQL → PostProcess → Render into a single execution.

## 🔄 Phase 3: Validation, Verification & Polish (Completed & Polished)

- [x] Integration testing of the full execution pipeline.
- [x] Unit tests for `SqlGeneratorService` and `PostProcessorService`.
- [x] Unit tests for `ReportValidationService` (cyclic formula detection, schema expression checks).
- [x] SQL Generator integration test (`SqlGeneratorServiceIT`) against seeded live database.
- [x] Seeded report smoke-test (`ReportSeededValidationIT`) validating all 14 production templates.
- [x] `MetadataControllerTest` covering injection rejection on dynamic table/column params.
- [x] `ReportPreviewControllerTest` and `ReportControllerTest` unit test coverage.
- [x] Schema catalog: `SchemaCatalogLoader` + `SchemaGraphRouter` (Dijkstra BFS multi-hop JOIN resolver) powered by `meta_*` tables (migration `010`).
- [x] Report version lifecycle: `ReportVersionController` with `draft → in_review → published → fork` state machine and auto-clone on publish.
- [x] `GlobalExceptionHandler` (`@ControllerAdvice`) for structured JSON error responses.
- [x] `rpt_column_def` schema extensions: `rolling_grain` column, composite key adjustments, constraint relaxation (migrations `011`–`015`).
- [x] In-memory metadata pre-caching (`MetadataCache.java`) to eliminate repeated DWH catalog and `information_schema` round-trips.
- [x] Modular Monolith Deconstruction (Phase A): separated REST endpoints into `ReportController`, `ReportVersionController`, `ReportExecutionController`, and `SchemaDiscoveryController`.
- [x] Extracted versioning state logic to `VersioningService`.
- [x] Request correlation tracing filter (`CorrelationIdFilter`) stamp requests/logs with `X-Correlation-ID`.
- [x] Robust Cloud Run health checks utilizing Spring Actuator `/actuator/health` probes in `deploy.sh`.
- [x] Fixed catalog listing bug where auto-forked draft overrode published status.
- [ ] Frontend report detail view: run execution spinner and live status badges.
- [ ] Edge case handling: divide-by-zero, cyclic formula references, missing metric expressions.
- [ ] Column setup: fix "Rolling In" field behavior in Step 2.
- [ ] General filter: support free-text SQL expressions (e.g., `amount > 1000`).
