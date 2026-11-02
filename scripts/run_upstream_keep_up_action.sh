#!/usr/bin/env bash
set -eu

CHECKED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
PINS="facebook-plus-manager/upstream-pins.json"
ISSUE_TITLE="Upstream keep-up"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ISSUE_NUMBER="$(gh issue list --state all --search "in:title \"$ISSUE_TITLE\"" --json number,title --jq ".[] | select(.title==\"$ISSUE_TITLE\") | .number" | head -n 1 || true)"
PREVIOUS_BODY=""
if [ -n "${ISSUE_NUMBER:-}" ]; then
  PREVIOUS_BODY="$(gh issue view "$ISSUE_NUMBER" --json body --jq .body)"
fi

fetch_ok=1
if ! HUSH_JSON="$(gh api repos/SysAdminDoc/HushFacebook/releases/latest)" \
  || ! PATCHER_JSON="$(gh api repos/MorpheApp/morphe-patcher/releases/latest)" \
  || ! MANAGER_JSON="$(gh api repos/MorpheApp/morphe-manager/releases/latest)"; then
  fetch_ok=0
fi

if [ "$fetch_ok" -eq 0 ]; then
  PAYLOAD="$(PREVIOUS_BODY="$PREVIOUS_BODY" CHECKED_AT="$CHECKED_AT" PINS="$PINS" python3 - <<'PY'
import json, os
print(json.dumps({
    "pins_path": os.environ["PINS"],
    "checked_at": os.environ["CHECKED_AT"],
    "error": "github fetch failed",
    "previous_body": os.environ.get("PREVIOUS_BODY", ""),
}))
PY
)"
else
  PAYLOAD="$(HUSH_JSON="$HUSH_JSON" PATCHER_JSON="$PATCHER_JSON" MANAGER_JSON="$MANAGER_JSON" PINS="$PINS" CHECKED_AT="$CHECKED_AT" python3 - <<'PY'
import json, os, sys
sys.path.insert(0, "scripts")
from upstream_keep_up import parse_hush_notes, is_stable_tag

def load(name):
    return json.loads(os.environ[name])

hush = load("HUSH_JSON")
patcher = load("PATCHER_JSON")
manager = load("MANAGER_JSON")
for rel, label in ((hush, "HushFacebook"), (patcher, "morphe-patcher"), (manager, "Morphe Manager")):
    tag = rel.get("tag_name") or ""
    if rel.get("draft") or rel.get("prerelease") or not is_stable_tag(tag):
        raise SystemExit(f"unstable latest for {label}: {tag}")
notes = parse_hush_notes(hush.get("body") or "")
print(json.dumps({
    "pins_path": os.environ["PINS"],
    "checked_at": os.environ["CHECKED_AT"],
    "latest": {
        "hushfacebook": {
            "tag": hush["tag_name"],
            "patcherVersion": notes["patcherVersion"],
            "facebookVersion": notes["facebookVersion"],
        },
        "morphePatcher": {"tag": patcher["tag_name"]},
        "morpheManager": {"tag": manager["tag_name"]},
    },
}))
PY
)"
fi

RESULT="$(python3 scripts/upstream_keep_up.py <<<"$PAYLOAD")"
BODY="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["body"])' "$RESULT")"
LABELS="$(python3 -c 'import json,sys; print("\n".join(json.loads(sys.argv[1])["labels"]))' "$RESULT")"

if [ -z "${ISSUE_NUMBER:-}" ]; then
  ISSUE_URL="$(gh issue create --title "$ISSUE_TITLE" --body "$BODY")"
  ISSUE_NUMBER="${ISSUE_URL##*/}"
else
  gh issue edit "$ISSUE_NUMBER" --body "$BODY"
fi

for NAME in patches-only patcher-bump manager-ui; do
  gh label create "$NAME" >/dev/null 2>&1 || true
done

CURRENT="$(gh issue view "$ISSUE_NUMBER" --json labels --jq '.labels[].name' || true)"
for NAME in patches-only patcher-bump manager-ui; do
  if echo "$CURRENT" | grep -qx "$NAME"; then
    if ! echo "$LABELS" | grep -qx "$NAME"; then
      gh issue edit "$ISSUE_NUMBER" --remove-label "$NAME"
    fi
  fi
done

if [ -n "$LABELS" ]; then
  ADD_ARGS=()
  while IFS= read -r NAME; do
    [ -n "$NAME" ] || continue
    ADD_ARGS+=(--add-label "$NAME")
  done <<<"$LABELS"
  if [ "${#ADD_ARGS[@]}" -gt 0 ]; then
    gh issue edit "$ISSUE_NUMBER" "${ADD_ARGS[@]}"
  fi
fi
