#!/usr/bin/env bash
# Builds the test copy ("Spendly Dev"), installs it on the connected phone and has Android compile it straight away.
# Without the last step Android runs a fresh install interpreted until its overnight compile, which feels laggy.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK_DIR=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
ADB=${ADB:-$(command -v adb || echo "${SDK_DIR:-$HOME/Android/Sdk}/platform-tools/adb")}
./gradlew installDebug
"$ADB" shell cmd package compile -m speed -f com.kk.spendly.debug
echo "Spendly Dev installed and compiled."
