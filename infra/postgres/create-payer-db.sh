#!/usr/bin/env bash
set -euo pipefail

if psql -tAc "SELECT 1 FROM pg_database WHERE datname = 'hapi_payer'" | grep -q 1; then
  echo "Database hapi_payer already exists."
else
  psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE hapi_payer OWNER hapi"
  echo "Created database hapi_payer."
fi
