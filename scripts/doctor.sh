#!/bin/sh
# Checks the local toolchain for stapel-kmp. Exits non-zero on any miss and prints the fix.
# Usage: source scripts/env.sh && scripts/doctor.sh
set -u
cd "$(dirname "$0")/.."
fail=0
ok()   { printf '  ok    %s\n' "$1"; }
warn() { printf '  warn  %s\n' "$1"; }
bad()  { printf '  FAIL  %s\n        fix: %s\n' "$1" "$2"; fail=1; }

echo "stapel-kmp doctor"

# JDK 21 (Gradle runs on 21; bytecode targets 17).
if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
    ok "JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
else
    bad "JDK 21 not found via JAVA_HOME" "brew install openjdk@21 && source scripts/env.sh"
fi

# Android SDK: compileSdk 37 platform + build-tools.
if [ -d "${ANDROID_HOME:-/nonexistent}/platforms" ]; then
    if ls "$ANDROID_HOME/platforms" | grep -q '^android-37'; then
        ok "Android platform: $(ls "$ANDROID_HOME/platforms" | grep '^android-37' | head -1)"
    else
        bad "Android platform 37 missing in $ANDROID_HOME/platforms" "install 'platforms;android-37.0' with the SDK manager"
    fi
    if [ -n "$(ls "$ANDROID_HOME/build-tools" 2>/dev/null)" ]; then
        ok "Android build-tools: $(ls "$ANDROID_HOME/build-tools" | tr '\n' ' ')"
    else
        bad "Android build-tools missing" "install build-tools with the SDK manager"
    fi
else
    bad "ANDROID_HOME (${ANDROID_HOME:-unset}) has no platforms/" "export ANDROID_HOME=\$HOME/Library/Android/sdk (or source scripts/env.sh)"
fi

# Xcode (macOS only): iOS klibs and simulator tests.
if [ "$(uname -s)" = "Darwin" ]; then
    if xc="$(xcodebuild -version 2>/dev/null | head -1)"; then
        ok "Xcode: $xc (DEVELOPER_DIR=${DEVELOPER_DIR:-<xcode-select default>})"
        major="$(echo "$xc" | sed -n 's/^Xcode \([0-9]*\).*/\1/p')"
        # Kotlin 2.4.21 is tested with Xcode 26.4 (compatibility guide). Newer may work; measured in docs/toolchain.md.
        if [ -n "$major" ] && [ "$major" -gt 26 ]; then
            warn "Xcode $major is above Kotlin 2.4.21's tested 26.4; see docs/toolchain.md for the measured result"
        fi
        if xcrun simctl list runtimes 2>/dev/null | grep -q 'iOS'; then
            ok "iOS simulator runtime: $(xcrun simctl list runtimes 2>/dev/null | grep iOS | tail -1 | sed 's/ *(.*//')"
        else
            bad "no iOS simulator runtime" "install one from Xcode > Settings > Components"
        fi
    else
        bad "xcodebuild not usable" "export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer (no sudo needed)"
    fi
else
    warn "not macOS: iOS targets are compiled (klibs) but simulator tests and Dokka for iOS need a Mac"
fi

# Hooks.
if [ "$(git config core.hooksPath 2>/dev/null)" = ".githooks" ]; then
    ok "git hooks: .githooks active"
else
    bad "git hooks not active" "scripts/setup-hooks.sh"
fi
if command -v stapel-exposure-lint >/dev/null 2>&1; then
    ok "stapel-exposure-lint on PATH (pre-push exposure stage active)"
else
    warn "stapel-exposure-lint not on PATH: the pre-push exposure stage is skipped on this machine"
fi
if command -v stapel-hooks >/dev/null 2>&1; then
    if stapel-hooks doctor >/dev/null 2>&1; then ok "stapel-hooks doctor"; else warn "stapel-hooks doctor reports problems (run it for details)"; fi
fi

exit $fail
