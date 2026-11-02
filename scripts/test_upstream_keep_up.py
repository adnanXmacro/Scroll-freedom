#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from upstream_keep_up import (
    classify,
    is_stable_tag,
    normalize_version,
    parse_hush_notes,
    parse_pins,
    render_issue,
)

SHIPPED_PINS = {
    "hushfacebook": {
        "repo": "SysAdminDoc/HushFacebook",
        "tag": "v0.19.0",
        "patcherVersion": "2.0.0",
        "facebookVersion": "584.0.0.51.56",
    },
    "morphePatcher": {
        "repo": "MorpheApp/morphe-patcher",
        "tag": "v2.0.0",
    },
    "morpheManager": {
        "repo": "MorpheApp/morphe-manager",
        "tag": "v2.1.0",
    },
}


def latest(**overrides):
    data = {
        "hushfacebook": {
            "tag": "v0.19.0",
            "patcherVersion": "2.0.0",
            "facebookVersion": "584.0.0.51.56",
        },
        "morphePatcher": {"tag": "v2.0.0"},
        "morpheManager": {"tag": "v2.1.0"},
    }
    for key, value in overrides.items():
        data[key] = {**data[key], **value}
    return data


class NormalizeVersionTest(unittest.TestCase):
    def test_strips_leading_v(self):
        self.assertEqual("2.1.0", normalize_version("v2.1.0"))
        self.assertEqual("2.1.0", normalize_version("2.1.0"))


class StableTagTest(unittest.TestCase):
    def test_stable_and_dev_tags(self):
        self.assertTrue(is_stable_tag("v2.1.0"))
        self.assertFalse(is_stable_tag("v2.2.0-dev.1"))
        self.assertFalse(is_stable_tag("2.1.0-dev.7"))


class ClassifyTest(unittest.TestCase):
    def test_nothing_moved(self):
        self.assertEqual(set(), classify(SHIPPED_PINS, latest()))

    def test_hush_tag_moved_same_patcher_stamp(self):
        self.assertEqual(
            {"patches-only"},
            classify(
                SHIPPED_PINS,
                latest(hushfacebook={"tag": "v0.20.0", "patcherVersion": "2.0.0"}),
            ),
        )

    def test_hush_stamp_newer_is_patcher_bump_not_patches_only(self):
        self.assertEqual(
            {"patcher-bump"},
            classify(
                SHIPPED_PINS,
                latest(hushfacebook={"tag": "v0.20.0", "patcherVersion": "2.1.0"}),
            ),
        )

    def test_only_patcher_tag_newer(self):
        self.assertEqual(
            {"patcher-bump"},
            classify(SHIPPED_PINS, latest(morphePatcher={"tag": "v2.1.0"})),
        )

    def test_only_manager_tag_newer(self):
        self.assertEqual(
            {"manager-ui"},
            classify(SHIPPED_PINS, latest(morpheManager={"tag": "v2.3.0"})),
        )

    def test_hush_stamp_and_patcher_tag_newer_is_still_patcher_bump(self):
        self.assertEqual(
            {"patcher-bump"},
            classify(
                SHIPPED_PINS,
                latest(
                    hushfacebook={"tag": "v0.20.0", "patcherVersion": "2.1.0"},
                    morphePatcher={"tag": "v2.1.0"},
                ),
            ),
        )

    def test_unknown_hush_patcher_is_patcher_bump(self):
        self.assertEqual(
            {"patcher-bump"},
            classify(
                SHIPPED_PINS,
                latest(hushfacebook={"tag": "v0.20.0", "patcherVersion": "unknown"}),
            ),
        )

    def test_v_prefix_does_not_count_as_a_move(self):
        self.assertEqual(
            set(),
            classify(
                SHIPPED_PINS,
                latest(
                    hushfacebook={"tag": "0.19.0"},
                    morphePatcher={"tag": "2.0.0"},
                    morpheManager={"tag": "2.1.0"},
                ),
            ),
        )


class ParseHushNotesTest(unittest.TestCase):
    def test_v020_style_notes(self):
        body = (
            "This one needs Morphe Manager 2.3.0 or newer, so update Manager before you patch, "
            "and it moves to Facebook 585.0.0.47.54.\n"
            "The patches are built on Morphe patcher 2.1.0, the one Manager 2.3.0 ships with."
        )
        self.assertEqual(
            {"patcherVersion": "2.1.0", "facebookVersion": "585.0.0.47.54"},
            parse_hush_notes(body),
        )

    def test_v019_style_notes(self):
        body = (
            "You need Morphe Manager 2.2.0 or newer now. The patches are built on "
            "Morphe patcher 2.0.1, which uses less memory while patching.\n"
            "Patch Facebook 584.0.0.51.56."
        )
        self.assertEqual(
            {"patcherVersion": "2.0.1", "facebookVersion": "584.0.0.51.56"},
            parse_hush_notes(body),
        )

    def test_empty_body_is_unknown(self):
        self.assertEqual(
            {"patcherVersion": "unknown", "facebookVersion": "unknown"},
            parse_hush_notes(""),
        )


class ParsePinsTest(unittest.TestCase):
    def test_parses_required_keys(self):
        pins = parse_pins(json.dumps(SHIPPED_PINS))
        self.assertEqual("v0.19.0", pins["hushfacebook"]["tag"])
        self.assertEqual("2.0.0", pins["hushfacebook"]["patcherVersion"])
        self.assertEqual("584.0.0.51.56", pins["hushfacebook"]["facebookVersion"])
        self.assertEqual("SysAdminDoc/HushFacebook", pins["hushfacebook"]["repo"])
        self.assertEqual("v2.0.0", pins["morphePatcher"]["tag"])
        self.assertEqual("MorpheApp/morphe-patcher", pins["morphePatcher"]["repo"])
        self.assertEqual("v2.1.0", pins["morpheManager"]["tag"])
        self.assertEqual("MorpheApp/morphe-manager", pins["morpheManager"]["repo"])

    def test_missing_hush_patcher_version_raises(self):
        broken = json.loads(json.dumps(SHIPPED_PINS))
        del broken["hushfacebook"]["patcherVersion"]
        with self.assertRaises(ValueError):
            parse_pins(json.dumps(broken))

    def test_missing_patcher_tag_raises(self):
        broken = json.loads(json.dumps(SHIPPED_PINS))
        del broken["morphePatcher"]["tag"]
        with self.assertRaises(ValueError):
            parse_pins(json.dumps(broken))

    def test_real_pins_file_has_required_keys(self):
        path = Path(__file__).resolve().parent.parent / "facebook-plus-manager" / "upstream-pins.json"
        pins = parse_pins(path.read_text())
        self.assertEqual("v0.19.0", pins["hushfacebook"]["tag"])
        self.assertEqual("2.0.0", pins["hushfacebook"]["patcherVersion"])
        self.assertEqual("v2.0.0", pins["morphePatcher"]["tag"])
        self.assertEqual("v2.1.0", pins["morpheManager"]["tag"])


class RenderIssueTest(unittest.TestCase):
    def test_sync_command_uses_newer_patcher_when_only_engine_moved(self):
        body = render_issue(
            SHIPPED_PINS,
            latest(morphePatcher={"tag": "v2.1.0"}),
            {"patcher-bump"},
            "2026-11-02T00:00:00Z",
        )
        self.assertIn("./scripts/sync-patcher.sh v2.1.0", body)
        self.assertNotIn("sync-patcher.sh v2.0.0", body)


if __name__ == "__main__":
    unittest.main()
