# Headless BI Reporting Platform

This repository contains the complete metadata-driven report configuration and execution platform. The system has been fully migrated from a prototype Python engine to a robust, enterprise-grade full-stack architecture comprising a **Java (Spring Boot) backend**, a **modern Angular SPA frontend**, and a **dockerized PostgreSQL database**.

The reporting platform ingests Excel layout templates, normalizes their configuration into metadata tables, resolves logical metrics against a semantic data model, generates high-performance SQL query structures with conditional aggregation, evaluates math formulas, and renders final styled Excel workbooks.

---

## 🏗️ Unified Architecture

```
                       ┌─────────────────────────┐
                       │   Angular Frontend      │
                       │   (UI / SPA on :4200)   │
                       └────────────┬────────────┘
                                    │ HTTP / REST APIs
                                    ▼
                       ┌─────────────────────────┐
                       │   Spring Boot Backend   │
                       │   (REST APIs on :8101)  │
                       └────────────┬────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         ▼                          ▼                          ▼
┌─────────────────┐        ┌──────────────────┐       ┌──────────────────┐
│  Excel Parser   │        │  SQL Generator   │       │  Post-Processor  │
│  (Apache POI)   │        │  (Conditional    │       │  (exp4j Formula  │
│                 │        │  Aggregation)    │       │  Evaluation)     │
└────────┬────────┘        └────────┬─────────┘       └────────┬─────────┘
         │                          │                          │
         └──────────────────────────┼──────────────────────────┘
                                    ▼
                       ┌─────────────────────────┐
                       │    PostgreSQL Container │
                       │    (Docker port :5432)  │
                       └─────────────────────────┘
```

The system operates across two database layers in PostgreSQL:
* **`reporting.*`**: Stores metadata, report definitions, explores, views, dimension/measure columns, join paths, and import audit history.
* **`analytics.*`**: Represents the physical Data Warehouse (DWH) containing dimension and fact tables (seeding transaction, performance, investment, and sales data for 2024–2026).

---

## 📂 Project Structure

```
ReportTemplate/
├── db/                       # Database container configuration
│   ├── migrations/           # SQL migration scripts (000 to 006)
│   └── Dockerfile            # Custom Postgres image bundling migrations
├── src/main/java/            # Spring Boot Java application
│   └── com/reporting/
│       ├── config/           # CORS and Security Configurations
│       ├── controller/       # REST API Endpoints (Auth, Reports, Semantics)
│       ├── domain/           # JPA Entities mapping to reporting.* tables
│       ├── dto/              # Data Transfer Objects
│       ├── repository/       # Spring Data JPA repositories
│       └── service/          # Core Business Services (Parser, SQL, POI)
├── frontend/                 # Standalone Angular SPA UI
│   └── src/app/
│       ├── components/       # Login, Catalog, Detail view, Semantic browser
│       ├── guards/           # Route access controllers (authGuard)
│       └── services/         # REST API Service clients
├── documentation/            # PDF and Markdown user/authoring guides
├── maven/                    # Embedded local Maven wrapper
├── docker-compose.yml        # Docker composition orchestration
├── pom.xml                   # Backend dependencies and Maven build script
└── README.md                 # Project root documentation
```

---

## 🛠️ Main Engine Components (Java)

All core reporting capabilities are written in Java and located in [src/main/java/com/reporting/service/](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/):

* **[ExcelParserService.java](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/ExcelParserService.java)**: Parses the user-defined Excel layout workbook using **Apache POI**, extracts layout variables, and persists configuration records in a database transaction.
* **[SemanticResolverService.java](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/SemanticResolverService.java)**: Resolves logical measure names used in report rows to their physical SQL expressions and database paths.
* **[SqlGeneratorService.java](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/SqlGeneratorService.java)**: Builds dynamic, highly-optimized SQL queries using CTE structures and conditional aggregations mapped against time-intelligence intervals (MTD, YTD, WEEK, ROLLING).
* **[PostProcessorService.java](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/PostProcessorService.java)**: Evaluates horizontal `CALC` columns and vertical `calc` math formulas on query results using **exp4j**.
* **[LayoutRendererService.java](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/LayoutRendererService.java)**: Outputs the final results back to `.xlsx` sheets with grid formatting, indentation levels, currency styles, and headers using **Apache POI**.
* **[ReportRunnerService.java](file:///g:/workspace/ReportTemplate/src/main/java/com/reporting/service/ReportRunnerService.java)**: Orchestrates the execution sequence.

---

## 🚀 How to Run the Platform

### 1. Start the PostgreSQL Container
Bring up the database container. This will build the custom image from `db/Dockerfile` and automatically run all SQL migrations in order:
```bash
# Deletes old volume and builds/starts container
docker-compose down -v
docker-compose up --build -d
```

### 2. Run the Java Backend (Spring Boot)
Run the application server locally on port `8101` using the embedded Maven wrapper:
```bash
# Clean compilation and run boot server
maven/apache-maven-3.9.6/bin/mvn clean compile
maven/apache-maven-3.9.6/bin/mvn spring-boot:run
```

### 3. Start the Angular UI
Run the frontend server (serves on `http://localhost:4200/`):
```bash
cd frontend
npm install
npm start
```

---

## 📥 End-to-End Application Flow

1. Open **`http://localhost:4200/`** and sign in with the default credentials:
   * **Username**: `admin`
   * **Password**: `password`
2. Under the catalog screen, select the **Import Template** option and upload **`hybrid_reporting_template.xlsx`** (or a similar showcase layout).
3. The backend will parse the workbook and insert metadata definitions into PostgreSQL.
4. Click on the imported report (e.g. `SALES_OVERVIEW` or `INVESTMENT_SUMMARY`), select a Reference Date (e.g. `2025-12-31`), and click **Run**.
5. The backend will dynamically fetch values, evaluate formulas, apply styles, and compile a `.xlsx` report file for direct download.
6. Browse the **Semantic Layer** tab to inspect DWH schema explore paths, joins, logical view mappings, dimensions, and measures.
