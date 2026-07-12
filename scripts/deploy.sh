#!/bin/bash
# macOS/Linux Deployment Script for Reporting Engine
# Run this script from anywhere — paths are resolved relative to this script's location.
#
# Default deployment region: europe-west3 (Frankfurt, Germany)
# Override by setting GCP_REGION in your .env file.

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Helper to load .env variables
if [ -f "${SCRIPT_DIR}/../.env" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    # Skip comments and empty lines
    if [[ ! "$line" =~ ^# ]] && [[ "$line" =~ = ]]; then
      # Strip carriage returns if file has Windows line endings
      clean_line=$(echo "$line" | tr -d '\r')
      export "$clean_line"
    fi
  done < "${SCRIPT_DIR}/../.env"
else
  echo "Error: .env file not found at ${SCRIPT_DIR}/../.env"
  exit 1
fi

# Default to Frankfurt (europe-west3) if not set in .env
GCP_REGION="${GCP_REGION:-europe-west3}"

# Validate required variables
required_vars=("GCP_PROJECT_ID" "BACKEND_SERVICE_NAME" "SPRING_DATASOURCE_URL" "SPRING_DATASOURCE_USERNAME" "SPRING_DATASOURCE_PASSWORD")
for var in "${required_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "Error: Required environment variable $var is missing from .env"
    exit 1
  fi
done

echo "Deployment region: ${GCP_REGION}"

echo "Setting gcloud project to ${GCP_PROJECT_ID}..."
gcloud config set project ${GCP_PROJECT_ID}

echo "Deploying Backend Service (${BACKEND_SERVICE_NAME}) to Cloud Run..."
gcloud run deploy ${BACKEND_SERVICE_NAME} \
  --source "${SCRIPT_DIR}/.." \
  --region ${GCP_REGION} \
  --port 8080 \
  --memory 1Gi \
  --set-env-vars="^;^SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL};SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME};SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD};SECURITY_ADMIN_USERNAME=${SECURITY_ADMIN_USERNAME};SECURITY_ADMIN_PASSWORD=${SECURITY_ADMIN_PASSWORD};SECURITY_ADMIN_ROLE=${SECURITY_ADMIN_ROLE};CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS};SPRING_PROFILES_ACTIVE=dev;MOCK_FALLBACK_TOKEN={MOCK_FALLBACK_TOKEN}" \
  --allow-unauthenticated \
  --liveness-probe=httpGet.path=/actuator/health/liveness,httpGet.port=8080 \
  --startup-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,periodSeconds=10,failureThreshold=12 \
  --quiet

echo "Retrieving Backend URL..."
BACKEND_URL=$(gcloud run services describe ${BACKEND_SERVICE_NAME} --region ${GCP_REGION} --format "value(status.url)")
echo "Backend URL: ${BACKEND_URL}"

echo "=========================================="
echo "Deployment completed successfully!"
echo "Backend API URL: ${BACKEND_URL}"
echo "=========================================="
