#!/bin/bash
# ==============================================================================
# Liquibase Deployment Execution Script
# Description: Triggers Liquibase migrations using Maven wrapper with database
#              auto-creation and clean reset options.
# ==============================================================================

# Navigate to project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

# Load environment variables from .env file if it exists
if [ -f .env ]; then
  echo "Found .env file. Loading configuration..."
  export $(grep -v '^#' .env | xargs)
fi

# Parse parameters (environment and clean flag)
TARGET_ARG=${1:-"local"}
CLEAN_MODE=false

if [ "$1" = "clean" ] || [ "$1" = "--clean" ]; then
  TARGET_ENV="local"
  CLEAN_MODE=true
elif [ "$2" = "clean" ] || [ "$2" = "--clean" ]; then
  TARGET_ENV=$1
  CLEAN_MODE=true
else
  TARGET_ENV=$TARGET_ARG
fi

# Function to parse postgresql:// credentials and host details into JDBC format
parse_postgres_uri() {
  local uri=$1
  local clean_uri=${uri#*://}
  
  if [[ "$clean_uri" == *"@"* ]]; then
    local credentials=${clean_uri%%@*}
    local host_port_db=${clean_uri#*@}
    
    if [[ "$credentials" == *":"* ]]; then
      DB_USER=${credentials%%:*}
      DB_PASSWORD=${credentials#*:}
    else
      DB_USER=$credentials
      DB_PASSWORD=""
    fi
    DB_URL="jdbc:postgresql://$host_port_db"
  else
    DB_USER=$SPRING_DATASOURCE_USERNAME
    DB_PASSWORD=$SPRING_DATASOURCE_PASSWORD
    DB_URL="jdbc:postgresql://$clean_uri"
  fi

  if [ -z "$DB_USER" ] || [ -z "$DB_PASSWORD" ]; then
    echo "❌ Error: SPRING_DATASOURCE_USERNAME or SPRING_DATASOURCE_PASSWORD is not defined in .env file or environment variables!"
    exit 1
  fi

  # Extract DB_NAME from DB_URL (strip query params if present)
  local path_part="${DB_URL#*://*/}"
  path_part="${path_part%%\?*}"
  DB_NAME="${path_part}"
  if [ -z "$DB_NAME" ]; then
    DB_NAME="reporting_db"
  fi
}

if [ "$TARGET_ENV" = "local" ] || [ -z "$TARGET_ENV" ]; then
  URI_TO_PARSE=${SPRING_DATASOURCE_URL:-${LOCAL_DATABASE_URL:-$DATABASE_URL}}
  echo "Target environment: LOCAL database ($URI_TO_PARSE)"
elif [ "$TARGET_ENV" = "neon" ]; then
  URI_TO_PARSE=$NEON_DATABASE_URL
  echo "Target environment: NEON cloud database ($URI_TO_PARSE)"
else
  # If a specific postgres:// URL is provided directly
  URI_TO_PARSE=$TARGET_ENV
  echo "Target environment: Custom connection URL ($URI_TO_PARSE)"
fi

if [ -z "$URI_TO_PARSE" ]; then
  echo "❌ Error: Target database URL (LOCAL_DATABASE_URL / NEON_DATABASE_URL / DATABASE_URL) is not defined in .env file or environment variables!"
  exit 1
fi

# Parse the resolved URI
parse_postgres_uri "$URI_TO_PARSE"

echo "======================================================================"
echo "🚀 Triggering Liquibase Database Deployment"
echo "======================================================================"
echo "Target URL:  $DB_URL"
echo "Target DB:   $DB_NAME"
echo "Username:    $DB_USER"
echo "Changelog:   db/liquibase/db.changelog-master.xml"
if [ "$CLEAN_MODE" = true ]; then
  echo "Mode:        CLEAN RESET (Schemas & Changelogs will be recreated)"
fi
echo "======================================================================"

# ── Preflight: Check Docker container & Auto-create Database if needed ────────
CONTAINER_NAME="report_template_db"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER_NAME}$"; then
  # Check if target database exists inside local docker container
  DB_EXISTS=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME';" 2>/dev/null)
  
  if [ "$DB_EXISTS" != "1" ]; then
    echo "⚠️ Database '$DB_NAME' does not exist in container. Auto-creating database '$DB_NAME'..."
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;" 2>/dev/null
  fi

  # Perform Clean Wipe if clean flag is set
  if [ "$CLEAN_MODE" = true ]; then
    echo "🧹 Executing clean wipe of existing database schemas..."
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "
      DROP SCHEMA IF EXISTS report_builder_owner CASCADE;
      DROP SCHEMA IF EXISTS catalog_owner CASCADE;
      DROP SCHEMA IF EXISTS reporting CASCADE;
      DROP SCHEMA IF EXISTS catalog CASCADE;
      DROP SCHEMA IF EXISTS analytics CASCADE;
      DROP TABLE IF EXISTS public.databasechangelog CASCADE;
      DROP TABLE IF EXISTS public.databasechangeloglock CASCADE;
    " 2>/dev/null
  fi
fi

# Run Liquibase via maven
./maven/apache-maven-3.9.6/bin/mvn liquibase:update \
  -Dliquibase.url="$DB_URL" \
  -Dliquibase.username="$DB_USER" \
  -Dliquibase.password="$DB_PASSWORD"

if [ $? -eq 0 ]; then
  echo "======================================================================"
  echo "✅ Database tables and data seeded successfully!"
  echo "======================================================================"
else
  echo "======================================================================"
  echo "❌ Liquibase deployment failed. Check logs above."
  echo "💡 Tip for local reset: Run './scripts/deploy-liquibase.sh local clean' or 'docker compose down -v'"
  echo "======================================================================"
  exit 1
fi
