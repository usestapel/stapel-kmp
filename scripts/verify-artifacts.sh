#!/bin/sh
# Asserts the staging repository (build/staging-repo, from
# `./gradlew publishAllPublicationsToStagingRepository`) holds what Maven Central
# and a consumer need, per published module: .module + .pom with the POM fields
# Central requires, a sources jar, a javadoc jar, the JVM jar, the Android AAR and
# klibs for iosArm64 and iosSimulatorArm64.
# Usage: scripts/verify-artifacts.sh [repo-dir]
set -eu
cd "$(dirname "$0")/.."
repo="${1:-build/staging-repo}"
group="$(sed -n 's/^stapel.group=//p' gradle.properties)"
version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)"
gdir="$repo/$(echo "$group" | tr . /)"
[ -d "$gdir" ] || { echo "verify-artifacts: no $gdir (run the staging publish first)"; exit 1; }

fail=0
need() { [ -e "$1" ] || { echo "  MISSING $1"; fail=1; }; }

for mdir in "$gdir"/*/; do
    a="$(basename "$mdir")"
    case "$a" in
        *-jvm|*-android|*-iosarm64|*-iossimulatorarm64) continue ;;
    esac
    v="$mdir$version"
    [ -d "$v" ] || { echo "  MISSING $v"; fail=1; continue; }
    echo "$a $version"
    need "$v/$a-$version.pom"
    pom="$v/$a-$version.pom"
    if [ -f "$pom" ]; then
        for field in '<name>' '<description>' '<url>' '<licenses>' '<developers>' '<scm>'; do
            grep -q "$field" "$pom" || { echo "  POM $a lacks $field"; fail=1; }
        done
    fi
    if [ "$a" = "stapel-bom" ]; then
        continue
    fi
    need "$v/$a-$version.module"
    need "$v/$a-$version-sources.jar"
    need "$v/$a-$version-javadoc.jar"
    need "$gdir/$a-jvm/$version/$a-jvm-$version.jar"
    need "$gdir/$a-android/$version/$a-android-$version.aar"
    need "$gdir/$a-iosarm64/$version/$a-iosarm64-$version.klib"
    need "$gdir/$a-iossimulatorarm64/$version/$a-iossimulatorarm64-$version.klib"
done

if [ "$fail" -ne 0 ]; then
    echo "verify-artifacts: FAILED"
    exit 1
fi
echo "verify-artifacts: ok"
