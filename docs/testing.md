# Testing & Quality Assurance

This document details the test framework, tools, directory structure, testing strategy, and execution steps to verify the correctness of the Report Template Engine. This is structured to guide human developers and LLM agents alike.

---

## 🛠️ Tooling

We use separate, industry-standard test tooling across the backend and frontend:

- **Backend (Java)**:
  - **JUnit 5**: The default test runner for writing and executing tests.
  - **Mockito**: Mocking framework for unit tests.
  - **AssertJ**: Assertion library utilizing readable fluent assertions (`assertThat(...)`).
  - **Spring Boot Starter Test**: Bundles AssertJ, Mockito, and Spring integration testing.
  - **Spring Security Test**: Provides mock authentication filters for testing secured REST controllers.
  - **Testcontainers (PostgreSQL)**: Automatically spins up isolated PostgreSQL instances for Integration Tests (`IT`).
- **Frontend (Angular)**:
  - **Vitest**: Test runner and assertion framework for fast, Node-based class-instance unit tests.

---

## 📂 Test Location & Directory Structure

All test sources reside in the standard maven test directory `src/test/java`, mirroring the main source code packages:

```text
src/test/java/
└── com/reporting/
    ├── BaseIT.java                               <-- Base class for database integration tests
    ├── ReportSeededValidationIT.java             <-- Smoke tests checking the 14 seeded templates
    ├── controller/
    │   ├── AuthControllerTest.java               <-- Unit/Security test for authentication
    │   ├── MetadataControllerTest.java           <-- Input validation and SQL injection tests
    │   ├── ReportControllerTest.java             <-- CRUD and config validation endpoint test
    │   ├── ReportControllerIT.java               <-- Secured integration controller test
    │   └── ReportPreviewControllerTest.java      <-- Previews generated SQL query structure
    └── service/
        ├── DateUtilsTest.java                    <-- Utility test for time offset boundaries
        ├── LayoutRendererServiceTest.java        <-- POI Excel workbook rendering unit test
        ├── PostProcessorServiceTest.java         <-- exp4j algebraic formula evaluation unit test
        ├── ReportValidationServiceTest.java      <-- Validation check unit tests (e.g. cycle check)
        ├── SemanticResolverServiceTest.java      <-- Legacy metadata resolver unit test
        ├── SqlGeneratorServiceTest.java          <-- Mocked config generator SQL unit test
        ├── SqlGeneratorServiceIT.java            <-- Dynamic compilation integration test (live DB)
        ├── ReportConfigServiceTest.java          <-- Mocked CRUD and cascade service unit test
        ├── ReportConfigServiceIT.java            <-- Real-database configuration save/delete IT
        └── ReportRunnerServiceIT.java            <-- E2E database loading to Excel rendering pipeline IT
```

---

## 🎯 Testing Strategy

To ensure high-quality software, the workspace maintains a strict testing strategy:

1.  **Isolation (Unit Tests)**:
    *   Tests ending with `*Test.java` (e.g. `PostProcessorServiceTest.java`) target individual components in isolation.
    *   Do **NOT** load the Spring Context (`@SpringBootTest`) for unit tests. Extend with `MockitoExtension.class` and mock all dependencies.
    *   Target Coverage: **90%+** for service classes, **85%+** for controllers, and **95%+** for utility classes.
2.  **Stateful Validation (Integration Tests)**:
    *   Tests ending with `*IT.java` (e.g. `ReportConfigServiceIT.java`) test components in context.
    *   Extend [BaseIT](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/BaseIT.java) to automatically boot up a dockerized PostgreSQL container (via Testcontainers) and run Liquibase migrations.
    *   Annotate tests/methods with `@Transactional` to auto-rollback changes.
3.  **Security Gates & SQL Injection Checking**:
    *   Ensure any new endpoint or query parameter parameterization is tested against malicious inputs (e.g. `;`, `UNION`, `--`) to check for SQL injection vulnerability.
4.  **Mathematical Safety Checks**:
    *   Test math formula evaluation against boundary conditions like division by zero or circular references.

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
| **Frontend** | `npm test` | Frontend Root | Runs fast Node-based Vitest unit tests |

---

## Test Categories

### 1. Unit Tests

- **Backend (Java)**:
  - [FilterCompilerServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/FilterCompilerServiceTest.java): Tests structured JSON filter compilation, sealed interface type checks, and record AST-to-SQL generation.
  - [PostProcessorServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/PostProcessorServiceTest.java): Tests formula parsing calculations with sample expressions (e.g., `R2 / R3`, `C1 - C2`) and asserts expected outputs.
  - [SqlGeneratorServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/SqlGeneratorServiceTest.java): Tests CTE generation, granularity column mappings, and filter pushdown logic using mocked config DTOs.
  - [ReportValidationServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/ReportValidationServiceTest.java): Tests cyclic formula detection, schema expression validation, and missing metric error paths.
  - [DateUtilsTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/DateUtilsTest.java): Tests period boundary calculations (week, month, quarter, year) across rolling column offsets.
  - [LayoutRendererServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/LayoutRendererServiceTest.java): Tests POI cell styling and Excel rendering logic.
  - [SemanticResolverServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/SemanticResolverServiceTest.java): Tests metric metadata resolution (legacy path).
  - [ReportConfigServiceTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/ReportConfigServiceTest.java): Tests JDBC save and cascade-delete behaviour with mocked templates.
  - [MetadataControllerTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/controller/MetadataControllerTest.java): Tests injection rejection — malformed strings, SQL keywords (e.g. `;`, `UNION`) return `400 Bad Request`.
  - [ReportPreviewControllerTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/controller/ReportPreviewControllerTest.java): Tests the SQL preview endpoint with mocked generator output.
  - [AuthControllerTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/controller/AuthControllerTest.java): Tests Basic Auth response with valid and invalid credentials.
  - [ReportControllerTest](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/controller/ReportControllerTest.java): Tests report CRUD endpoints (list, get, save, run, validate) and SchemaDiscoveryController endpoints (autocomplete, schema meta).
  > [!NOTE]
  > Because backend tests require Java 21, ensure your local environment points to the JDK 21 installation. If the default shell is targeting a lower JDK version, run commands with `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` prefixed.
- **Frontend**: Verify authentication guards block page access, and components emit events when layout coordinates change.

### 2. Integration Tests

- **[ReportControllerIT](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/controller/ReportControllerIT.java)**: Bootstraps a full Spring MVC context against a live Testcontainers PostgreSQL DB. Validates `/api/reports` returns 200 with a list, and that protected endpoints correctly reject unauthenticated requests.
- **[SqlGeneratorServiceIT](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/SqlGeneratorServiceIT.java)**: Runs `SqlGeneratorService` against a live seeded database. Verifies the generated CTE SQL structure and asserts that filter pushdown lands in the correct fact CTE scope.
- **[ReportConfigServiceIT](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/ReportConfigServiceIT.java)**: Tests cascade deletion and JDBC Template saves against a real database to ensure configurations are cleanly written and updated.
- **[ReportRunnerServiceIT](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/service/ReportRunnerServiceIT.java)**: End-to-end pipeline test: config load → SQL generation → database execution → post-processing → Excel rendering, against a seeded Testcontainers database.
- **[ReportSeededValidationIT](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/test/java/com/reporting/ReportSeededValidationIT.java)**: Smoke-tests all 14 seeded production report templates: validates that each report's configuration passes `ReportValidationService` checks without errors.


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

