# =============================================================================
# Local Development Startup Script — Report Template Engine (Backend)
# =============================================================================
# Starts the full local stack in the correct order:
#   1. PostgreSQL Docker container  (port 5432)
#   2. Spring Boot backend          (port 8101)
#
# Run from anywhere:
#   .\scripts\dev.ps1
#
# Prerequisites: Docker Desktop, Java 17+
# =============================================================================

$ErrorActionPreference = "Stop"

# Resolve paths relative to this script, regardless of CWD
$ProjectRoot = Resolve-Path "$PSScriptRoot/.."
$Mvn         = Join-Path $ProjectRoot "maven\apache-maven-3.9.6\bin\mvn.cmd"

# ── Preflight checks ──────────────────────────────────────────────────────────
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed or not on PATH."; exit 1
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java 17+ is not installed or not on PATH."; exit 1
}
if (-not (Test-Path $Mvn)) {
    Write-Error "Maven wrapper not found at $Mvn"; exit 1
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Report Template Engine — Local Dev Setup  " -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""

# ── Step 1: Start PostgreSQL via Docker Compose ───────────────────────────────
Write-Host "[INFO]  Starting PostgreSQL Docker container..." -ForegroundColor Cyan
Set-Location $ProjectRoot

# Bring up only the DB service (rebuild image if needed)
docker compose up --build -d report_db
if ($LASTEXITCODE -ne 0) { Write-Error "docker compose up failed."; exit 1 }

# Wait for the container to pass its health check
Write-Host "[INFO]  Waiting for PostgreSQL to become healthy..." -ForegroundColor Cyan
$retries = 30
while ($true) {
    $status = docker inspect --format='{{.State.Health.Status}}' report_template_db 2>$null
    if ($status -eq "healthy") { break }
    $retries--
    if ($retries -le 0) {
        Write-Error "PostgreSQL container did not become healthy in time.`nCheck logs: docker logs report_template_db"
        exit 1
    }
    Write-Host "." -NoNewline
    Start-Sleep -Seconds 2
}
Write-Host ""
Write-Host "[OK]    PostgreSQL is healthy on localhost:5432  (DB: agentic_ai | user: user | pass: password)" -ForegroundColor Green

# ── Step 2: Build and start Spring Boot ───────────────────────────────────────
Write-Host ""
Write-Host "[INFO]  Compiling and starting Spring Boot backend on port 8101..." -ForegroundColor Cyan
Write-Host "        (Press Ctrl+C to stop)" -ForegroundColor Yellow
Write-Host ""

& $Mvn spring-boot:run `
    -f "$ProjectRoot\pom.xml" `
    "-Dspring-boot.run.jvmArguments=-Dserver.port=8101"
