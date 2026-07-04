#!/bin/bash
# ==============================================================================
# Liquibase Deployment Execution Script
# Description: Triggers the Liquibase migrations using the Maven wrapper.
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
    DB_USER="user"
    DB_PASSWORD="password"
    DB_URL="jdbc:postgresql://$clean_uri"
  fi
}

# Resolve target environment (defaults to local)
TARGET_ENV=${1:-"local"}

if [ "$TARGET_ENV" = "local" ]; then
  URI_TO_PARSE=$LOCAL_DATABASE_URL
  echo "Target environment: LOCAL database"
elif [ "$TARGET_ENV" = "neon" ]; then
  URI_TO_PARSE=$NEON_DATABASE_URL
  echo "Target environment: NEON cloud database"
else
  # If a specific postgres:// URL is provided directly
  URI_TO_PARSE=$TARGET_ENV
  echo "Target environment: Custom connection URL"
fi

# Fallback default if URI is empty
URI_TO_PARSE=${URI_TO_PARSE:-"postgresql://user:password@127.0.0.1:5433/agentic_ai"}

# Parse the resolved URI
parse_postgres_uri "$URI_TO_PARSE"

echo "======================================================================"
echo "🚀 Triggering Liquibase Database Deployment"
echo "======================================================================"
echo "Target URL:  $DB_URL"
echo "Username:    $DB_USER"
echo "Changelog:   db/liquibase/db.changelog-master.xml"
echo "======================================================================"

# Run liquibase via maven
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
  echo "❌ Liquibase deployment failed. Please check logs above."
  echo "======================================================================"
  exit 1
fi
