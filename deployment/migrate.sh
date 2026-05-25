#!/bin/bash
if [ -z "$DATABASE_URL" ]; then
  echo "Error: DATABASE_URL environment variable is not set."
  exit 1
fi

echo "Applying migrations to Neon database..."
for file in db/migrations/*.sql; do
  echo "Running $file..."
  psql "$DATABASE_URL" -f "$file"
done
echo "All migrations applied successfully!"
