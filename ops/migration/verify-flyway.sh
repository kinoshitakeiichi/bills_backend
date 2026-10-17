#!/usr/bin/env bash
set -euo pipefail

echo "== Migration files =="
find b-application/src/main/resources/db/migration \
  -maxdepth 1 -type f -name 'V*.sql' -print | sort

echo
echo "== Flyway profile =="
cat b-application/src/main/resources/application-migration.yml

echo
echo "== Tests =="
./gradlew clean test --no-daemon

echo
echo "To perform the first live migration, restart backend with:"
echo "  SPRING_PROFILES_ACTIVE=prod,migration"
echo "or append ',migration' to your existing active profile."
