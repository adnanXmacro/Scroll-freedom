#!/usr/bin/env python3
import json
import re
from pathlib import Path

REQUIRED_KEYS = {
    "hushfacebook": ("repo", "tag", "patcherVersion", "facebookVersion"),
    "morphePatcher": ("repo", "tag"),
    "morpheManager": ("repo", "tag"),
}

PATCHER_IN_NOTES = re.compile(r"Morphe patcher (\d+\.\d+\.\d+)", re.IGNORECASE)
FACEBOOK_IN_NOTES = re.compile(r"Facebook (\d+\.\d+\.\d+\.\d+\.\d+)", re.IGNORECASE)


def normalize_version(value: str) -> str:
    value = (value or "").strip()
    return value[1:] if value.lower().startswith("v") else value


def is_stable_tag(tag: str) -> bool:
    return "-dev" not in normalize_version(tag).lower()


def _parts(value: str):
    return tuple(int(part) if part.isdigit() else 0 for part in normalize_version(value).split("."))


def is_newer(candidate: str, current: str) -> bool:
    if (candidate or "").strip().lower() == "unknown":
        return True
    if not candidate or not current:
        return False
    return _parts(candidate) > _parts(current)


def parse_hush_notes(body: str) -> dict:
    patcher = PATCHER_IN_NOTES.search(body or "")
    facebook = FACEBOOK_IN_NOTES.search(body or "")
    return {
        "patcherVersion": patcher.group(1) if patcher else "unknown",
        "facebookVersion": facebook.group(1) if facebook else "unknown",
    }


def parse_pins(source) -> dict:
    text = source if isinstance(source, str) and source.lstrip().startswith("{") else Path(source).read_text()
    data = json.loads(text)
    for block, keys in REQUIRED_KEYS.items():
        if block not in data or not isinstance(data[block], dict):
            raise ValueError(f"missing {block}")
        for key in keys:
            if not data[block].get(key):
                raise ValueError(f"missing {block}.{key}")
    return data


def classify(pins: dict, latest: dict) -> set:
    hush_tag_moved = normalize_version(latest["hushfacebook"]["tag"]) != normalize_version(
        pins["hushfacebook"]["tag"]
    )
    hush_stamp_newer = is_newer(
        latest["hushfacebook"].get("patcherVersion"),
        pins["morphePatcher"]["tag"],
    )
    patcher_tag_newer = is_newer(latest["morphePatcher"]["tag"], pins["morphePatcher"]["tag"])
    manager_tag_newer = is_newer(latest["morpheManager"]["tag"], pins["morpheManager"]["tag"])

    classes = set()
    if hush_stamp_newer or patcher_tag_newer:
        classes.add("patcher-bump")
    elif hush_tag_moved:
        classes.add("patches-only")
    if manager_tag_newer:
        classes.add("manager-ui")
    return classes


def main(argv=None) -> int:
    import sys

    payload = json.load(sys.stdin)
    pins = parse_pins(payload["pins_path"]) if "pins_path" in payload else payload["pins"]
    checked_at = payload.get("checked_at", "")
    error = payload.get("error")
    previous_body = payload.get("previous_body", "")
    if error:
        prefix = f"Fetch failed at {checked_at}: {error}\n\n"
        body = prefix + (previous_body or "No previous body.")
        json.dump({"body": body, "labels": [], "classes": []}, sys.stdout)
        sys.stdout.write("\n")
        return 0
    latest = payload["latest"]
    classes = classify(pins, latest)
    body = render_issue(pins, latest, classes, checked_at)
    json.dump({"body": body, "labels": sorted(classes), "classes": sorted(classes)}, sys.stdout)
    sys.stdout.write("\n")
    return 0


def required_patcher_tag(latest: dict) -> str:
    hush = latest["hushfacebook"].get("patcherVersion") or "unknown"
    engine = latest["morphePatcher"]["tag"]
    if hush.lower() == "unknown" or is_newer(engine, hush):
        chosen = engine
    else:
        chosen = hush
    return chosen if str(chosen).startswith("v") else f"v{normalize_version(chosen)}"


def render_issue(pins: dict, latest: dict, classes: set, checked_at: str) -> str:
    class_list = ", ".join(sorted(classes)) if classes else "up to date"
    tag = required_patcher_tag(latest)
    bump_line = ""
    if "patcher-bump" in classes:
        bump_line = (
            f"\nRequired Patcher-Version: {normalize_version(tag)}\n"
            f"Shipped: {pins['morphePatcher']['tag']}\n"
            f"`./scripts/sync-patcher.sh {tag}`\n"
        )
    return (
        f"Last check: {checked_at}\n\n"
        f"HushFacebook pin {pins['hushfacebook']['tag']} vs latest {latest['hushfacebook']['tag']} "
        f"(patcher {latest['hushfacebook'].get('patcherVersion')}, facebook {latest['hushfacebook'].get('facebookVersion')})\n"
        f"morphe-patcher pin {pins['morphePatcher']['tag']} vs latest {latest['morphePatcher']['tag']}\n"
        f"Morphe Manager pin {pins['morpheManager']['tag']} vs latest {latest['morpheManager']['tag']}\n\n"
        f"Class: {class_list}\n"
        f"{bump_line}"
    )


if __name__ == "__main__":
    raise SystemExit(main())
