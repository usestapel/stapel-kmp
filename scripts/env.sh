# shellcheck shell=sh
# Toolchain for this shell only: `source scripts/env.sh`. Changes nothing global
# (no xcode-select, no java_home registration). Override any variable by
# exporting it before sourcing.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
    if command -v brew >/dev/null 2>&1 && [ -d "$(brew --prefix openjdk@21 2>/dev/null)/libexec/openjdk.jdk" ]; then
        JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
    elif [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    fi
fi
export JAVA_HOME
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ "$(uname -s)" = "Darwin" ]; then
    export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
fi
