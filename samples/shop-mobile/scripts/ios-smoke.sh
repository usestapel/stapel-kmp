#!/usr/bin/env bash
# iOS launch-and-deep-link smoke of shop-mobile on a simulator.
#
#   samples/shop-mobile/scripts/ios-smoke.sh [<simulator udid>]
#   samples/shop-mobile/scripts/ios-smoke.sh --uitest [<simulator udid>]   # + the XCUITest suite
#
# Builds iosApp with xcodebuild for the simulator (the Xcode build phase runs
# :shared:embedAndSignAppleFrameworkForXcode), boots the simulator, installs and launches the app,
# waits for the app's log markers (stapel.app.ready, stapel.nav <id>), opens a deep link and waits
# for stapel.link <uri> opened, and saves a screenshot. The markers are read from the app's own marker
# file in its data container (launch argument -stapelLogFile) and from the unified log; an app that
# exits or crashes while a marker is awaited fails at once, with diagnose.log (marker file, crash
# report, `log show`) and failure.png. Artifacts in samples/shop-mobile/build/ios-smoke.
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

# IOS_SMOKE_RUNTIME=newest (default) | oldest: which installed iOS runtime's iPhone to use when no UDID
# is given (the oldest-runtime CI job runs the app on the oldest runtime the runner has).
UDID="${1:-}"
if [[ -z "$UDID" ]]; then
    UDID="$(xcrun simctl list devices available -j | IOS_SMOKE_RUNTIME="${IOS_SMOKE_RUNTIME:-newest}" python3 -c '
import json,os,re,sys
d=json.load(sys.stdin)["devices"]
def ver(rt): return [int(x) for x in re.findall(r"\d+", rt.rsplit("iOS", 1)[-1])]
rts=sorted((rt for rt in d if ".iOS-" in rt), key=ver, reverse=os.environ["IOS_SMOKE_RUNTIME"] != "oldest")
for rt in rts:
    for x in d[rt]:
        if x["name"].startswith("iPhone"): print(x["udid"]); sys.exit()')"
fi
RUNTIME="$(xcrun simctl list devices -j | python3 -c '
import json,sys
for rt,devs in json.load(sys.stdin)["devices"].items():
    for x in devs:
        if x["udid"] == sys.argv[1]: print(rt.rsplit(".", 1)[-1], x["name"])' "$UDID")"
echo "[ios-smoke] simulator $UDID ($RUNTIME)"
echo "$RUNTIME" >"$OUT/runtime.txt"

echo "[ios-smoke] xcodebuild"
xcodebuild -project "$HERE/iosApp/iosApp.xcodeproj" -scheme iosApp -configuration Debug \
    -sdk iphonesimulator -destination "id=$UDID" -derivedDataPath "$DERIVED" \
    "${SIGN[@]}" build >"$OUT/xcodebuild.log" 2>&1 || { tail -40 "$OUT/xcodebuild.log"; exit 1; }
APP="$(find "$DERIVED/Build/Products" -maxdepth 2 -name 'ShopMobile.app' | head -1)"
[[ -d "$APP" ]] || { echo "[ios-smoke] no ShopMobile.app built"; exit 1; }
# The app is built for the project's deployment target (the iOS floor; checkIosFloor keeps it there).
FLOOR="$(sed -n 's/.*IPHONEOS_DEPLOYMENT_TARGET = \([0-9.]*\);.*/\1/p' "$HERE/iosApp/iosApp.xcodeproj/project.pbxproj" | sort -u)"
MINOS="$(otool -l "$APP/ShopMobile" | awk '/LC_BUILD_VERSION/{b=1} b&&/minos/{print $2; exit}')"
echo "[ios-smoke] deployment target $FLOOR, app binary minos $MINOS"
[[ "$MINOS" == "$FLOOR" ]] || { echo "[ios-smoke] the app binary's minos ($MINOS) is not the deployment target ($FLOOR)"; exit 1; }

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b >/dev/null
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$UDID" "$APP"

