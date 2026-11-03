#!/usr/bin/env bash
# Builds the app, installs it on the connected device and saves a screenshot
# of the main screen to docs/.
#
#   ./scripts/screenshot.sh          light theme
#   ./scripts/screenshot.sh dark     dark theme
set -euo pipefail

cd "$(dirname "$0")/.."

adb=adb
if ! command -v adb >/dev/null; then
    sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties | sed 's/\\:/:/; s/\\\\/\//g')}"
    adb="$sdk/platform-tools/adb"
fi

pkg=eu.janxyxy.unreel
theme="${1:-light}"
out=docs/screenshot.png
[ "$theme" = dark ] && out=docs/screenshot-dark.png

./gradlew -q installDebug

"$adb" shell input keyevent KEYCODE_WAKEUP
if "$adb" shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "Phone is locked, unlock it and run again" >&2
    exit 1
fi

# switch the theme, then put the phone's own setting back when done
was=$("$adb" shell cmd uimode night | tr -d '\r' | awk '{print $NF}')
trap '"$adb" shell cmd uimode night "$was" >/dev/null' EXIT
"$adb" shell cmd uimode night "$([ "$theme" = dark ] && echo yes || echo no)" >/dev/null
sleep 2 # some launchers restart on theme change and jump to the front

"$adb" shell am force-stop "$pkg"
"$adb" shell am start -W -n "$pkg/.MainActivity" >/dev/null
sleep 1

if ! "$adb" shell dumpsys activity activities | grep -q "ResumedActivity.*$pkg"; then
    echo "Unreel isn't on screen, not taking the screenshot" >&2
    exit 1
fi
if "$adb" shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "Phone locked itself, unlock it and run again" >&2
    exit 1
fi

mkdir -p docs
"$adb" exec-out screencap -p > "$out"
echo "saved $out"
