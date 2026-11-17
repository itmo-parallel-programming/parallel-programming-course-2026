#!/usr/bin/env bash
# Полный прогон с конфигурацией из src/main/java/org/labs/Main.java.
# Каждый коллектор измеряется в отдельной JVM; CSV и график сохраняет Main.
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./gradlew -q runAll "$@"
