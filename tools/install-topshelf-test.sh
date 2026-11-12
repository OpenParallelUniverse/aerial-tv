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
snapshot() {
  "${TASK_ADB[@]}" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  for key in enabled_accessibility_services screensaver_components screensaver_enabled dreams_enabled; do
    printf '%s=' "$key"; "${TASK_ADB[@]}" shell settings get secure "$key"
  done
  for key in boot_app_componentname boot_app_mode; do
    printf '%s=' "$key"; "${TASK_ADB[@]}" shell settings get system "$key"
  done
  "${TASK_ADB[@]}" shell getprop persist.vt.use_home_pkg
  "${TASK_ADB[@]}" shell getprop persist.sys.launcher.component
  "${TASK_ADB[@]}" shell 'dumpsys package app.orchard.tv | grep -E "versionCode=|versionName=" || true'
}
TASK_BEFORE=$(snapshot | tr -d '\r')
if [[ "$TASK_BEFORE" == *"$TASK_PKG"* ]]; then
  echo 'Previous test package is configured as HOME, a service or a startup target; stop before updating it.' >&2
  exit 1
fi
TASK_SDK=$("${TASK_ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
# Verify the artifact locally before installation; apkanalyzer is part of Android SDK tools.
command -v apkanalyzer >/dev/null || { echo 'apkanalyzer is required to verify the test package before installation.' >&2; exit 1; }
[[ $(apkanalyzer manifest application-id "$TASK_APK") == "$TASK_PKG" ]] || { echo 'Refusing a non-test APK.' >&2; exit 1; }
TASK_TMP=$(mktemp -d)
trap 'rm -rf "$TASK_TMP"' EXIT
apkanalyzer manifest print "$TASK_APK" > "$TASK_TMP/manifest.xml"
python3 - "$TASK_TMP/manifest.xml" <<'PYVERIFY'
import sys,xml.etree.ElementTree as E
r=E.parse(sys.argv[1]).getroot(); a='{http://schemas.android.com/apk/res/android}'
assert r.get('package')=='app.orchard.tv.topshelf'
assert not any('HOME' in x.get(a+'name','') for x in r.iter('category'))
for x in list(r.iter('service'))+list(r.iter('receiver')):
 if x.get(a+'name','').split('.')[-1] in ('BootReceiver','ConfigReceiver','HomeGuardService','RevivalListener','RevivalSyncService','RevivalAccountService','AerialDream'):
  assert x.get(a+'enabled')=='false',x.attrib
blocked={'WRITE_SECURE_SETTINGS','WRITE_SETTINGS','SYSTEM_ALERT_WINDOW','RECEIVE_BOOT_COMPLETED','PACKAGE_USAGE_STATS'}
assert not {x.get(a+'name','').split('.')[-1] for x in r.iter('uses-permission')} & blocked
PYVERIFY
if (( TASK_SDK >= 28 )); then
  TASK_API=28; if (( TASK_SDK >= 31 )); then TASK_API=31; fi
  cp "$TASK_APK" "$TASK_TMP/test.apk"
  unzip -p "$TASK_APK" "assets/aerial-dm/$TASK_API.dm" > "$TASK_TMP/test.dm"
  "${TASK_ADB[@]}" install-multiple -r "$TASK_TMP/test.apk" "$TASK_TMP/test.dm"
else
  "${TASK_ADB[@]}" install -r "$TASK_APK"
fi
"${TASK_ADB[@]}" shell pm grant "$TASK_PKG" android.permission.READ_TV_LISTINGS 2>/dev/null || true
# No default-home assignment, secure-settings grant, service enablement or reboot.
TASK_AFTER=$(snapshot | tr -d '\r')
[[ "$TASK_BEFORE" == "$TASK_AFTER" ]] || { echo 'Existing HOME, services, startup, screensaver or production package state changed; stop and inspect.' >&2; exit 1; }
printf 'Existing installation state unchanged:\n%s\n' "$TASK_AFTER"
"${TASK_ADB[@]}" shell am start -n "$TASK_PKG/$TASK_MAIN"
# Home must still lead to the existing launcher; do not reconfigure either guard if it blocks the test app.
echo 'Manual test opened. Press HOME to return to your existing launcher.'
