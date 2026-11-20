#!/usr/bin/env bash
# Checks the consent gate in the BUILT Android app (gate G-C1): the merged manifest of a debug build
# made with -Pshop.firebase=true keeps Firebase's own collection off until the hub's consent turns it on,
# collects no advertising id and does not ask for the AD_ID permission.
#
#   ./gradlew :androidApp:assembleDebug -Pshop.firebase=true && scripts/check-consent-manifest.sh
set -euo pipefail
cd "$(dirname "$0")/.."

manifest="$(find androidApp/build/intermediates -path '*debug*' -name AndroidManifest.xml -path '*merged_manifest*' 2>/dev/null | head -n 1)"
if [ -z "$manifest" ]; then
    echo "check-consent-manifest: no merged debug manifest under androidApp/build (build with -Pshop.firebase=true first)" >&2
    exit 1
fi

fail=0
expect_meta() {
    local name="$1" value="$2"
    if ! tr -d '\n' < "$manifest" | grep -Eq "<meta-data[^>]*android:name=\"$name\"[^>]*android:value=\"$value\""; then
        echo "check-consent-manifest: $manifest lacks <meta-data $name=$value>" >&2
        fail=1
    fi
}
expect_meta firebase_analytics_collection_enabled false
expect_meta google_analytics_adid_collection_enabled false
if grep -q 'com.google.android.gms.permission.AD_ID' "$manifest"; then
    echo "check-consent-manifest: $manifest asks for com.google.android.gms.permission.AD_ID" >&2
    fail=1
fi
[ "$fail" = 0 ] && echo "check-consent-manifest: ok ($manifest)"
exit "$fail"
