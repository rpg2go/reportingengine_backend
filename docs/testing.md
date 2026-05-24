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
- **Backend**: Test formula parsing calculations inside `PostProcessorService` with sample expressions (e.g., `R2 / R3` or `C1 - C2`) and assert expected outputs.
- **Frontend**: Verify authentication guards block page access, and components emit events when layout coordinates change.

### 2. Integration Tests
- **API Endpoint Checks**: Bootstrap mock MVC web context and test `/api/reports` returns listing payloads with valid HTTP status codes (`200 OK` or `201 Created`).
- **SQL Generation Checks**: Run `SqlGeneratorService` with config mocks and verify output SQL contains required Common Table Expressions (`WITH cte_...`).

### 3. Manual Verification
To manually test REST APIs, you can run these simple PowerShell commands (ensure headers are passed for Basic Auth):

**1. Authentication Verification**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8101/api/auth/login" `
  -Method Post `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:password")) }
```

**2. List Catalog Reports**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8101/api/reports" `
  -Method Get `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:password")) }
```
