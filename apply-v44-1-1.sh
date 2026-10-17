#!/usr/bin/env bash
set -euo pipefail

rm -f b-web-controller/src/main/java/com/jiajunxie/controller/TestController.java
chmod +x ops/docker/backup-postgres.sh ops/docker/restore-postgres.sh

echo "V44.1.1 applied."
echo "Run: ./gradlew clean test --no-daemon"
