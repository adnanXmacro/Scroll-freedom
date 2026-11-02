# Facebook+ Manager upstream keep-up

Spec: `docs/superpowers/specs/2026-11-02-upstream-keep-up-design.md`

This pass does **not** bump the shipped patcher or release a new APK. It adds pins, classification, a notify-only GitHub Action, `sync-patcher.sh`, and the protocol/handoff files. A later chat that is told **implement update protocol** does the actual 2.1.0 patcher rebuild.

Work in `/workspace`. Classifier and watcher live at the Scroll-freedom root so Actions can run them without opening Android Studio. Kotlin packages, `applicationId`, branded shell, and the 2.1.2 Facebook+ dialog skip stay untouched.

TDD for the classifier: write the failing test, run it, then the minimum production change. Do not auto-compile or auto-release.

## Why

HushFacebook v0.20.0 needs patcher 2.1.0 / Manager 2.3.0 and Facebook 585. We still ship patcher 2.0.0. Patches auto-download; the engine does not. Skipping the dialog is not a keep-up strategy.

## Order

1. Classifier + pins parse (TDD, Python)
2. `upstream-pins.json` filled from what we ship today
3. `sync-patcher.sh` + Gradle pin check
4. `UPDATE-PROTOCOL.md` + `HANDOFF.md`
5. GitHub Action (notify only)
6. Run classifier tests + existing facebookplus Gradle tests

## 1. Classifier (TDD)

### Tests first

Create `scripts/test_upstream_keep_up.py`. Use the stdlib `unittest` so there is no pytest dependency.

Pins fixture (what we ship today):

```
hush tag v0.19.0, hush patcherVersion 2.0.0, facebook 584.0.0.51.56
morphePatcher tag v2.0.0
morpheManager tag v2.1.0
```

Cases (assert `classify(pins, latest)` returns a set of classes):

- Nothing moved -> empty set
- HushFacebook tag `v0.20.0` but `patcherVersion` still `2.0.0` -> `{patches-only}`
- HushFacebook tag `v0.20.0` and `patcherVersion` `2.1.0` -> `{patcher-bump}` (not `patches-only`)
- Only `morphe-patcher` tag `v2.1.0` -> `{patcher-bump}`
- Only Morphe Manager tag `v2.3.0` -> `{manager-ui}`
- HushFacebook stamp `2.1.0` and patcher tag `v2.1.0` together -> `{patcher-bump}`
- HushFacebook `patcherVersion` `unknown` (notes omitted) -> `{patcher-bump}`
- Strip `v` so `2.1.0` equals `v2.1.0`

Notes parser cases (raw HushFacebook body -> `{patcherVersion, facebookVersion}`):

- v0.20.0 style: "needs Morphe Manager 2.3.0" / "built on Morphe patcher 2.1.0" / "Facebook 585.0.0.47.54" -> patcher `2.1.0`, facebook `585.0.0.47.54`
- v0.19.0 style: "patcher 2.0.1" / Facebook 584.0.0.51.56 -> `2.0.1` / `584.0.0.51.56`
- Empty body -> `patcherVersion` `unknown`, facebook `unknown`

Pins parse:

- `parse_pins(json_text)` returns the three blocks
- Missing `hushfacebook.patcherVersion` or `morphePatcher.tag` raises

`is_stable_tag`: `v2.1.0` true, `v2.2.0-dev.1` false, `2.1.0-dev.7` false

Run (expect fail: module missing):

```
python3 scripts/test_upstream_keep_up.py
```

### Production

Create `scripts/upstream_keep_up.py`.

- `normalize_version(s)` strips a leading `v`
- `is_newer(a, b)` uses dotted numeric compare (good enough for 2.0.0 vs 2.1.0 vs 2.0.1). Treat `unknown` as newer than anything we ship.
- `classify(pins, latest)` as the table in the spec
- `parse_hush_notes(body)`
- `parse_pins(path or text)`
- `render_issue(pins, latest, classes, checked_at)` markdown for the standing issue: last check UTC, each pin vs latest, classes, and for `patcher-bump` the line `./scripts/sync-patcher.sh v2.1.0`

Do not call the network from this module. The Action passes JSON in.

Re-run until green:

```
python3 scripts/test_upstream_keep_up.py
```

## 2. Pins file

Create `facebook-plus-manager/upstream-pins.json` from what we **ship**, not what HushFacebook latest wants:

