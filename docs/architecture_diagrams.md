# 📊 Metadata-Driven Report Builder - Architectural Topology & Sequence Diagrams

This document outlines the system component boundaries, deployment patterns, and the end-to-end request-to-response sequence lifecycle mapping of our reporting backend solution.

---

## 1. Google Cloud Platform (GCP) Component & Systems Integration Architecture

This diagram details the deep infrastructure topology on GCP, illustrating ingress, containerized execution runtime, CI/CD deployment pipelines, IAM service accounts, security boundaries, and telemetry logging.

```mermaid
graph TB
    %% Client & Ingress Tiers
    subgraph IngressTier ["GCP Edge & Ingress Routing"]
        Analyst["Business Analyst (Browser)"]
        CloudDNS["Google Cloud DNS"]
        CloudArmor["Google Cloud Armor (WAF / DDoS protection)"]
        GCP_LB["Google Cloud Load Balancing (HTTPS)"]
        GCS_Bucket["Google Cloud Storage (GCS)<br>Host: Angular Standalone App assets"]
    end

    %% Security & Config
    subgraph SecurityTier ["Identity, Security, & Credentials"]
        SecretManager["Google Secret Manager<br>(Postgres Credentials / App Configs)"]
        CloudIAM["Google Cloud IAM<br>(Service Accounts & Roles)"]
    end

    %% CI/CD & Deploy Pipelines
    subgraph PipelineTier ["CI/CD Build & Container Registry"]
        ArtifactRegistry["GCP Artifact Registry<br>(Docker Container Images)"]
        CloudBuild["Google Cloud Build<br>(Build / Deployment runner)"]
    end

    %% Serverless Compute Tier
    subgraph ComputeTier ["Serverless Runtime (Google Cloud Run)"]
        subgraph RunSvc ["Cloud Run Service: reporting-backend"]
            direction TB
            ServiceSA["Service Account:<br>report-backend-sa"]
            LoomJVM["Java 21 JVM (Spring Boot 3.5)<br>Virtual Threads Enabled"]
            ASTEngine["Pattern Matching AST Compiler"]
            POIExporter["Apache POI Streaming Exporter"]
        end

        subgraph RunJob ["Cloud Run Job: db-migrator"]
            direction TB
            JobSA["Service Account:<br>report-migrator-sa"]
            LiquibaseRunner["Liquibase Migration Tool<br>(Pure SQL Changesets)"]
        end
    end

    %% Storage & Warehouse Tier
    subgraph StorageTier ["Data Storage Tier (Google Cloud SQL for PostgreSQL)"]
        PostgresDB[("PostgreSQL DB Instance<br>(Cloud SQL / Neon)")]
        ReportingSchema["reporting schema<br>[Config & Layout Metadata]"]
        AnalyticsSchema["analytics schema<br>[Data Warehouse Tables]"]
    end

    %% Observability Tier
    subgraph MonitorTier ["Google Cloud Observability Suite"]
        CloudLogging["Google Cloud Logging"]
        CloudTrace["Google Cloud Trace (OTel spans)"]
    end

    %% -------------------------------------------------------------------------
    %% Relations & Interaction Flows
    
    %% Ingress Flow
    Analyst -->|"1. Resolves Domain"| CloudDNS
    Analyst -->|"2. HTTPS Request"| CloudArmor
    CloudArmor --> GCP_LB
    GCP_LB -->|"Route Static Assets (*)"| GCS_Bucket
    GCP_LB -->|"Route API Requests (/api/*)"| RunSvc

    %% CI/CD Pipeline Flow
    CloudBuild -->|"Builds & Pushes images"| ArtifactRegistry
    ArtifactRegistry -->|"Pulls Service Image"| RunSvc
    ArtifactRegistry -->|"Pulls Migrator Image"| RunJob
    CloudBuild -->|"Triggers Migrations"| RunJob

    %% Security & Configuration Access
    ServiceSA -->|"Access Secrets via IAM role"| SecretManager
    JobSA -->|"Access Secrets via IAM role"| SecretManager
    SecretManager -->|"Supplies runtime vars (Basic Auth/DB URL)"| LoomJVM
    SecretManager -->|"Supplies migration DB credentials"| LiquibaseRunner

    %% Job Execution
    LiquibaseRunner -->|"Applies schema migrations (JDBC TLS)"| ReportingSchema

    %% Service Execution & Data Flow
    LoomJVM -->|"1. Compiles Row Expressions"| ASTEngine
    LoomJVM -->|"2. Reads report templates (JDBC)"| ReportingSchema
    LoomJVM -->|"3. Queries fact tables (JDBC)"| AnalyticsSchema
    AnalyticsSchema -->|"Streams query result set"| LoomJVM
    LoomJVM -->|"4. Feeds rows to stream"| POIExporter
    POIExporter -->|"5. Binary stream payload"| GCP_LB

    %% Observability Integration
    LoomJVM -->|"App Logs (Structured JSON)"| CloudLogging
    LoomJVM -->|"Spans (OpenTelemetry)"| CloudTrace

    %% Styling and colors
    classDef client fill:#e1f5fe,stroke:#01579b,stroke-width:2px,color:#01579b;
    classDef ingress fill:#fff3e0,stroke:#e65100,stroke-width:2px,color:#e65100;
    classDef security fill:#ffebee,stroke:#c62828,stroke-width:2px,color:#c62828;
    classDef pipeline fill:#efebe9,stroke:#4e342e,stroke-width:2px,color:#4e342e;
    classDef compute fill:#ede7f6,stroke:#4a148c,stroke-width:2px,color:#4a148c;
    classDef database fill:#e8f5e9,stroke:#1b5e20,stroke-width:2px,color:#1b5e20;
    classDef monitor fill:#eceff1,stroke:#37474f,stroke-width:2px,color:#37474f;

    class Analyst client;
    class CloudDNS,CloudArmor,GCP_LB,GCS_Bucket ingress;
    class SecretManager,CloudIAM security;
    class ArtifactRegistry,CloudBuild pipeline;
    class RunSvc,RunJob,LoomJVM,ASTEngine,POIExporter,LiquibaseRunner compute;
    class PostgresDB,ReportingSchema,AnalyticsSchema database;
    class CloudLogging,CloudTrace monitor;
```

