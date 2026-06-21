# Testing & Quality Assurance

This document details the test framework, tools, execution steps, and manual quality assurance scripts for the reporting platform.

---

## Tooling

We use separate, industry-standard test tooling across the backend and frontend:

- **Backend (Java)**:
  - **JUnit 5**: The default test runner for writing and executing tests.
  - **Spring Boot Starter Test**: Bundles AssertJ, Mockito, and Spring integration testing.
  - **Spring Security Test**: Provides mock authentication filters for testing secured REST controllers.
- **Frontend (Angular)**:
  - **Jasmine**: Behavioral-driven testing framework for writing specs.
  - **Karma**: Test runner that spawns browser instances to run Angular spec suites.

---

## CI/CD vs Local

- **Local Development**:
  - Tests connect directly to the active Docker container `report_template_db` on port `5432` for database integration checks.
  - Mocked endpoints are utilized in tests where database isolation is required.
- **CI/CD Environments**:
  - Services are started in detached containers.
  - Environment variables (e.g. `SPRING_DATASOURCE_URL`) are injected to map host port bindings.
  - Integration suites run on pre-seeded migration tables to prevent DDL initialization failures.

---

## Commands

Run the following commands in their respective environments to check quality metrics:

| Scope | Command | Directory | Purpose |
| :--- | :--- | :--- | :--- |
| **Backend** | `maven\apache-maven-3.9.6\bin\mvn.cmd test` | Project Root | Runs all JUnit backend tests (Windows) |
| **Backend** | `./maven/apache-maven-3.9.6/bin/mvn test` | Project Root | Runs all JUnit backend tests (macOS/Linux) |
| **Frontend** | `npm test` | `frontend/` | Launches Karma browser test runner |

---

## Test Categories

### 1. Unit Tests

- **Backend**:
  - [PostProcessorServiceTest](../src/test/java/com/reporting/service/PostProcessorServiceTest.java): Tests formula parsing calculations with sample expressions (e.g., `R2 / R3`, `C1 - C2`) and asserts expected outputs.
  - [SqlGeneratorServiceTest](../src/test/java/com/reporting/service/SqlGeneratorServiceTest.java): Tests CTE generation, granularity column mappings, and filter pushdown logic using mocked config DTOs.
  - [ReportValidationServiceTest](../src/test/java/com/reporting/service/ReportValidationServiceTest.java): Tests cyclic formula detection, schema expression validation, and missing metric error paths.
  - [DateUtilsTest](../src/test/java/com/reporting/service/DateUtilsTest.java): Tests period boundary calculations (week, month, quarter, year) across rolling column offsets.
  - [LayoutRendererServiceTest](../src/test/java/com/reporting/service/LayoutRendererServiceTest.java): Tests POI cell styling and Excel rendering logic.
  - [SemanticResolverServiceTest](../src/test/java/com/reporting/service/SemanticResolverServiceTest.java): Tests metric metadata resolution (legacy path).
  - [ReportConfigServiceTest](../src/test/java/com/reporting/service/ReportConfigServiceTest.java): Tests JDBC save and cascade-delete behaviour with mocked templates.
  - [MetadataControllerTest](../src/test/java/com/reporting/controller/MetadataControllerTest.java): Tests injection rejection — malformed strings, SQL keywords (e.g. `;`, `UNION`) return `400 Bad Request`.
  - [ReportPreviewControllerTest](../src/test/java/com/reporting/controller/ReportPreviewControllerTest.java): Tests the SQL preview endpoint with mocked generator output.
  - [AuthControllerTest](../src/test/java/com/reporting/controller/AuthControllerTest.java): Tests Basic Auth response with valid and invalid credentials.
  - [ReportControllerTest](../src/test/java/com/reporting/controller/ReportControllerTest.java): Tests report CRUD endpoints (list, get, save, run, validate).
- **Frontend**: Verify authentication guards block page access, and components emit events when layout coordinates change.

### 2. Integration Tests

- **[ReportControllerIT](../src/test/java/com/reporting/controller/ReportControllerIT.java)**: Bootstraps a full Spring MVC context against a live Testcontainers PostgreSQL DB. Validates `/api/reports` returns 200 with a list, and that protected endpoints correctly reject unauthenticated requests.
- **[SqlGeneratorServiceIT](../src/test/java/com/reporting/service/SqlGeneratorServiceIT.java)**: Runs `SqlGeneratorService` against a live seeded database. Verifies the generated CTE SQL structure and asserts that filter pushdown lands in the correct fact CTE scope.
- **[ReportConfigServiceIT](../src/test/java/com/reporting/service/ReportConfigServiceIT.java)**: Tests cascade deletion and JDBC Template saves against a real database to ensure configurations are cleanly written and updated.
- **[ReportRunnerServiceIT](../src/test/java/com/reporting/service/ReportRunnerServiceIT.java)**: End-to-end pipeline test: config load → SQL generation → database execution → post-processing → Excel rendering, against a seeded Testcontainers database.
- **[ReportSeededValidationIT](../src/test/java/com/reporting/ReportSeededValidationIT.java)**: Smoke-tests all 14 seeded production report templates: validates that each report's configuration passes `ReportValidationService` checks without errors.


### 3. Manual Verification
To manually test REST APIs, you can run these simple PowerShell commands (ensure headers are passed for Basic Auth):

**1. Authentication Verification**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8101/api/auth/login" `
  -Method Get `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:password")) }
```

**2. List Catalog Reports**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8101/api/reports" `
  -Method Get `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:password")) }
```