```json
{
  "hushfacebook": {
    "repo": "SysAdminDoc/HushFacebook",
    "tag": "v0.19.0",
    "patcherVersion": "2.0.0",
    "facebookVersion": "584.0.0.51.56"
  },
  "morphePatcher": {
    "repo": "MorpheApp/morphe-patcher",
    "tag": "v2.0.0"
  },
  "morpheManager": {
    "repo": "MorpheApp/morphe-manager",
    "tag": "v2.1.0"
  }
}
```

Add a test that loads this real file and asserts those keys exist. First watcher run must then classify current GitHub as `patcher-bump`.

## 3. Patcher swap script + Gradle pin check

### `facebook-plus-manager/scripts/sync-patcher.sh`

Usage: `./scripts/sync-patcher.sh v2.1.0` (also accepts `2.1.0`; store with `v`).

Steps, fail closed, no compile, no release:

1. Require exactly one argument.
2. `TAG` with leading `v`. `VERSION` without it.
3. In `gradle/libs.versions.toml` replace the `morphe-patcher = "…"` version line only. Do not rewrite the rest of the file. If the key is missing, exit 1.
4. If `../morphe-patcher` exists and is a git repo: `git fetch --tags --depth 1 origin tag "$TAG"` and `git checkout "$TAG"`. If that fails, exit 1. If `../morphe-patcher` does not exist, print that Maven will be used.
5. If the checkout exists, read its `gradle.properties` `version`. It must equal `$VERSION`.
6. Write `upstream-pins.json` `morphePatcher.tag` to `$TAG` last (python3 one-liner or the keep-up module). Do not clobber hushfacebook fields unless this script is given them; default is patcher tag only.
7. Re-read toml, pin, composite. Exit 1 on disagreement.

Make it executable.

Do **not** run it against 2.1.0 in this pass.

### Gradle

In `facebook-plus-manager/app/build.gradle.kts` (or a small `buildSrc` check), before `assembleDebug` / as part of `preBuild`:

- Read `../upstream-pins.json` `morphePatcher.tag` (strip `v`)
- Read `libs.versions.toml` `morphe-patcher`
- If they differ, `throw GradleException("patcher pin mismatch: toml=$toml pin=$pin")`
- If `file("../../morphe-patcher/gradle.properties")` exists (workspace composite), its `version` must match too

Keep the check cheap (file reads only).

## 4. Protocol and handoff

### `docs/superpowers/UPDATE-PROTOCOL.md`

This file **is** the command. Start with:

```
Trigger phrases (same meaning):
- implement update protocol
- run the update protocol
- swap the patcher
- keep up with upstream

After you have read this file, those phrases mean: do the numbered steps below in order. Do not add features. Do not skip stop rules.
```

Then numbered steps matching the spec:

1. Read `facebook-plus-manager/upstream-pins.json`.
2. Fetch latest *stable* releases (no draft, no prerelease, no `-dev`) for `SysAdminDoc/HushFacebook`, `MorpheApp/morphe-patcher`, `MorpheApp/morphe-manager` via `gh` or the GitHub API.
3. Parse HushFacebook notes with the same rules as `scripts/upstream_keep_up.py`. Classify. Print the class set.
4. If empty: stop, say up to date.
5. If only `patches-only`: stop. Phone already downloads the `.mpp`. Do not compile.
6. If only `manager-ui`: stop unless the human asked for Morphe UI/installer fixes.
7. If `patcher-bump`: continue.
8. Bump `facebook-plus-manager/app/gradle.properties` `version` to the next patch (2.1.2 -> 2.1.3 on the first real bump).
9. From `facebook-plus-manager/`: `./scripts/sync-patcher.sh v<required>` where required is the newer of HushFacebook's `Patcher-Version` and latest stable morphe-patcher tag.
10. Run:

```
cd /workspace/facebook-plus-manager
python3 /workspace/scripts/test_upstream_keep_up.py
./gradlew :app:testDebugUnitTest --offline --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusFlowTest' --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusBrandingTest' --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusPinTest' --tests 'app.morphe.manager.domain.manager.DownloadUrlResolverTest' --tests 'app.morphe.manager.domain.manager.ManagerUpdatePolicyTest' --tests 'app.morphe.manager.util.ManagerUpdateUrlsTest'
./gradlew :app:assembleDebug --offline
```

