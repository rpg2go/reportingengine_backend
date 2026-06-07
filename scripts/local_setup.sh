#!/bin/bash
# =============================================================================
# Local Development Startup Script — Report Template Engine (Backend)
# =============================================================================
# Starts the full local stack in the correct order:
#   1. PostgreSQL Docker container  (port 5433)
#   2. Spring Boot backend          (port 8101)
#
# Run from anywhere:
#   ./scripts/dev.sh
#
# Prerequisites: Docker, Java 17+
# =============================================================================

set -e

# Resolve paths relative to this script, regardless of CWD
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MVN="$PROJECT_ROOT/maven/apache-maven-3.9.6/bin/mvn"

# ── Colors ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Preflight checks ──────────────────────────────────────────────────────────
command -v docker >/dev/null 2>&1 || error "Docker is not installed or not on PATH."
command -v java   >/dev/null 2>&1 || error "Java 17+ is not installed or not on PATH."
[ -f "$MVN" ] || error "Maven wrapper not found at $MVN"

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Report Template Engine — Local Dev Setup  ${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# ── Step 1: Start PostgreSQL via Docker Compose ───────────────────────────────
info "Starting PostgreSQL Docker container..."
cd "$PROJECT_ROOT"

# Bring up only the DB service (rebuild image if needed)
docker compose up --build -d report_db

# Wait for the container to pass its health check
info "Waiting for PostgreSQL to become healthy..."
RETRIES=30
until docker inspect --format='{{.State.Health.Status}}' report_template_db 2>/dev/null | grep -q "healthy"; do
  RETRIES=$((RETRIES - 1))
  if [ "$RETRIES" -le 0 ]; then
    error "PostgreSQL container did not become healthy in time. Check: docker logs report_template_db"
  fi
  printf "."
  sleep 2
done
echo ""
success "PostgreSQL is healthy on localhost:5433  (DB: agentic_ai | user: user | pass: password)"

# ── Step 2: Build and start Spring Boot ───────────────────────────────────────
echo ""
info "Compiling and starting Spring Boot backend on port 8101..."
echo -e "${YELLOW}  (Press Ctrl+C to stop)${NC}"
echo ""

"$MVN" spring-boot:run \
  -f "$PROJECT_ROOT/pom.xml" \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8101"
