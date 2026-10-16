#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-simulator}"
build_number="${MIYAR_BUILD_NUMBER:-1}"
if [[ ! "$build_number" =~ ^[1-9][0-9]*$ ]]; then
  echo 'MIYAR_BUILD_NUMBER must be a positive integer.' >&2
  exit 2
fi
case "$mode" in simulator|device|archive|export) ;; *) echo 'Usage: bash scripts/build.sh simulator|device|archive|export' >&2; exit 2 ;; esac
if ! command -v xcodebuild >/dev/null 2>&1; then
  echo 'Xcode is required. Run this command on a Mac with Xcode selected.' >&2
  exit 2
fi
cd "$project_dir"
mkdir -p build
project=(-project MiyarMessenger.xcodeproj -scheme MiyarMessenger)
case "$mode" in
  simulator)
    xcodebuild "${project[@]}" -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO "CURRENT_PROJECT_VERSION=$build_number" build 2>&1 | tee build/simulator.log
    /usr/bin/ditto -c -k --keepParent build/DerivedData/Build/Products/Debug-iphonesimulator/MiyarMessenger.app build/MiyarMessenger-Simulator.zip
    echo 'Simulator app: build/MiyarMessenger-Simulator.zip (not an iPhone installation file).'
    ;;
  device)
    xcodebuild "${project[@]}" -configuration Release -destination 'generic/platform=iOS' -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO "CURRENT_PROJECT_VERSION=$build_number" build 2>&1 | tee build/device.log
    app='build/DerivedData/Build/Products/Release-iphoneos/MiyarMessenger.app'
    python3 - "$app" <<'PY'
import plistlib, sys
from pathlib import Path
app = Path(sys.argv[1])
info = plistlib.loads((app / 'Info.plist').read_bytes())
assert info.get('CFBundleSupportedPlatforms') == ['iPhoneOS'], 'A device build is required; simulator builds cannot be installed on iPhone.'
executable = info.get('CFBundleExecutable', '')
assert executable == 'MiyarMessenger' and (app / executable).is_file(), 'Compiled application executable is missing.'
assert (app / executable).stat().st_size > 0, 'Compiled application executable is empty.'
PY
    xcrun lipo "$app/MiyarMessenger" -verify_arch arm64
    payload_dir="$(mktemp -d "${TMPDIR:-/tmp/}miyar-device.XXXXXX")"
    trap 'rm -rf -- "$payload_dir"' EXIT
    mkdir "$payload_dir/Payload"
    /usr/bin/ditto "$app" "$payload_dir/Payload/MiyarMessenger.app"
    /usr/bin/ditto -c -k --norsrc "$payload_dir" build/MiyarMessenger-Unsigned.ipa
    echo 'Unsigned device IPA: build/MiyarMessenger-Unsigned.ipa. Sign it on your Windows PC before installation; see WINDOWS-INSTALL.md.'
    ;;
  archive)
    : "${MIYAR_TEAM_ID:?Set MIYAR_TEAM_ID to your Apple Developer team ID.}"
    archive_args=("${project[@]}" -configuration Release -destination 'generic/platform=iOS' -archivePath build/MiyarMessenger.xcarchive)
    if [[ "${MIYAR_ALLOW_PROVISIONING:-0}" == '1' ]]; then archive_args+=(-allowProvisioningUpdates); fi
    xcodebuild "${archive_args[@]}" "DEVELOPMENT_TEAM=$MIYAR_TEAM_ID" "CURRENT_PROJECT_VERSION=$build_number" archive 2>&1 | tee build/archive.log
    echo 'Archive: build/MiyarMessenger.xcarchive. Open it in Xcode Organizer for TestFlight distribution.'
    ;;
  export)
    : "${MIYAR_TEAM_ID:?Set MIYAR_TEAM_ID to your Apple Developer team ID.}"
    if [[ ! -d build/MiyarMessenger.xcarchive ]]; then echo 'Run archive first.' >&2; exit 2; fi
    python3 - "$MIYAR_TEAM_ID" <<'PY'
import plistlib, sys
from pathlib import Path
options = plistlib.loads(Path('Distribution/ExportOptions.plist').read_bytes())
options['teamID'] = sys.argv[1]
Path('build/ExportOptions.plist').write_bytes(plistlib.dumps(options))
PY
    export_args=(-exportArchive -archivePath build/MiyarMessenger.xcarchive -exportPath build/Export -exportOptionsPlist build/ExportOptions.plist)
    if [[ "${MIYAR_ALLOW_PROVISIONING:-0}" == '1' ]]; then export_args+=(-allowProvisioningUpdates); fi
    xcodebuild "${export_args[@]}" 2>&1 | tee build/export.log
    echo 'Signed export: build/Export. No build was uploaded to App Store Connect by this script.'
    ;;
esac
