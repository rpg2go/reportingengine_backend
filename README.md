# Reporting Engine Back-End — Enterprise Developer Guide

A robust, enterprise-grade, metadata-driven report configuration and execution engine. This repository contains the **Java 21 (Spring Boot v3.5.0-SNAPSHOT) backend** and PostgreSQL database migrations. The frontend is split into the [reportingengine_frontend](../reportingengine_frontend) repository.

The backend ingests Excel layout templates, normalizes their configuration into metadata tables, resolves logical metrics against a semantic data model, generates high-performance SQL query structures with conditional aggregation, evaluates math formulas, and renders final styled Excel workbooks.

---

## Repo Metadata

- **Author**: Antigravity Developer Team & Google DeepMind Pair Programmer
- **Repository**: [reportingengine_backend](./)
- **Backend Stack**: Java 21, Spring Boot 3.5.0-SNAPSHOT, Spring Data JPA, Hibernate, exp4j, Project Loom Virtual Threads
- **Database**: PostgreSQL 16 (Local Docker container or Neon Serverless Postgres in production)

---

## Table of Contents

- [Reporting Engine Back-End — Enterprise Developer Guide](#reporting-engine-back-end--enterprise-developer-guide)
  - [Repo Metadata](#repo-metadata)
  - [Table of Contents](#table-of-contents)
  - [Key Project Documentation](#key-project-documentation)
  - [Key Links](#key-links)
  - [Project Structure](#project-structure)
  - [Architectural Stack \& Key Optimizations](#architectural-stack--key-optimizations)
    - [Core Technologies](#core-technologies)
  - [Architecture Diagram](#architecture-diagram)
  - [Quick Start: Working With This Repo](#quick-start-working-with-this-repo)
    - [Prerequisites](#prerequisites)
      - [1. Software Runtimes \& Platforms](#1-software-runtimes--platforms)
      - [2. Step-by-Step Installation Instructions](#2-step-by-step-installation-instructions)
        - [🍎 macOS (using Homebrew, SDKMAN!, and NVM)](#-macos-using-homebrew-sdkman-and-nvm)
        - [🐧 Ubuntu / Debian Linux (using apt, NodeSource, and Docker Repository)](#-ubuntu--debian-linux-using-apt-nodesource-and-docker-repository)
        - [🪟 Windows (using winget or manual packages)](#-windows-using-winget-or-manual-packages)
      - [3. Verification Command Cheat Sheet](#3-verification-command-cheat-sheet)
    - [One-Time Setup](#one-time-setup)
    - [Per Dev Session](#per-dev-session)
  - [Useful Commands](#useful-commands)
  - [End-to-End Application Flow](#end-to-end-application-flow)
  - [Database Layers](#database-layers)
  - [Troubleshooting Port Conflicts](#troubleshooting-port-conflicts)
    - [1. Identify Running Processes](#1-identify-running-processes)
    - [2. Kill the Process Manually](#2-kill-the-process-manually)

---

## Key Project Documentation

| Document | Description |
| :--- | :--- |
| [README.md](README.md) | This file - the developer front door |
| [TODO.md](TODO.md) | Project plan, completed milestones, and development backlog |
| [docs/DESIGN.md](docs/DESIGN.md) | Visual design tokens, color guidelines, and UX guidelines |
| [docs/architecture-and-walkthrough.md](docs/architecture-and-walkthrough.md) | System design decisions (ADRs), solution architecture, and user journeys |
| [docs/testing.md](docs/testing.md) | Quality assurance guidelines, testing commands, and manual REST API checks |
| [docs/swagger-spec.yaml](docs/swagger-spec.yaml) | API Swagger Specification - complete OpenAPI 3.0.3 REST endpoints contract |
| [deployment/README.md](deployment/README.md) | Application packaging, Docker compose guidelines, and CI/CD stages |
| [.agents/agents/validation_agents.md](.agents/agents/validation_agents.md) | Back-end validation agents specification and execution guide |
| [docs/regional_distribution_template.md](docs/regional_distribution_template.md) | Detailed configuration reference for the Regional Distribution template |
| [documentation/report_authoring_guide.md](documentation/report_authoring_guide.md) | Business user guide on how to design layout templates in Excel |
| [documentation/implementation_plan.md](documentation/implementation_plan.md) | Base implementation plan for platform migration and dynamic filters |
| [GEMINI.md](GEMINI.md) | Handoff state, schema layout, API endpoints, and phase 2 roadmap |

---

## Key Links

- **Spring Boot Backend API**: [http://127.0.0.1:8101](http://127.0.0.1:8101)
- **Angular Frontend UI**: [http://127.0.0.1:4200](http://127.0.0.1:4200)
- **API Swagger Specification**: [docs/swagger-spec.yaml](docs/swagger-spec.yaml)
- **PostgreSQL Database**: `127.0.0.1:5433` (DB: `agentic_ai`, User: `user`, Pass: `*****` - maps to container port `5432`)

> **Windows/macOS Performance Note**: Use `127.0.0.1` instead of `localhost` to avoid IPv6 DNS resolution delay (saves 1–2 s per request on Windows).

---

## Project Structure

```text
reportingengine_backend/
├── .agents/                    # ADK validation agents configuration & code
│   ├── agents/                 # Validator specifications
│   └── validation/             # Executable validation agent (agent.py, tools.py)
├── db/                         # Database container configuration
│   ├── migrations/             # SQL migration scripts (000 to 015)
│   └── Dockerfile              # Custom Postgres image bundling migrations
├── docs/                       # Architecture, data model, and testing docs
├── documentation/              # Business user design plans and authoring guides
├── src/                        # Spring Boot Java application source code
│   ├── main/
│   │   ├── java/com/reporting/
│   │   │   ├── Application.java          # Bootloader application class
│   │   │   ├── cache/                    # In-memory startup caches
│   │   │   │   └── MetadataCache.java    # Pre-loads DWH schema catalogs, views & measures
│   │   │   ├── catalog/                  # Schema catalog & graph router
│   │   │   │   ├── SchemaCatalogLoader.java  # Loads meta_* tables into in-memory graph
│   │   │   │   ├── SchemaGraphRouter.java    # Dijkstra BFS join path resolver
│   │   │   │   ├── MetaTable.java
│   │   │   │   ├── MetaColumn.java
│   │   │   │   └── MetaRelationship.java
│   │   │   ├── config/                   # Security & CORS settings
│   │   │   ├── controller/               # REST Endpoints
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── MetadataController.java
│   │   │   │   ├── ReportController.java     # CRUD, validation, and Excel run
│   │   │   │   ├── ReportExecutionController.java # Live unpivoted query runs
│   │   │   │   ├── ReportPreviewController.java
│   │   │   │   ├── ReportVersionController.java   # HTTP adapter for versioning actions
│   │   │   │   └── SchemaDiscoveryController.java # DWH table and column autocomplete
│   │   │   ├── domain/                   # JPA Entities (rpt_* tables)
│   │   │   ├── dto/                      # Data Transfer Objects
│   │   │   ├── exception/                # Custom exception types
│   │   │   ├── filter/                   # HTTP Filters
│   │   │   │   └── CorrelationIdFilter.java # Injects MDC with request X-Correlation-ID
│   │   │   ├── repository/               # Spring Data repositories
│   │   │   ├── service/                  # Core services (Parser, SQL, POI, formulas)
│   │   │   │   ├── FilterCompilerService.java # Compiles row filters into AST and SQL
│   │   │   │   ├── FilterNode.java       # AST Sealed interface type
│   │   │   │   ├── RuleNode.java         # AST Record for terminal rule
│   │   │   │   ├── GroupNode.java        # AST Record for logical group
│   │   │   │   └── VersioningService.java # Business rules for version state and auto-forking
│   │   │   └── util/                     # MigrationRunner, DbDumper utilities
│   │   └── resources/
│   │       └── application.properties    # Server and datasource config
│   └── test/                             # JUnit unit & integration tests
├── maven/                      # Embedded Apache Maven 3.9.6 wrapper
├── docker-compose.yml          # Container composition orchestration
├── pom.xml                     # Maven POM dependencies build script
└── GEMINI.md                   # State handoff and database schema reference
```

---

## Architectural Stack & Key Optimizations

The backend is architected as a high-performance Spring Boot application prioritizing low-latency reads, structured data layers, and safe mathematical evaluation.

### Core Technologies

*   **Java Runtime:** Java 21 (LTS)
*   **Framework:** Spring Boot v3.5.0-SNAPSHOT
*   **Virtual Threads:** Enabled via `spring.threads.virtual.enabled=true` to handle Tomcat HTTP request processing and task execution on Project Loom virtual threads.
*   **Persistence:** Spring Data JPA (Hibernate v6.x) for configuration CRUD operations.
*   **Sealed AST Filter Compiler:** Uses a modern pattern matching compiler (`FilterCompilerService`) with sealed hierarchy (`FilterNode`) and Java 21 records (`RuleNode`, `GroupNode`) to compile row-level filter expressions.
*   **In-Memory Metadata Cache:** Startup-loaded `MetadataCache` pre-fetches column definitions, time keys, semantic measures, and views, reducing report compilation latency to ~50ms by eliminating live `information_schema` query overhead.
*   **Direct JDBC Optimization:** Direct JDBC Template with `RowCallbackHandler` bypassing Hibernate hydration for the critical read hot-path (`loadFromDb()`). This optimization reduces report configuration latency from ~163ms to ~59ms.
*   **Direct JDBC Save Path:** Report row/column configurations are persisted using direct `JdbcTemplate` updates in `ReportConfigService`, resolving Hibernate cascade overhead and preventing orphan rows.
*   **Pushed-Down SQL Filters:** Pushes general and quick filters into the individual fact table CTEs inside `SqlGeneratorService`, allowing PostgreSQL to optimize execution plans by filtering early during the scan.
*   **Request Trace Correlation:** `CorrelationIdFilter` stamps every incoming request and downstream log entry with a request-scoped `X-Correlation-ID` header, facilitating distributed tracing in Cloud Run.
*   **Excel Engine:** Apache POI (v5.3.0) for cell-level layout extraction and styled spreadsheet generation.
*   **Formula Engine:** `exp4j` (v0.4.8) for fast, isolated, sandbox-safe mathematical evaluation of cell and row formulas (preventing SQL or script injection).
*   **Database:** PostgreSQL 16 (hosted via Docker container locally; Neon Serverless Postgres in production).

### Column Time-Window Types & Period Boundaries

The engine resolves dynamic column time boundaries in `DateUtils` according to the following configurations:
*   **`WTD` (Week-to-Date):** Monday–Sunday window.
*   **`MTD` (Month-to-Date):** Beginning of the month to reporting date.
*   **`QTD` (Quarter-to-Date):** Beginning of the quarter (Q1: Jan 1, Q2: Apr 1, Q3: Jul 1, Q4: Oct 1) to reporting date.
*   **`YTD` (Year-to-Date):** January 1st to reporting date.
*   **`ROLLING`:** Multi-period rolling window (supporting `DAY`, `WEEK`, `MONTH`, `QUARTER`, and `YEAR` grains).

**Immutability & Expansion Rules for Past Periods:**
For all current periods (period offset = 0), the end boundary is locked to the reporting date (e.g. current day). For all past/future periods (period offset $\neq$ 0), the boundary automatically expands to cover the **entire period** (e.g. full month or quarter) rather than truncating to the day-of-period of the reporting date.

---

## Architecture Diagram

The architecture is built for clean separation of concerns:

```mermaid
flowchart TD
    A["Angular Frontend (UI / SPA on :4200)"] -->|HTTP / REST APIs| B["Spring Boot Backend (REST APIs on :8101)"]
    B --> C["Excel Parser (Apache POI)"]
    B --> D["SQL Generator (Conditional Aggregation CTEs)"]
    B --> E["Post-Processor (exp4j Formula Evaluation)"]
    B --> F["Layout Renderer (Apache POI)"]
    B --> H["Schema Catalog (SchemaCatalogLoader)"]
    H --> I["Graph Router (SchemaGraphRouter / Dijkstra BFS)"]
    D --> I
    C --> G[("PostgreSQL Database (Docker port :5433)")]
    D --> G
    E --> G
    F --> G
    H --> G
```

---

## Quick Start: Working With This Repo

Follow these steps to run the Reporting Engine backend locally:

### Prerequisites

Your development environment must have the following software runtimes, dependencies, and packages installed:

#### 1. Software Runtimes & Platforms

* **Java Development Kit (JDK) 21**: Needed to compile and run the Spring Boot backend. OpenJDK 21 or Eclipse Temurin 21 are recommended.
* **Node.js (v24+) & npm**: Needed to build and run the Angular frontend.
* **Docker & Docker Compose**: Needed to orchestrate and run the PostgreSQL 16 database container.
* **Python (v3.10+) & pip**: Needed to execute the ADK validation agents for automated code verification.
* **Git**: Needed to clone and manage the repository code.

---

#### 2. Step-by-Step Installation Instructions

##### 🍎 macOS (using Homebrew, SDKMAN!, and NVM)

1. **Install Git**:
   ```bash
   brew install git
   ```

2. **Install Java 21 (OpenJDK)**:
   You can install Java using Homebrew or via SDKMAN! (recommended for managing multiple Java versions):
   * *Option A: Via Homebrew*
     ```bash
     brew install openjdk@21
     # Link the system wrapper so macOS recognizes it

     sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
     export JAVA_HOME="/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home"
     ```
   * *Option B: Via SDKMAN!*
     ```bash
     curl -s "https://get.sdkman.io" | bash
     source "$HOME/.sdkman/bin/sdkman-init.sh"
     sdk install java 21.0.2-tem
     sdk default java 21.0.2-tem
     ```

3. **Install Node.js & npm (via NVM)**:
   ```bash
   curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
   source ~/.zshrc # or ~/.bashrc depending on your shell
   nvm install 24
   nvm use 24
   ```

4. **Install Docker & Docker Compose**:
   The easiest way is to install Docker Desktop:
   ```bash
   brew install --cask docker
   ```
   *Alternatively, start Docker from your Applications folder once installed.*

5. **Install Python & Pip**:
   ```bash
   brew install python@3.11
   # Validate version and ensure pip is linked

   python3 --version
   pip3 --version
   ```

---

##### 🐧 Ubuntu / Debian Linux (using apt, NodeSource, and Docker Repository)

1. **Install Git & Java 21**:
   ```bash
   sudo apt update
   sudo apt install -y git openjdk-21-jdk python3 python3-pip python3-venv
   # Verify Java installation

   java -version
   ```

2. **Install Node.js & npm (v24 via NodeSource)**:
   ```bash
   sudo apt install -y curl
   curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
   sudo apt install -y nodejs
   # Verify versions

   node -v
   npm -v
   ```

3. **Install Docker & Docker Compose**:
   ```bash
   sudo apt install -y ca-certificates gnupg
   sudo install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
   sudo chmod a+r /etc/apt/keyrings/docker.gpg

   echo \
     "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
     $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
     sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
   
   sudo apt update
   sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   
   # Add user to the docker group

   sudo usermod -aG docker $USER
   newgrp docker
   ```

---

##### 🪟 Windows (using winget or manual packages)

We highly recommend using Windows Package Manager (`winget`) via PowerShell (Run as Administrator):

1. **Install Git**:
   ```powershell
   winget install --id Git.Git -e --source winget
   ```

2. **Install Java 21 (Eclipse Temurin)**:
   ```powershell
   winget install --id EclipseAdoptium.Temurin.21.JDK -e --source winget
   ```

3. **Install Node.js & npm**:
   ```powershell
   winget install --id OpenJS.NodeJS.LTS -e --source winget
   ```

4. **Install Docker Desktop**:
   ```powershell
   winget install --id Docker.DockerDesktop -e --source winget
   ```

5. **Install Python & Pip**:
   ```powershell
   winget install --id Python.Python.3.11 -e --source winget
   ```

---

#### 3. Verification Command Cheat Sheet

```bash
# Verify Git

git --version

# Verify Java Compiler and Runtime

java -version
javac -version

# Verify Node.js and npm

node -v
npm -v

# Verify Docker and Docker Compose

docker --version
docker compose version

# Verify Python and pip

python3 --version || python --version
pip3 --version || pip --version
```

### One-Time Setup

1. **Spin up the Database Container**:
   Build the database image and start the PostgreSQL container:
   ```bash
   docker-compose down -v
   docker-compose up --build -d
   ```
   *Note: This will expose PostgreSQL on host port `5433` (container port `5432`) with database `agentic_ai`.*

2. **Deploy Database Migrations and Seed Data**:
   Deploy the database schemas and seed transaction/configuration records using the Liquibase runner.
   * **Local Database** (Uses `LOCAL_DATABASE_URL` from `.env` or defaults to port `5433` local container):
     ```bash
     ./scripts/deploy-liquibase.sh local
     ```
   * **Neon Cloud Database** (Uses `NEON_DATABASE_URL` from `.env`):
     ```bash
     ./scripts/deploy-liquibase.sh neon
     ```
   * **Custom Database URL**:
     ```bash
     ./scripts/deploy-liquibase.sh postgresql://user:pass@host:port/db
     ```

   > [!IMPORTANT]
   > **How to Force a Clean Database Rebuild / Reset Checklist**:
   > If you encounter checksum validation issues (due to regenerating seed files) or want to reset your local database environment from scratch, execute the following SQL commands in your database client editor to drop the existing schemas and changelog tracking tables:
   > ```sql
   > DROP SCHEMA IF EXISTS reporting CASCADE;
   > DROP SCHEMA IF EXISTS analytics CASCADE;
   > DROP TABLE IF EXISTS public.databasechangelog;
   > DROP TABLE IF EXISTS public.databasechangeloglock;
   > ```
   > After dropping them, run `./scripts/deploy-liquibase.sh local` to recreate the schemas and re-seed the tables.

3. **Initialize ADK Validation Environment**:
   Ensure `google-adk` is installed:
   ```bash
   pip install google-adk
   ```

### Per Dev Session

1. **Start the Java Backend**:
   Clean compile and launch the Spring Boot application server on port `8101` using the embedded Maven wrapper:
   - **On Windows (PowerShell/Cmd)**:
     ```cmd
     maven\apache-maven-3.9.6\bin\mvn.cmd clean compile
     maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
     ```
   - **On macOS/Linux**:
     ```bash
     ./maven/apache-maven-3.9.6/bin/mvn clean compile
     ./maven/apache-maven-3.9.6/bin/mvn spring-boot:run
     ```

2. **Run ADK Validation Agent**:
   To validate backend changes (compiling, running JUnit tests, and checking code quality/security):
   ```bash
   adk run .agents/validation
   ```

---

## Useful Commands

Below is a summary of the most useful commands for building and running the backend components:

| Category | Command | Target/CWD | Description |
| :--- | :--- | :--- | :--- |
| **Database** | `docker-compose up --build -d` | Project Root | Builds and starts database container in detached mode (exposes port `5433`) |
| **Database** | `docker-compose down -v` | Project Root | Stops the database container and deletes the persistent volume |
| **Database** | `./scripts/deploy-liquibase.sh [local\|neon]` | Project Root | Runs the Liquibase database migrations and seeds DWH/configs (defaults to local) |
| **Backend** | `maven\apache-maven-3.9.6\bin\mvn.cmd clean compile` | Project Root | Clean compile Spring Boot application (Windows) |
| **Backend** | `./maven/apache-maven-3.9.6/bin/mvn clean compile` | Project Root | Clean compile Spring Boot application (macOS/Linux) |
| **Backend** | `maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run` | Project Root | Runs the backend server on port 8101 (Windows) |
| **Backend** | `./maven/apache-maven-3.9.6/bin/mvn spring-boot:run` | Project Root | Runs the backend server on port 8101 (macOS/Linux) |
| **Backend** | `maven\apache-maven-3.9.6\bin\mvn.cmd test` | Project Root | Runs JUnit unit and integration tests (Windows) |
| **Backend** | `./maven/apache-maven-3.9.6/bin/mvn test` | Project Root | Runs JUnit unit and integration tests (macOS/Linux) |
| **ADK Agent**| `adk run .agents/validation` | Project Root | Runs the backend validation agent interactively |
| **ADK Agent**| `adk web .agents/validation` | Project Root | Launches the Web UI to chat/run backend validation tasks |

---

## End-to-End Application Flow

1. Open **[http://127.0.0.1:4200/](http://127.0.0.1:4200/)** and sign in with the default credentials:
    - **Username**: `admin`
2. Under the catalog screen, select the **Import Template** option and upload **`hybrid_reporting_template.xlsx`** (or a similar layout).
3. The backend will parse the workbook and insert metadata definitions into PostgreSQL.
4. Click on the imported report (e.g. `SALES_OVERVIEW` or `INVESTMENT_SUMMARY`), select a Reference Date (e.g. `2025-12-31`), and click **Run**.
5. The backend will dynamically fetch values, evaluate formulas, apply styles, and compile a `.xlsx` report file for direct download.
6. Browse the **Semantic Layer** tab to inspect DWH schema explore paths, joins, logical view mappings, dimensions, and measures.

---

## Database Layers

The PostgreSQL instance manages two schemas in the `agentic_ai` database:

- **`reporting.*`**: Stores metadata, report definitions, explores, views, dimension/measure columns, join paths, and import audit history.
- **`analytics.*`**: Represents the physical Data Warehouse (DWH) containing dimension and fact tables (seeding transaction, performance, investment, and sales data for 2024–2026).

---

## Troubleshooting Port Conflicts

When running the application locally, you may encounter port conflicts if the processes are not terminated cleanly (e.g., when a terminal session is closed without stopping the servers).

### 1. Identify Running Processes

To find which process is listening on a specific port:

* **macOS / Linux**:
  ```bash
  lsof -i :8101   # For backend (Port 8101)
  lsof -i :4200   # For frontend (Port 4200)
  ```
  This will print a list of running processes. Look for the `PID` column.

* **Windows**:
  ```powershell
  netstat -ano | findstr :8101
  netstat -ano | findstr :4200
  ```
  The last column in the output represents the process ID (`PID`).

### 2. Kill the Process Manually

Once you have identified the process ID (`PID`):

* **macOS / Linux**:
  ```bash
  kill -9 <PID>
  ```

* **Windows**:
  ```powershell
  taskkill /PID <PID> /F
  ```

