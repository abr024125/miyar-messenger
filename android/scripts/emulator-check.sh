#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
gradle --no-daemon --console=plain :app:connectedDebugAndroidTest
mkdir -p build/verification
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear tech.deanju.messenger
adb shell settings put system font_scale 1.0
for appearance in light dark; do
  if [[ "$appearance" == light ]]; then adb shell cmd uimode night no; else adb shell cmd uimode night yes; fi
  adb shell am force-stop tech.deanju.messenger
  adb shell am start -W -n tech.deanju.messenger/.MainActivity
  sleep 3
  adb shell pidof tech.deanju.messenger
  adb exec-out screencap -p > "build/verification/Miyar-Android-login-$appearance.png"
done
adb logcat -d -s AndroidRuntime:E > build/verification/runtime.log
if grep -q 'FATAL EXCEPTION' build/verification/runtime.log; then
  cat build/verification/runtime.log
  exit 1
fi
