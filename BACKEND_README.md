# Reporting Engine Back-End — Enterprise Developer Guide

An enterprise-grade, metadata-driven report configuration and execution engine built on Spring Boot. This repository houses the core Java services, database migration runner, and custom execution engines.

---

## 1. Architectural Stack Overview

The backend is architected as a high-performance Spring Boot application prioritizing low-latency reads, structured data layers, and safe mathematical evaluation.

### Core Technologies
*   **Java Runtime:** Java 17 (LTS)
*   **Framework:** Spring Boot v3.2.4
*   **Persistence:** Spring Data JPA (Hibernate v6.x) for configuration CRUD operations.
*   **Direct JDBC Optimization:** Direct JDBC Template with `RowCallbackHandler` bypassing Hibernate hydration for the critical read hot-path (`loadFromDb()`). This optimization reduces report configuration latency from ~163ms to ~59ms.
*   **Excel Engine:** Apache POI (v5.2.5) for cell-level layout extraction and styled spreadsheet generation.
*   **Formula Engine:** `exp4j` (v0.4.8) for fast, isolated, sandbox-safe mathematical evaluation of cell and row formulas (preventing SQL or script injection).
*   **Database:** PostgreSQL 16 (hosted via Docker container locally; Neon Serverless Postgres in production).

### Database Schema Layers
The database maintains two separated logical schemas inside the `agentic_ai` database:
1.  **`reporting` Schema:** Stores metadata configurations (`rpt_report`, `rpt_column_def`, `rpt_row`, `rpt_row_metric`, `rpt_row_formula`, `rpt_row_column_map`). Saves are handled via cascade-delete overwrites to prevent orphans.
2.  **`analytics` Schema:** Represents the physical Data Warehouse (DWH) containing dimension and fact tables (seeding transaction, performance, investment, and sales data).

---

## 2. Comprehensive System Prerequisites

To run, build, and test the backend, your system must meet the following prerequisites:

### Runtimes and Databases
*   **Java Development Kit (JDK) 17:** Required for compiling and running the application. OpenJDK 17 or Eclipse Temurin 17 are recommended.
*   **Docker Engine & Docker Compose:** Required to run the local PostgreSQL 16 container.
*   **Python v3.10+ & Pip:** Required to execute the ADK validation agent (`.agents/validation`).

### Default Port Bindings & Environment Variables
The application reads settings from `src/main/resources/application.properties`. You can override them via environment variables:

| Property / Env Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` / `server.port` | `8101` | The port the backend listens on. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5433/agentic_ai` | PostgreSQL connection URL. Note that local port maps to **`5433`** on the host. |
| `SPRING_DATASOURCE_USERNAME` | `user` | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Database password. |

---

## 3. Step-by-Step Multi-OS Environment Setup Commands

### 🍎 macOS Setup (using Homebrew & SDKMAN!)

1.  **Install Homebrew** (if not installed):
    ```bash
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    ```

2.  **Install Git and Docker**:
    ```bash
    brew install git
    brew install --cask docker
    ```
    *Open the Docker desktop app to start the Docker daemon.*

3.  **Install Java 17 (via SDKMAN!)**:
    ```bash
    curl -s "https://get.sdkman.io" | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install java 17.0.10-tem
    # Set as default
    sdk default java 17.0.10-tem
    ```

4.  **Install Python 3 & Pip**:
    ```bash
    brew install python@3.11
    ```

---

### 🐧 Ubuntu / Debian Setup (using apt & Docker Engine Repo)

1.  **Install Git and Java 17**:
    ```bash
    sudo apt update
    sudo apt install -y git openjdk-17-jdk python3 python3-pip python3-venv
    ```

2.  **Install Docker Engine & Docker Compose**:
    ```bash
    sudo apt install -y ca-certificates curl gnupg
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    sudo apt update
    sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    
    # Add your user to docker group to run without sudo
    sudo usermod -aG docker $USER
    newgrp docker
    ```

---

### 🪟 Windows Setup (using winget via PowerShell Admin)

```powershell
# Install Git
winget install --id Git.Git -e --source winget

# Install Eclipse Temurin JDK 17
winget install --id EclipseAdoptium.Temurin.17.JDK -e --source winget

# Install Docker Desktop (Requires restart)
winget install --id Docker.DockerDesktop -e --source winget

# Install Python 3
winget install --id Python.Python.3.11 -e --source winget
```

---

## 4. Local Run & Verification Guide

### Step 1: Start PostgreSQL DB Container
Run the following from the project root:
```bash
docker-compose down -v
docker-compose up --build -d
```
This builds a custom Postgres container that automatically applies migrations `000` to `008` from `/db/migrations/` and binds to host port `5433`.

### Step 2: Build & Start Spring Boot Backend
The project embeds Apache Maven 3.9.6 wrapper in `maven/`. Use it to run:
*   **On macOS/Linux:**
    ```bash
    ./maven/apache-maven-3.9.6/bin/mvn clean compile
    ./maven/apache-maven-3.9.6/bin/mvn spring-boot:run
    ```
*   **On Windows (PowerShell/CMD):**
    ```cmd
    maven\apache-maven-3.9.6\bin\mvn.cmd clean compile
    maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
    ```

### Step 3: Run Validation & Tests
*   **Run Backend JUnit & Integration Tests:**
    ```bash
    # On macOS/Linux
    ./maven/apache-maven-3.9.6/bin/mvn test
    # On Windows
    maven\apache-maven-3.9.6\bin\mvn.cmd test
    ```
*   **Run ADK Validation Agent:**
    ```bash
    pip install google-adk
    adk run .agents/validation
    ```
