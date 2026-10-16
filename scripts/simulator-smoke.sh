#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
app='build/DerivedData/Build/Products/Debug-iphonesimulator/MiyarMessenger.app'
if [[ ! -d "$app" ]]; then echo 'Build the simulator app first.' >&2; exit 2; fi
device="$(xcrun simctl list devices available --json | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
available = [d for runtime, values in devices.items() if ".iOS-" in runtime for d in values if d.get("isAvailable") and d["name"].startswith("iPhone")]
if not available: sys.exit("No available iPhone simulator runtime")
available.sort(key=lambda d: (d["state"] != "Booted", d["name"]))
print(available[0]["udid"])
')"
if ! xcrun simctl list devices booted | /usr/bin/grep -q "$device"; then xcrun simctl boot "$device"; fi
trap 'xcrun simctl shutdown "$device" >/dev/null 2>&1 || true' EXIT
xcrun simctl bootstatus "$device" -b
xcrun simctl status_bar "$device" override --time '9:41' --batteryState charged --batteryLevel 100
xcrun simctl ui "$device" appearance light
xcrun simctl install "$device" "$app"
xcrun simctl launch --terminate-running-process "$device" tech.deanju.messenger
sleep 5
xcrun simctl io "$device" screenshot build/Miyar-login-light.png
xcrun simctl ui "$device" appearance dark
sleep 2
xcrun simctl io "$device" screenshot build/Miyar-login-dark.png
xcrun simctl terminate "$device" tech.deanju.messenger
echo 'PASS: app installed and launched on iPhone Simulator; light and dark login screenshots saved.'
