#!/bin/sh
# Activates the repo's own hooks (.githooks/pre-push). Run once per clone.
# The pre-push hook runs stapel-exposure-lint from stapel-tools >= 0.82.0; this fails until it is on PATH.
set -e
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "core.hooksPath = $(git config core.hooksPath)"
if ! command -v stapel-exposure-lint >/dev/null 2>&1 || ! stapel-exposure-lint --help 2>/dev/null | grep -q -- '--strict'; then
    echo "setup-hooks: stapel-exposure-lint from stapel-tools >= 0.82.0 is not on PATH (the pre-push hook needs it)."
    echo "  uv tool install 'stapel-tools>=0.82.0'     # an older one: uv tool upgrade stapel-tools"
    exit 1
fi
echo "stapel-exposure-lint: $(command -v stapel-exposure-lint)"
