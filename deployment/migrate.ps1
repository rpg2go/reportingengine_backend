if (-not $env:DATABASE_URL) {
    Write-Error "Error: DATABASE_URL environment variable is not set."
    exit 1
}

Write-Host "Applying migrations to Neon database..."
$migrations = Get-ChildItem -Path "db/migrations/*.sql" | Sort-Object Name
foreach ($file in $migrations) {
    Write-Host "Running $($file.Name)..."
    psql $env:DATABASE_URL -f $file.FullName
}
Write-Host "All migrations applied successfully!"
