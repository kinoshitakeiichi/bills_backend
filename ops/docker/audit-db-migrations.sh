#!/usr/bin/env bash
set -euo pipefail

echo "== Current migration directory =="
find b-application/src/main/resources/db/migration -maxdepth 1 -type f -print | sort || true

echo
echo "== Manual admin SQL =="
find b-application/src/main/resources/db/admin -maxdepth 1 -type f -name '*.sql' -print | sort || true

echo
echo "== Init SQL =="
find b-application/src/main/resources/db/init -maxdepth 1 -type f -name '*.sql' -print | sort || true

echo
echo "== Hibernate schema mode =="
grep -R -n 'ddl-auto' b-application/src/main/resources/application*.yml || true
