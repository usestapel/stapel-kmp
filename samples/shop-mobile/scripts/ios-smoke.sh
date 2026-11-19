#!/usr/bin/env bash
# iOS launch-and-deep-link smoke of shop-mobile on a simulator.
#
#   samples/shop-mobile/scripts/ios-smoke.sh [<simulator udid>]
#   samples/shop-mobile/scripts/ios-smoke.sh --uitest [<simulator udid>]   # + the XCUITest suite
#
# Builds iosApp with xcodebuild for the simulator (the Xcode build phase runs
# :shared:embedAndSignAppleFrameworkForXcode), boots the simulator, installs and launches the app,
# waits for the app's log markers (stapel.app.ready, stapel.nav <id>), opens a deep link and waits
# for stapel.link <uri> opened, and saves a screenshot. Artifacts in samples/shop-mobile/build/ios-smoke.
# The app is signed ad hoc ("Sign to Run Locally", no team) so it has a Keychain on the simulator.
#
# --uitest then uninstalls the app (a fresh install: no Keychain item, no install marker) and runs the
# iosAppUITests target with `xcodebuild test`: fresh-install sign-in without a notice, the mock-code
# sign-in against the stand, a system deep link through the "Open in …?" prompt, the edge-swipe back,
# the Keychain vault across relaunches and its wipe on sign-out. It needs a local auth stand at
# SHOP_STAND_URL (default http://127.0.0.1:18741/; bricks/auth/e2e/stand/stand.sh up, K7_STAND_PORT).
# Needs DEVELOPER_DIR (default /Applications/Xcode.app/Contents/Developer).
set -euo pipefail
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HERE/build/ios-smoke"
DERIVED="$OUT/derived"
BUNDLE_ID="io.github.usestapel.sample.shop"
LINK="${SHOP_SMOKE_LINK:-shopmobile://app/orders/42}"
mkdir -p "$OUT"
UITEST=0
if [[ "${1:-}" == "--uitest" ]]; then UITEST=1; shift; fi
STAND_URL="${SHOP_STAND_URL:-http://127.0.0.1:18741/}"
SIGN=(CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM=)

UDID="${1:-}"
if [[ -z "$UDID" ]]; then
    UDID="$(xcrun simctl list devices available -j | python3 -c '
import json,sys
d=json.load(sys.stdin)["devices"]
for rt,devs in sorted(d.items(), reverse=True):
    if "iOS" not in rt: continue
    for x in devs:
        if x["name"].startswith("iPhone"): print(x["udid"]); sys.exit()')"
fi
echo "[ios-smoke] simulator $UDID"

echo "[ios-smoke] xcodebuild"
xcodebuild -project "$HERE/iosApp/iosApp.xcodeproj" -scheme iosApp -configuration Debug \
    -sdk iphonesimulator -destination "id=$UDID" -derivedDataPath "$DERIVED" \
    "${SIGN[@]}" build >"$OUT/xcodebuild.log" 2>&1 || { tail -40 "$OUT/xcodebuild.log"; exit 1; }
APP="$(find "$DERIVED/Build/Products" -maxdepth 2 -name 'ShopMobile.app' | head -1)"
[[ -d "$APP" ]] || { echo "[ios-smoke] no ShopMobile.app built"; exit 1; }

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b >/dev/null
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$UDID" "$APP"

LOG="$OUT/app.log"
: >"$LOG"
xcrun simctl spawn "$UDID" log stream --level info --style compact \
    --predicate 'process == "ShopMobile" AND eventMessage CONTAINS "stapel."' >"$LOG" 2>&1 &
STREAM=$!
trap 'kill $STREAM 2>/dev/null || true' EXIT
sleep 2

wait_for() { # pattern seconds
    for _ in $(seq 1 "$2"); do grep -q "$1" "$LOG" && return 0; sleep 1; done
    echo "[ios-smoke] '$1' did not appear within $2 s"; tail -20 "$LOG"; return 1
}

echo "[ios-smoke] launch"
xcrun simctl launch "$UDID" "$BUNDLE_ID" >"$OUT/launch.txt"
wait_for 'stapel.app.ready' 60
wait_for 'stapel.nav ' 30
xcrun simctl io "$UDID" screenshot "$OUT/launched.png" >/dev/null

echo "[ios-smoke] system link $LINK (expect the system's open-in-app prompt)"
xcrun simctl openurl "$UDID" "$LINK"
sleep 3
xcrun simctl io "$UDID" screenshot "$OUT/os-link-prompt.png" >/dev/null
if grep -q "stapel.link $LINK" "$LOG"; then echo "[ios-smoke] the system delivered the link without a prompt"; fi

echo "[ios-smoke] relaunch with the link as a launch argument (the app's URL entry point)"
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
: >"$LOG.prev"; cp "$LOG" "$LOG.prev"
xcrun simctl launch "$UDID" "$BUNDLE_ID" -stapelOpenUrl "$LINK" >>"$OUT/launch.txt"
wait_for "stapel.link $LINK opened" 60
sleep 2
xcrun simctl io "$UDID" screenshot "$OUT/deep-link.png" >/dev/null
grep -E 'stapel\.(app|nav|link)' "$LOG" | sed 's/^/[ios-smoke] log: /'
if grep -q 'stapel.session: vault' "$LOG"; then grep 'stapel.session: vault' "$LOG" | sed 's/^/[ios-smoke] /'; echo "[ios-smoke] the token vault failed"; exit 1; fi
echo "[ios-smoke] OK — artifacts in $OUT"

if [[ $UITEST == 1 ]]; then
    echo "[ios-smoke] XCUITest against the stand at $STAND_URL (fresh install)"
    xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
    xcrun simctl uninstall "$UDID" "$BUNDLE_ID"
    rm -rf "$OUT/uitest.xcresult"
    TEST_RUNNER_STAPEL_STAND_URL="$STAND_URL" xcodebuild -project "$HERE/iosApp/iosApp.xcodeproj" -scheme iosApp \
        -configuration Debug -sdk iphonesimulator -destination "id=$UDID" -derivedDataPath "$DERIVED" \
        -resultBundlePath "$OUT/uitest.xcresult" "${SIGN[@]}" test >"$OUT/uitest.log" 2>&1 \
        || { grep -E "error|failed|Test Case" "$OUT/uitest.log" | tail -40; exit 1; }
    grep -E "Test Case .*(passed|failed)" "$OUT/uitest.log" | sed 's/^/[ios-smoke] /'
    echo "[ios-smoke] XCUITest OK — result bundle $OUT/uitest.xcresult"
fi
