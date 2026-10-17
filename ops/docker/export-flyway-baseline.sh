#!/usr/bin/env bash
set -euo pipefail

# Export the *actual live database schema* as the source for the Flyway baseline.
# No data is exported and nothing is modified in PostgreSQL.
#
# Defaults match the current docker-compose.yml:
#   container: build_postgres
#   database : mydb
#   user     : myuser
#
# Credentials can be supplied through the same .env variables used by Compose:
#   POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD

DB_CONTAINER="${DB_CONTAINER:-build_postgres}"
POSTGRES_DB="${POSTGRES_DB:-mydb}"
POSTGRES_USER="${POSTGRES_USER:-myuser}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-}"
OUTPUT_DIR="${OUTPUT_DIR:-$PWD/flyway-baseline}"

mkdir -p "$OUTPUT_DIR"

docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || {
  echo "Database container not found: $DB_CONTAINER" >&2
  exit 1
}

timestamp="$(date '+%Y%m%d_%H%M%S')"
raw="$OUTPUT_DIR/live-schema-${timestamp}.sql"
clean="$OUTPUT_DIR/V1__baseline_live_schema.sql"

echo "Exporting live schema from $DB_CONTAINER/$POSTGRES_DB ..."

docker exec \
  -e "PGPASSWORD=$POSTGRES_PASSWORD" \
  "$DB_CONTAINER" \
  pg_dump \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    --schema-only \
    --no-owner \
    --no-privileges \
    --schema=bills \
    --quote-all-identifiers \
  > "$raw"

[[ -s "$raw" ]] || {
  echo "Schema export failed: empty output." >&2
  exit 1
}

# Normalize environment-specific pg_dump noise while preserving all DDL.
# Keep the raw file alongside it for comparison.
sed \
  -e '/^-- Dumped from database version /d' \
  -e '/^-- Dumped by pg_dump version /d' \
  -e '/^SET statement_timeout = /d' \
  -e '/^SET lock_timeout = /d' \
  -e '/^SET idle_in_transaction_session_timeout = /d' \
  -e '/^SET transaction_timeout = /d' \
  -e '/^SET client_encoding = /d' \
  -e '/^SET standard_conforming_strings = /d' \
  -e '/^SELECT pg_catalog.set_config/d' \
  "$raw" > "$clean"

echo
echo "Baseline candidate generated:"
echo "  raw:        $raw"
echo "  normalized: $clean"
echo
echo "Nothing has been migrated yet."
echo "Review the normalized file before V44.3.2 enables Flyway."
