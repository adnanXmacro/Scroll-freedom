#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <tag>" >&2
  exit 1
fi

RAW="$1"
if [[ "$RAW" == v* ]]; then
  TAG="$RAW"
  VERSION="${RAW#v}"
else
  TAG="v$RAW"
  VERSION="$RAW"
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOML="$ROOT/gradle/libs.versions.toml"
PINS="$ROOT/upstream-pins.json"
COMPOSITE="$(cd "$ROOT/.." && pwd)/morphe-patcher"

if [[ ! -f "$TOML" ]]; then
  echo "missing $TOML" >&2
  exit 1
fi
if ! grep -q '^morphe-patcher = "' "$TOML"; then
  echo "missing morphe-patcher version in $TOML" >&2
  exit 1
fi

TMP="$(mktemp)"
sed "s/^morphe-patcher = \".*\"/morphe-patcher = \"$VERSION\"/" "$TOML" > "$TMP"
mv "$TMP" "$TOML"

if [[ -d "$COMPOSITE/.git" ]]; then
  git -C "$COMPOSITE" fetch --tags --depth 1 origin "refs/tags/$TAG:refs/tags/$TAG"
  git -C "$COMPOSITE" checkout "$TAG"
  COMPOSITE_VERSION="$(sed -n 's/^version *= *//p' "$COMPOSITE/gradle.properties" | tr -d '[:space:]')"
  if [[ "$COMPOSITE_VERSION" != "$VERSION" ]]; then
    echo "composite version $COMPOSITE_VERSION != $VERSION" >&2
    exit 1
  fi
else
  echo "no local morphe-patcher checkout; Maven will be used"
fi

python3 - "$PINS" "$TAG" <<'PY'
import json, sys
path, tag = sys.argv[1], sys.argv[2]
data = json.loads(open(path).read())
data.setdefault("morphePatcher", {})
data["morphePatcher"]["tag"] = tag
open(path, "w").write(json.dumps(data, indent=2) + "\n")
PY

TOML_VERSION="$(sed -n 's/^morphe-patcher = "\(.*\)"/\1/p' "$TOML" | head -1)"
PIN_TAG="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["morphePatcher"]["tag"])' "$PINS")"
PIN_VERSION="${PIN_TAG#v}"

if [[ "$TOML_VERSION" != "$VERSION" || "$PIN_VERSION" != "$VERSION" ]]; then
  echo "pin mismatch: toml=$TOML_VERSION pin=$PIN_VERSION expected=$VERSION" >&2
  exit 1
fi

if [[ -d "$COMPOSITE/.git" ]]; then
  COMPOSITE_VERSION="$(sed -n 's/^version *= *//p' "$COMPOSITE/gradle.properties" | tr -d '[:space:]')"
  if [[ "$COMPOSITE_VERSION" != "$VERSION" ]]; then
    echo "composite version $COMPOSITE_VERSION != $VERSION" >&2
    exit 1
  fi
fi

echo "synced morphe-patcher $TAG"
