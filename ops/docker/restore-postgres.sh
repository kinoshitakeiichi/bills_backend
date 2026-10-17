#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 1 ]] || { echo "Usage: $0 /path/to/database.dump" >&2; exit 2; }

DUMP_FILE="$1"
DB_CONTAINER="${DB_CONTAINER:-build_postgres}"
DB_USERNAME="${DB_USERNAME:-myuser}"
DB_NAME="${DB_NAME:-mydb}"
DB_PASSWORD="${DB_PASSWORD:-mypassword}"

[[ -f "$DUMP_FILE" ]] || { echo "Dump not found: $DUMP_FILE" >&2; exit 1; }
docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || {
  echo "Database container not found: $DB_CONTAINER" >&2
  exit 1
}

if [[ -f "${DUMP_FILE}.sha256" ]]; then
  (
    cd "$(dirname "$DUMP_FILE")"
    shasum -a 256 -c "$(basename "${DUMP_FILE}.sha256")"
  )
fi

echo "WARNING: restoring will replace objects in '$DB_NAME' on '$DB_CONTAINER'."
read -r -p "Type '$DB_NAME' to continue: " confirmation
[[ "$confirmation" == "$DB_NAME" ]] || { echo "Cancelled."; exit 1; }

docker exec -i \
  -e "PGPASSWORD=$DB_PASSWORD" \
  "$DB_CONTAINER" \
  pg_restore \
    -U "$DB_USERNAME" \
    -d "$DB_NAME" \
    --clean \
    --if-exists \
    --no-owner \
    --no-acl < "$DUMP_FILE"

echo "Restore complete."