---

## 2. End-to-End Sequential Request Lifecycle Diagram

This sequence diagram traces the chronological execution lifecycle of a single report run request from the browser down to the Cloud SQL PostgreSQL database and POI streaming.

```mermaid
sequenceDiagram
    autonumber
    actor Analyst as Business Analyst (Browser)
    participant Angular as Angular 21 SPA Client
    participant Controller as ReportController (API)
    participant LoomThread as Loom Virtual Thread
    participant AST as FilterCompilerService
    participant Router as SchemaGraphRouter
    participant DB as PostgreSQL (Cloud SQL)
    participant POI as LayoutRenderer (SXSSF)

    Analyst->>Angular: Clicks "⚡ Run Report" (Hierarchical layout)
    Angular->>Controller: POST /api/reports/{id}/run (Config JSON)
    activate Controller
    Note over Controller: spring.threads.virtual.enabled = true
    Controller->>LoomThread: Spawns lightweight Virtual Thread
    activate LoomThread
    Controller-->>Angular: Accepts HTTP connection (keeps socket open)
    
    %% AST Compilation
    LoomThread->>AST: compileRowFilter(FilterNode AST)
    activate AST
    Note over AST: Java 21 Sealed class Pattern Matching
    AST-->>LoomThread: Returns compiled SQL filter snippet
    deactivate AST
    
    %% Join Path Resolution
    LoomThread->>Router: getJoinGraphPath(factTable, dims)
    activate Router
    Note over Router: Dijkstra BFS with FK conformed cost weights
    Router-->>LoomThread: Returns LEFT JOIN SQL clauses
    deactivate Router

    %% PostgreSQL Query Execution
    LoomThread->>DB: Execute consolidated SQL (with optimized date intervals)
    activate DB
    Note over DB: Runs query against analytics schema tables
    DB-->>LoomThread: Streams cursor rows back
    deactivate DB

    %% Apache POI Streaming
    LoomThread->>POI: pipeRowsToWorkbook(SXSSFWorkbook)
    activate POI
    Note over POI: Direct in-memory compression & socket flush
    POI->>Controller: Flushes spreadsheet chunks continuously
    deactivate POI
    
    LoomThread-->>Controller: Task complete (thread terminates)
    deactivate LoomThread
    
    Controller-->>Angular: Live binary streaming output (application/octet-stream)
    deactivate Controller
    
    Angular-->>Analyst: Analyst browser triggers dialog for final publication-ready file (.xlsx)
```
