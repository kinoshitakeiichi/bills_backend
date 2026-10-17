#!/usr/bin/env bash
set -euo pipefail

echo "== Gradle testRuntimeClasspath =="
./gradlew :b-application:dependencies --configuration testRuntimeClasspath \
  | grep -A3 -B3 'b-service-core' || true

echo
echo "== AccountServiceImpl class output =="
find b-service-core/build/classes/java/main \
  -name 'AccountServiceImpl.class' -print || true
