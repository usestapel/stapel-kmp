#!/usr/bin/env bash
# Vendors stapel-tools' platform-neutral composition data into gradle/composition/ and pins it.
#
#   tools/composition/vendor.sh <stapel-tools version>
#
# Installs stapel-tools==<version> from PyPI into a throwaway venv, copies
# stapel_tools/data/composition-registry.json and data/route-plans/*.json, and writes pin.json with
# the version, the tag's commit (`git ls-remote` of the public repository) and every file's sha256.
# checkNavParity and stapel-app's PinnedRoutePlanTest read only the pinned copy.
set -euo pipefail
VERSION="${1:?usage: $0 <stapel-tools version>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="$ROOT/gradle/composition"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
python3 -m venv "$TMP/venv"
"$TMP/venv/bin/pip" install -q "stapel-tools==$VERSION"
DATA="$("$TMP/venv/bin/python" -I -c 'import os, stapel_tools; print(os.path.join(os.path.dirname(stapel_tools.__file__), "data"))')"
COMMIT="$(git ls-remote https://github.com/usestapel/stapel-tools.git "refs/tags/v$VERSION^{}" | cut -f1)"
[[ -n "$COMMIT" ]] || COMMIT="$(git ls-remote https://github.com/usestapel/stapel-tools.git "refs/tags/v$VERSION" | cut -f1)"
rm -rf "$DEST" && mkdir -p "$DEST/route-plans"
cp "$DATA/composition-registry.json" "$DEST/"
cp "$DATA"/route-plans/*.json "$DEST/route-plans/"
python3 -I - "$DEST" "$VERSION" "$COMMIT" <<'PY'
import hashlib, json, os, sys
dest, version, commit = sys.argv[1:4]
files = {}
for rel in ["composition-registry.json"] + sorted("route-plans/" + f for f in os.listdir(os.path.join(dest, "route-plans"))):
    with open(os.path.join(dest, rel), "rb") as fh:
        files[rel] = {"source": "stapel_tools/data/" + rel, "sha256": hashlib.sha256(fh.read()).hexdigest()}
pin = {"package": "stapel-tools", "version": version, "tag": "v" + version, "commit": commit,
       "how": "copied from the installed stapel-tools==%s wheel (PyPI); refresh with tools/composition/vendor.sh <version>" % version,
       "files": files}
with open(os.path.join(dest, "pin.json"), "w") as fh:
    fh.write(json.dumps(pin, indent=2) + "\n")
PY
echo "vendored stapel-tools $VERSION ($COMMIT) into gradle/composition"