LOG="$OUT/app.log"
MARKERS_NAME="stapel-smoke.log"
DATA="$(xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" data)"
MARKERS="$DATA/Documents/$MARKERS_NAME"
rm -f "$MARKERS" "$OUT"/*.png "$OUT/markers.log" "$OUT/diagnose.log"
: >"$LOG"
# Two channels for the app's markers: the app's own marker file in its data container (-stapelLogFile;
# deterministic) and the unified log (informative; its filtering differs between simulator runtimes).
# Every wait reads both; the summary says which channel delivered.
xcrun simctl spawn "$UDID" log stream --level info --style compact \
    --predicate 'process == "ShopMobile" AND eventMessage CONTAINS "stapel."' >"$LOG" 2>&1 &
STREAM=$!
trap 'kill $STREAM 2>/dev/null || true' EXIT
sleep 2

app_pid() { sed -n 's/^.*: \([0-9][0-9]*\)$/\1/p' "$OUT/launch.txt" | tail -1; }
alive() { # the launched process still runs
    local pid; pid="$(app_pid)"
    [[ -n "$pid" ]] && xcrun simctl spawn "$UDID" launchctl list 2>/dev/null | grep -q "^$pid[[:space:]].*$BUNDLE_ID"
}
diagnose() { # what the app did, when a marker did not come (never fails itself)
    set +e +o pipefail
    local reports=~/Library/Logs/DiagnosticReports latest
    latest="$(ls -t "$reports" 2>/dev/null | grep -i shopmobile | head -1)"
    {
        echo "== launch: $(tr '\n' ' ' <"$OUT/launch.txt")"
        echo "== process alive: $(alive && echo yes || echo NO)"
        echo "== marker file $MARKERS:"; cat "$MARKERS" 2>/dev/null || echo "(absent)"
        echo "== latest crash report: ${latest:-(none)}"
        [[ -n "$latest" ]] && head -c 6000 "$reports/$latest" && echo
        echo "== log show (process ShopMobile, last 4 min, info+debug):"
        xcrun simctl spawn "$UDID" log show --last 4m --info --debug --style compact \
            --predicate 'process == "ShopMobile"' 2>&1 | tail -200
    } >"$OUT/diagnose.log" 2>&1
    xcrun simctl io "$UDID" screenshot "$OUT/failure.png" >/dev/null 2>&1
    sed -n '1,/^== log show/p' "$OUT/diagnose.log" | head -60 | cut -c1-300 | sed 's/^/[ios-smoke] diag: /'
    grep -E 'stapel|Exception|Terminating|fault|error' "$OUT/diagnose.log" | grep -v '^== ' | tail -40 | cut -c1-300 | sed 's/^/[ios-smoke] diag log: /'
    set -e -o pipefail
}
seen() { grep -qF "$1" "$MARKERS" 2>/dev/null || grep -qF "$1" "$LOG"; }
wait_for() { # literal seconds
    for _ in $(seq 1 "$2"); do
        if seen "$1"; then
            echo "[ios-smoke] '$1' (marker file: $(grep -qF "$1" "$MARKERS" 2>/dev/null && echo yes || echo no), unified log: $(grep -qF "$1" "$LOG" && echo yes || echo no))"
            return 0
        fi
        # A crash at start fails at once, not after the timeout.
        if ! alive; then sleep 1; seen "$1" && continue; echo "[ios-smoke] the app is not running (crashed or exited) while waiting for '$1'"; diagnose; return 1; fi
        sleep 1
    done
    echo "[ios-smoke] '$1' did not appear within $2 s"; diagnose; return 1
}

echo "[ios-smoke] launch"
xcrun simctl launch "$UDID" "$BUNDLE_ID" -stapelLogFile "$MARKERS_NAME" >"$OUT/launch.txt"
wait_for 'stapel.app.ready' 120
wait_for 'stapel.nav ' 30
xcrun simctl io "$UDID" screenshot "$OUT/launched.png" >/dev/null

echo "[ios-smoke] system link $LINK (expect the system's open-in-app prompt)"
xcrun simctl openurl "$UDID" "$LINK"
sleep 3
xcrun simctl io "$UDID" screenshot "$OUT/os-link-prompt.png" >/dev/null
if seen "stapel.link $LINK"; then echo "[ios-smoke] the system delivered the link without a prompt"; fi

echo "[ios-smoke] relaunch with the link as a launch argument (the app's URL entry point)"
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl launch "$UDID" "$BUNDLE_ID" -stapelLogFile "$MARKERS_NAME" -stapelOpenUrl "$LINK" >>"$OUT/launch.txt"
wait_for "stapel.link $LINK opened" 60
sleep 2
xcrun simctl io "$UDID" screenshot "$OUT/deep-link.png" >/dev/null
cp "$MARKERS" "$OUT/markers.log"
grep -E 'stapel\.(app|nav|link)' "$OUT/markers.log" | sed 's/^/[ios-smoke] marker: /'
if grep -h 'stapel.session: vault' "$OUT/markers.log" "$LOG"; then echo "[ios-smoke] the token vault failed"; exit 1; fi
echo "[ios-smoke] OK — artifacts in $OUT"

keep_uitest_markers() { # the app's log records during the suite (launch argument -stapelLogFile)
    local data
    data="$(xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" data 2>/dev/null)" || return 0
    cp "$data/Documents/uitest-markers.log" "$OUT/uitest-markers.log" 2>/dev/null || true
    grep -h 'stapel.link' "$OUT/uitest-markers.log" 2>/dev/null | sed 's/^/[ios-smoke] uitest marker: /' || true
}

if [[ $UITEST == 1 ]]; then
    echo "[ios-smoke] XCUITest against the stand at $STAND_URL (fresh install)"
    xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
    xcrun simctl uninstall "$UDID" "$BUNDLE_ID"
    rm -rf "$OUT/uitest.xcresult"
    TEST_RUNNER_STAPEL_STAND_URL="$STAND_URL" xcodebuild -project "$HERE/iosApp/iosApp.xcodeproj" -scheme iosApp \
        -configuration Debug -sdk iphonesimulator -destination "id=$UDID" -derivedDataPath "$DERIVED" \
        -resultBundlePath "$OUT/uitest.xcresult" "${SIGN[@]}" test >"$OUT/uitest.log" 2>&1 \
        || { keep_uitest_markers; grep -E "error|failed|Test Case" "$OUT/uitest.log" | tail -40; exit 1; }
    keep_uitest_markers
    grep -E "Test Case .*(passed|failed)" "$OUT/uitest.log" | sed 's/^/[ios-smoke] /'
    echo "[ios-smoke] XCUITest OK — result bundle $OUT/uitest.xcresult"
fi