11. Verify the debug APK: package `app.eightbitsoftworks.facebookplus.manager.debug`, `versionName` matches gradle.properties, launcher label `Facebook+ Manager`, no Morph / Morph Debug, English `strings.xml` does not contain `HushFacebook`.
12. GitHub release `facebook-plus-manager-v<version>-debug` on `adnanXmacro/Scroll-freedom` with that APK. Do not upload Morphe's APK.
13. Only after the release asset exists: write `app-release.json` at repo root, `facebook-plus-manager/app-release.json`, and `facebook-plus-manager/app/app-release.json` to that asset URL and version. Commit those three files. Push `main`.
14. Update `upstream-pins.json` hushfacebook tag / patcherVersion / facebookVersion to what you just matched. Commit if not already in the JSON commit.

Stop rules, copied into this file:

- GitHub fetch fails: stop, do not guess tags.
- Pins, toml, composite disagree: stop, do not compile.
- Tests fail, Morph in launcher, HushFacebook in English copy: stop, do not release.
- Never ship Morphe's APK.
- Never push `app-release.json` until the new APK is on the GitHub release.
- No extra features in the same pass.

### `docs/superpowers/HANDOFF.md`

Short. Pointers only:

- This is Facebook+ Manager, a one-tap fork. Not Morphe. Package `app.eightbitsoftworks.facebookplus.manager`.
- Three clocks: HushFacebook `.mpp` (phone auto-updates), `morphe-patcher` (needs our APK), Morphe Manager UI (optional).
- Read `docs/superpowers/UPDATE-PROTOCOL.md` first. The phrase **implement update protocol** means that file.
- Pins: `facebook-plus-manager/upstream-pins.json`
- Specs: branded shell `docs/superpowers/specs/2026-10-28-facebook-plus-manager-branded-shell-design.md`, keep-up `docs/superpowers/specs/2026-11-02-upstream-keep-up-design.md`
- Never ship Morphe's APK. Never leak Morph in the launcher. Never name HushFacebook in user-facing English copy.

## 5. GitHub Action (notify only)

Create `.github/workflows/upstream-keep-up.yml` on the Scroll-freedom root (this repo), not inside the Morphe fork's own `.github`.

```
on:
  schedule:
    - cron: "0 */6 * * *"
  workflow_dispatch:
```

Permissions: `issues: write`, `contents: read`.

Job:

1. Checkout.
2. `python3` (runner default).
3. Fetch latest stable release JSON for the three repos (`gh api repos/.../releases` and pick the first non-draft non-prerelease whose tag has no `-dev`, or `/releases/latest` which already skips prereleases).
4. Parse HushFacebook `body` with `scripts/upstream_keep_up.py`.
5. Classify against `facebook-plus-manager/upstream-pins.json`.
6. Find issue titled exactly `Upstream keep-up` (`gh issue list --search`). If none, `gh issue create --title "Upstream keep-up"`.
7. `gh issue edit` body to `render_issue(...)`. If fetch failed, prepend `Fetch failed at <utc>: <error>` and keep the previous body below; do not change pins.
8. Set labels to the current class set only: `patches-only`, `patcher-bump`, `manager-ui`. Create labels if missing. Remove labels that no longer apply. If empty class set, no those three labels.
9. Do not build. Do not release. Do not push pins.

The Action must call the same `scripts/upstream_keep_up.py` functions (a small `__main__` that reads stdin JSON and prints the issue body + labels).

## 6. Verify this pass

```
python3 scripts/test_upstream_keep_up.py
cd /workspace/facebook-plus-manager && ./gradlew :app:testDebugUnitTest --offline --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusBrandingTest' --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusFlowTest'
```

Do **not** run `sync-patcher.sh v2.1.0`. Do **not** `assembleDebug` unless the pin check needs a compile smoke; prefer `./gradlew :app:preBuild --offline` if that runs the new mismatch task.

Optional: `gh workflow run` after push, or wait for cron.

## Done when

- `python3 scripts/test_upstream_keep_up.py` is green
- `facebook-plus-manager/upstream-pins.json` matches shipped 2.0.0 / v0.19.0
- `scripts/sync-patcher.sh` exists and is executable
- Gradle pin check is in the app build
- `docs/superpowers/UPDATE-PROTOCOL.md` and `docs/superpowers/HANDOFF.md` exist
- `.github/workflows/upstream-keep-up.yml` exists
- No new APK, no patcher bump in toml

A new chat given the repo and "read `docs/superpowers/UPDATE-PROTOCOL.md` first" then **implement update protocol** can bump to patcher 2.1.0, test, and ship without reading the rest of the tree.
