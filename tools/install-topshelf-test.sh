#!/usr/bin/env bash
# Install only the isolated test app. Never use the production installer for test APKs.
set -euo pipefail
TASK_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
TASK_APK=${1:-$TASK_DIR/aerial-topshelf-standard.apk}
TASK_PKG=app.orchard.tv.topshelf
TASK_MAIN=app.orchard.tv.MainActivity
TASK_SERIAL=${TV:-}
TASK_ADB=(adb)
if [[ -n "$TASK_SERIAL" ]]; then TASK_ADB+=(-s "$TASK_SERIAL"); fi
[[ -f "$TASK_APK" ]] || { echo "Missing test APK: $TASK_APK" >&2; exit 1; }
TASK_BEFORE=$("${TASK_ADB[@]}" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r')
TASK_SERVICES=$("${TASK_ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')
TASK_SDK=$("${TASK_ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
# Verify the artifact locally before installation; apkanalyzer is part of Android SDK tools.
command -v apkanalyzer >/dev/null || { echo 'apkanalyzer is required to verify the test package before installation.' >&2; exit 1; }
[[ $(apkanalyzer manifest application-id "$TASK_APK") == "$TASK_PKG" ]] || { echo 'Refusing a non-test APK.' >&2; exit 1; }
if (( TASK_SDK >= 28 )); then
  TASK_API=28; if (( TASK_SDK >= 31 )); then TASK_API=31; fi
  TASK_TMP=$(mktemp -d)
  trap 'rm -rf "$TASK_TMP"' EXIT
  cp "$TASK_APK" "$TASK_TMP/test.apk"
  unzip -p "$TASK_APK" "assets/aerial-dm/$TASK_API.dm" > "$TASK_TMP/test.dm"
  "${TASK_ADB[@]}" install-multiple -r "$TASK_TMP/test.apk" "$TASK_TMP/test.dm"
else
  "${TASK_ADB[@]}" install -r "$TASK_APK"
fi
"${TASK_ADB[@]}" shell pm grant "$TASK_PKG" android.permission.READ_TV_LISTINGS 2>/dev/null || true
# No default-home assignment, secure-settings grant, service enablement or reboot.
TASK_AFTER=$("${TASK_ADB[@]}" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r')
TASK_SERVICES_AFTER=$("${TASK_ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')
[[ "$TASK_BEFORE" == "$TASK_AFTER" && "$TASK_SERVICES" == "$TASK_SERVICES_AFTER" ]] || { echo 'Home or accessibility state changed; stop testing and inspect before proceeding.' >&2; exit 1; }
printf 'HOME before/after:\n%s\nAccessibility before/after:\n%s\n' "$TASK_AFTER" "$TASK_SERVICES_AFTER"
"${TASK_ADB[@]}" shell am start -n "$TASK_PKG/$TASK_MAIN"
# Home must still lead to the existing launcher; do not reconfigure either guard if it blocks the test app.
echo 'Manual test opened. Press HOME to return to your existing launcher.'
