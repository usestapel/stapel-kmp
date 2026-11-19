#!/bin/sh
# Activates the repo's own hooks (.githooks/pre-push). Run once per clone.
set -e
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "core.hooksPath = $(git config core.hooksPath)"
