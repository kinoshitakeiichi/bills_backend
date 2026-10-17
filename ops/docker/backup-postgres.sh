#!/usr/bin/env bash
set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-build_postgres}"
DB_USERNAME="${DB_USERNAME:-myuser}"
DB_NAME="${DB_NAME:-mydb}"
DB_PASSWORD="${DB_PASSWORD:-mypassword}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/Backups/suji-finance}"
KEEP_BACKUPS="${KEEP_BACKUPS:-10}"

mkdir -p "$BACKUP_DIR"
timestamp="$(date '+%Y%m%d_%H%M%S')"
output="$BACKUP_DIR/${DB_NAME}_${timestamp}.dump"
tmp="${output}.tmp"
trap 'rm -f "$tmp"' EXIT

docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || {
  echo "Database container not found: $DB_CONTAINER" >&2
  exit 1
}

echo "Backing up $DB_NAME from Docker container $DB_CONTAINER..."
docker exec \
  -e "PGPASSWORD=$DB_PASSWORD" \
  "$DB_CONTAINER" \
  pg_dump \
    -U "$DB_USERNAME" \
    -d "$DB_NAME" \
    -Fc \
    --no-owner \
    --no-acl > "$tmp"

[[ -s "$tmp" ]] || { echo "Backup failed: empty dump." >&2; exit 1; }

docker exec -i "$DB_CONTAINER" pg_restore -l < "$tmp" >/dev/null

mv "$tmp" "$output"
shasum -a 256 "$output" > "${output}.sha256"

count=0
while IFS= read -r old; do
  count=$((count + 1))
  if (( count > KEEP_BACKUPS )); then
    rm -f "$old" "${old}.sha256"
  fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "${DB_NAME}_*.dump" -print | sort -r)

echo "Backup complete: $output"
