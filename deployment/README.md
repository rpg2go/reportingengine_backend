# Deployment & Infrastructure

This document outlines the deployment configuration, application packaging, and environment setup guidelines for the Reporting Engine.

---

## Deployment Overview

The platform is designed to run in containerized environments. There are two primary deployment models:

1. **Local Development / Single VM**: Managed via Docker Compose orchestrating the application backend, frontend, and PostgreSQL database.
2. **Cloud Run / Managed Containers (Production)**: The backend and frontend are built as separate Docker images and deployed to a container hosting service (like Google Cloud Run), while the database connects to a managed SQL service (such as Cloud SQL for PostgreSQL).

---

## Application Packaging

### 1. Database Image

- Bundles PostgreSQL 16 on Alpine.
- Copies all migrations located in [db/migrations/](../db/migrations) to `/docker-entrypoint-initdb.d/` so they run automatically when the container starts.

### 2. Backend (Spring Boot Jar)

- Packaged as a executable fat JAR.
- Compiled via Maven:
    ```bash
    maven/apache-maven-3.9.6/bin/mvn clean package -DskipTests
    ```

### 3. Frontend (Angular static bundle)

- Compiled into static HTML/CSS/JS files inside `dist/frontend`.
- Built via npm:
    ```bash
    npm run build
    ```
- In production, these static assets are typically served through an Nginx container.

---

## Prerequisites

To package and deploy this project, your environment must meet the core runtime dependencies:

- **Java JDK 17** (to compile the Spring Boot backend)
- **Node.js (v24+) & npm** (to build the Angular frontend bundle)
- **Docker & Docker Compose** (to orchestrate and run local containers)

> [!NOTE]
> For complete, step-by-step installation instructions for macOS, Ubuntu/Debian, and Windows, please refer to the **[Prerequisites Section in the Main README](../README.md#prerequisites)**.

---

## By Script (Docker Compose)

For local and single-instance VM deployments, use Docker Compose to stand up the entire stack.

**1. Spin Up the Stack**:

```bash
docker-compose up --build -d
```

This builds the custom Postgres database container and mounts the persistent volume `pgdata`.

**2. Tear Down the Stack**:
To stop the application and clean up container assets:

```bash
docker-compose down -v
```

_Warning: The `-v` flag removes the Postgres database volumes, resetting all data._

---

## CI/CD Pipelines

A standard deployment pipeline should implement the following stages:

```mermaid
graph LR
    A[Commit Code] --> B[Run Unit Tests]
    B --> C[Build Docker Images]
    C --> D[Push to Container Registry]
    D --> E[Deploy to Staging/Prod]
```

1. **Continuous Integration**:
    - Runs JUnit tests (`mvn test`) and Angular specs (`npm test`).
    - Validates code style and compilation flags.
2. **Continuous Deployment**:
    - Triggered on merge to `main`.
    - Builds production images using the dockerfiles and pushes them to a secure container registry (e.g., Google Artifact Registry).
    - Updates target server instance tasks or Cloud Run services to pull the latest image versions.
