# Windows PowerShell Deployment Script for Reporting Engine
# Run this script from anywhere — paths are resolved relative to this script's location ($PSScriptRoot).
#
# Default deployment region: europe-west3 (Frankfurt, Germany)
# Override by setting GCP_REGION in your .env file.

# Helper to load .env variables into script scope
function Load-Env {
    param ($Path)
    if (Test-Path $Path) {
        Get-Content $Path | ForEach-Object {
            $line = $_.Trim()
            if ($line -and !$line.StartsWith('#') -and $line.Contains('=')) {
                $parts = $line.Split('=', 2)
                $key = $parts[0].Trim()
                $value = $parts[1].Trim()
                # Remove optional surrounding quotes
                if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                Set-Variable -Name $key -Value $value -Scope Script
            }
        }
    } else {
        Write-Error "Error: .env file not found at $Path"
        exit 1
    }
}

# Load variables from the project root .env
Load-Env -Path "$PSScriptRoot/../.env"

# Default to Frankfurt (europe-west3) if GCP_REGION was not in .env
if (-not (Get-Variable -Name GCP_REGION -Scope Script -ErrorAction SilentlyContinue)) {
    $GCP_REGION = "europe-west3"
}
Write-Host "Deployment region: $GCP_REGION" -ForegroundColor Cyan

# Validate required variables
$requiredVars = @("GCP_PROJECT_ID", "BACKEND_SERVICE_NAME", "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD")
foreach ($var in $requiredVars) {
    if (-not (Get-Variable -Name $var -ErrorAction SilentlyContinue)) {
        Write-Error "Error: Required environment variable $var is missing from .env"
        exit 1
    }
}

Write-Host "Setting gcloud project to $GCP_PROJECT_ID..." -ForegroundColor Cyan
gcloud config set project $GCP_PROJECT_ID

Write-Host "Deploying Backend Service ($BACKEND_SERVICE_NAME) to Cloud Run..." -ForegroundColor Cyan
gcloud run deploy $BACKEND_SERVICE_NAME `
  --source "$PSScriptRoot/.." `
  --region $GCP_REGION `
  --set-env-vars="SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME,SPRING_DATASOURCE_PASSWORD=$SPRING_DATASOURCE_PASSWORD,SPRING_PROFILES_ACTIVE=dev" `
  --allow-unauthenticated `
  --quiet

if ($LASTEXITCODE -ne 0) {
  Write-Error "Backend deployment failed!"
  exit $LASTEXITCODE
}

Write-Host "Retrieving Backend URL..." -ForegroundColor Cyan
$BACKEND_URL = (gcloud run services describe $BACKEND_SERVICE_NAME --region $GCP_REGION --format "value(status.url)")
Write-Host "Backend URL: $BACKEND_URL" -ForegroundColor Green

Write-Host "==========================================" -ForegroundColor Green
Write-Host "Deployment completed successfully!" -ForegroundColor Green
Write-Host "Backend API URL: $BACKEND_URL" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
