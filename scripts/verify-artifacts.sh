#!/bin/sh
# Asserts the staging repository (build/staging-repo, from
# `./gradlew publishAllPublicationsToStagingRepository`) holds what Maven Central
# and a consumer need. Every POM carries the fields Central requires. Per artifact kind:
#   KMP library      .module, .pom, sources + javadoc jars, the JVM jar, the Android AAR and
#                    klibs for iosArm64 and iosSimulatorArm64
#   stapel-bom       .pom only
#   stapel-catalog   a version catalog: .toml, .module, .pom (no code)
#   *-gradle-plugin  a JVM Gradle plugin: .jar, .module, .pom, sources + javadoc jars, and its
#                    plugin-marker POM (<group>.<id>:<group>.<id>.gradle.plugin, id = name
#                    without "stapel-" and "-gradle-plugin")
#   plugin marker    a group directory holding <id>.gradle.plugin: its POM only
# Usage: scripts/verify-artifacts.sh [repo-dir]
# Inversions: build-logic VerifyArtifactsScriptTest.
set -eu
cd "$(dirname "$0")/.."
repo="${1:-build/staging-repo}"
group="$(sed -n 's/^stapel.group=//p' gradle.properties)"
version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)"
gdir="$repo/$(echo "$group" | tr . /)"
[ -d "$gdir" ] || { echo "verify-artifacts: no $gdir (run the staging publish first)"; exit 1; }

fail=0
need() { [ -e "$1" ] || { echo "  MISSING $1"; fail=1; }; }
pom_fields() { # pom-path label
    need "$1"
    [ -f "$1" ] || return 0
    for field in '<name>' '<description>' '<url>' '<licenses>' '<developers>' '<scm>'; do
        grep -q "$field" "$1" || { echo "  POM $2 lacks $field"; fail=1; }
    done
}

for mdir in "$gdir"/*/; do
    a="$(basename "$mdir")"
    case "$a" in
        *-jvm|*-android|*-iosarm64|*-iossimulatorarm64) continue ;;
    esac
    # A plugin id's marker group (<group>.<id>): <group>.<id>.gradle.plugin/<version>/*.pom.
    if ls -d "$mdir"*.gradle.plugin >/dev/null 2>&1; then
        for m in "$mdir"*.gradle.plugin; do
            echo "$(basename "$m") $version (plugin marker)"
            pom_fields "$m/$version/$(basename "$m")-$version.pom" "$(basename "$m")"
        done
        continue
    fi
    v="$mdir$version"
    [ -d "$v" ] || { echo "  MISSING $v"; fail=1; continue; }
    echo "$a $version"
    pom_fields "$v/$a-$version.pom" "$a"
    case "$a" in
        stapel-bom)
            ;;
        stapel-catalog)
            need "$v/$a-$version.toml"
            need "$v/$a-$version.module"
            ;;
        *-gradle-plugin)
            need "$v/$a-$version.module"
            need "$v/$a-$version.jar"
            need "$v/$a-$version-sources.jar"
            need "$v/$a-$version-javadoc.jar"
            id="${a#stapel-}"
            id="${id%-gradle-plugin}"
            marker="$group.$id.gradle.plugin"
            need "$gdir/$id/$marker/$version/$marker-$version.pom"
            ;;
        *)
            need "$v/$a-$version.module"
            need "$v/$a-$version-sources.jar"
            need "$v/$a-$version-javadoc.jar"
            need "$gdir/$a-jvm/$version/$a-jvm-$version.jar"
            need "$gdir/$a-android/$version/$a-android-$version.aar"
            need "$gdir/$a-iosarm64/$version/$a-iosarm64-$version.klib"
            need "$gdir/$a-iossimulatorarm64/$version/$a-iossimulatorarm64-$version.klib"
            ;;
    esac
done

if [ "$fail" -ne 0 ]; then
    echo "verify-artifacts: FAILED"
    exit 1
fi
echo "verify-artifacts: ok"
