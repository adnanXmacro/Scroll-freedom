# Facebook+ Manager: keep up with upstream

Date: 2026-11-02

How Facebook+ Manager stays current with HushFacebook patches, morphe-patcher, and Morphe Manager without auto-shipping APKs, and how a free AI that has only git, a browser, and Gradle can run that work from one file.

Branded shell, one-tap wizard, JSON-only self-update, and identity rules in `docs/superpowers/specs/2026-10-28-facebook-plus-manager-branded-shell-design.md` still apply. This document does not change product UI.

## Goal

1. Explain why a new HushFacebook release can demand a newer manager, and when it does not.
2. Notify the maintainer when any of three upstream repos moves, classified so a rebuild is only requested when it is required.
3. Make swapping the compiled patcher a short, pinned, documented step.
4. Give future AIs a single protocol file. After they read it, the phrase **implement update protocol** means follow that file in order and stop.

A non-technical maintainer stars the standing GitHub issue, or pastes the protocol path into a new chat. They never have to reread the tree to bump the engine and ship.

## Why the patcher version exists

HushFacebook does not ship Facebook. It ships a `.mpp` patch bundle. That bundle is compiled against a specific `morphe-patcher` API and stamps `Patcher-Version` in its manifest.

Our manager embeds that same library. `libs.versions.toml` `morphe-patcher` becomes `BuildConfig.PATCHER_VERSION`. At load and at preflight, Morphe compares the bundle's stamp to that field (`isPatcherOutdated`). If the bundle wants a newer patcher, the honest outcomes are:

- Skip the dialog and still patch: works only while the new patches do not call APIs we lack.
- Skip the dialog and the new patches call new APIs: `NoSuchMethodError` / linkage failure while loading or running the bundle.
- Rebuild the manager with the matching patcher: the real fix.

Skipping the Facebook+ preflight dialog in 2.1.2 hid a false stop on an already-loaded bundle. It is not a strategy for the next HushFacebook release.

Recorded bumps:

| HushFacebook | Required manager (their notes) | Required patcher | Facebook pin |
|---|---|---|---|
| v0.18.x | 2.0.0 | 2.0.0 | 584.0.0.51.56 |
| v0.19.0 (2026-10-28) | 2.2.0 | 2.0.1 | 584.0.0.51.56 |
| v0.20.0 (2026-11-01) | 2.3.0 | 2.1.0 | 585.0.0.47.54 |

We still ship patcher `2.0.0` via `libs.versions.toml` and a leftover `/workspace/morphe-patcher` checkout at `version = 2.0.0`. Official Morphe Manager is already at `v2.3.0` with patcher `2.1.0`.

Will it keep happening? Patch *content* can land daily and the phone already downloads it. Engine stamps move whenever HushFacebook rebuilds on a newer patcher. In eight days they required a newer manager twice. That will not stay this hot forever, but while Morphe is shipping patcher minors every few days, we must rebuild on those minors.

## Three clocks

| Clock | Who ships it | Phone already gets it? | Needs a new Facebook+ Manager APK? |
|---|---|---|---|
| HushFacebook `.mpp` (switches, Facebook pin, catalog) | `SysAdminDoc/HushFacebook` GitHub release | Yes, existing source auto-update | No, unless `Patcher-Version` is newer than we ship |
| `morphe-patcher` (JVM compiled into our APK) | `MorpheApp/morphe-patcher` | No | **Yes** |
| Morphe Manager UI / installer | `MorpheApp/morphe-manager` | No | Optional. Only if we want those fixes |

Patches keep themselves current. We rebuild when their `Patcher-Version` is newer than `BuildConfig.PATCHER_VERSION`.

## Non-goals

- Auto-compiling or auto-releasing an APK
- Auto-merging a patcher bump
- Forking HushFacebook or writing patches
- Changing Facebook+ product UI, branding, or `applicationId`
- Watching pre-release / `-dev` tags
- Shipping Morphe's APK or pointing `app-release.json` at Morphe
- Using this Agent environment as a always-on worker (GitHub Actions on Scroll-freedom is the worker)

## Architecture

Two paths, one source of truth.

```
upstream-pins.json
        ^
        |
  GitHub Action (every 6h) ---- standing issue on Scroll-freedom
        |
        v
  human or AI reads docs/superpowers/UPDATE-PROTOCOL.md
        |
        +-- patches-only  -> stop (phone already has it)
        +-- manager-ui    -> stop unless the human asked
        +-- patcher-bump  -> sync-patcher.sh -> tests -> assembleDebug -> GitHub release -> app-release.json on main
```

The watcher never builds. The protocol is the only path that builds or ships.

## Pins file

Path: `facebook-plus-manager/upstream-pins.json`

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

Rules:

- Tags are latest *stable* releases only (no `draft`, no `prerelease`, no `-dev`).
- `hushfacebook.patcherVersion` is the bundle manifest `Patcher-Version` for that tag, not Morphe Manager's version name.
- `morphePatcher.tag` without a leading `v` mismatch is allowed in comparison after stripping `v`. Stored form always includes `v` if upstream does.
- The watcher and the protocol both read this file. Neither guesses.

On first implementation, fill the file from what we actually ship today, not from what HushFacebook latest wants. The first watcher run will then correctly open a `patcher-bump` for v0.20.0 / patcher 2.1.0.

## Classification

Inputs: pins vs latest stable tags.

| Condition | Class | Action |
|---|---|---|
| HushFacebook tag changed, its `Patcher-Version` is not newer than `morphePatcher` pin / `libs.versions.toml` | `patches-only` | Notify. Do not rebuild. Phone already downloads the `.mpp`. Facebook pin in the catalog updates on the next bundle load. |
| HushFacebook `Patcher-Version` is newer than we ship, or `morphe-patcher` stable tag is newer than we ship | `patcher-bump` | Notify and, if running the protocol, rebuild. |
| Only Morphe Manager tag changed | `manager-ui` | Notify. Skip rebuild unless the human asked for those UI/installer fixes. |
| Nothing changed | (no class) | Watcher leaves the standing issue body as "up to date" with the last check time. Protocol stops after step 1. |

If more than one class applies, the standing issue lists all of them. The protocol runs `patcher-bump` and ignores `manager-ui` unless asked. `patches-only` never compiles.

## Watcher

A GitHub Action on `adnanXmacro/Scroll-freedom`, cron every 6 hours, plus `workflow_dispatch`.

It:

1. Reads `facebook-plus-manager/upstream-pins.json`.
2. Fetches latest stable release for each of the three repos.
3. For HushFacebook, records tag, published time, and the required manager/patcher plus Facebook version from the release notes line they already publish ("needs Morphe Manager X / patcher Y", "moves to Facebook …"). First implementation does not download the `.mpp`. If notes omit the patcher stamp, the issue says `patcherVersion: unknown` and classifies as `patcher-bump` so a human checks rather than assuming `patches-only`.
4. Classifies.
5. Creates or updates **one** standing issue, title `Upstream keep-up`. Updates in place. No new issue per run.
6. Labels on that issue match the current class set: `patches-only`, `patcher-bump`, `manager-ui`. Remove labels that no longer apply.
7. Body includes: last check UTC, each pin vs latest, class, and for `patcher-bump` the required `Patcher-Version`, our shipped version, and `./scripts/sync-patcher.sh <tag>`.
8. If GitHub fetch fails: leave the previous body, prepend a failure line, do not guess tags.

Free plan is enough (cron + issue write). No self-hosted runner. No APK build in CI for this workflow.

## Patcher swap

The engine is already a Gradle module. The failure mode is two sources of truth: `libs.versions.toml` and a local composite at `/workspace/morphe-patcher` that silently substitutes `app.morphe:morphe-patcher`.

Script: `facebook-plus-manager/scripts/sync-patcher.sh <tag>`

1. Resolve `<tag>` (accept `v2.1.0` or `2.1.0`; store with `v` if upstream uses it).
2. Prefer the published Maven artifact. Set `gradle/libs.versions.toml` `morphe-patcher` to that version. That string is `BuildConfig.PATCHER_VERSION`.
3. If `../morphe-patcher` exists (workspace composite), check it out at that tag so `gradle.properties` `version` matches. Do not leave a stale checkout overriding Maven. If the checkout is absent, print that Maven will be used and continue.
4. Write `upstream-pins.json` `morphePatcher.tag` (and `hushfacebook.patcherVersion` if the protocol already recorded the new stamp).
5. Fail if toml, pin, and composite (when present) disagree. No partial toml write; write pins last.
6. Do not compile. Do not release.

Gradle must also fail the `:app` build when those three disagree, so a human who edits toml by hand cannot ship a lie.

## Protocol file (the command)

Path: `docs/superpowers/UPDATE-PROTOCOL.md`

A free AI with no slash commands is started with: the repo, and "read `docs/superpowers/UPDATE-PROTOCOL.md` first." After that, these phrases mean the same thing and must run that file in order:

- implement update protocol
- run the update protocol
- swap the patcher
- keep up with upstream

The file is the command. It contains:

1. Trigger phrases (above).
2. Read `facebook-plus-manager/upstream-pins.json`. Compare to latest stable HushFacebook, morphe-patcher, Morphe Manager (browser or `gh`).
3. Classify. Stop on `patches-only`. Skip `manager-ui` unless the human asked. Continue only on `patcher-bump`.
4. Run `scripts/sync-patcher.sh` for the required patcher tag. Keep the local composite from overriding.
5. Rewrite pins.
6. Run the listed unit tests, then `./gradlew :app:assembleDebug --offline`.
7. Verify launcher label `Facebook+ Manager`, package `app.eightbitsoftworks.facebookplus.manager.debug`, no Morph / Morph Debug, English copy does not name HushFacebook.
8. GitHub release `facebook-plus-manager-v<version>-debug` with the debug APK. Then commit and push `app-release.json` (workspace root, `facebook-plus-manager/app-release.json`, `facebook-plus-manager/app/app-release.json`) to `main`. Raw GitHub is what the phone fetches.
9. Stop rules (next section). Never ship Morphe's APK. Never guess versions. No extra features in the same pass.

Bump `facebook-plus-manager/app/gradle.properties` `version` to the next patch (example: 2.1.2 -> 2.1.3) only on a `patcher-bump` that produces an APK.

## Handoff file

Path: `docs/superpowers/HANDOFF.md`

Short. First file after the protocol if someone is lost. Covers:

- What this app is (one-tap Facebook+ Manager, not Morphe).
- The three clocks and when an APK is required.
- Pointer to the protocol, pins, branded-shell spec.
- Exact test / build / ship commands.
- Never ship Morphe's APK. Never leak Morph in the launcher. Never name HushFacebook in user-facing English copy. `applicationId` stays `app.eightbitsoftworks.facebookplus.manager`.

## Stop rules

Watcher and protocol both obey:

- GitHub fetch fails: note it, do not guess tags.
- Pins, `libs.versions.toml`, and any local `morphe-patcher` checkout disagree: stop. Do not compile.
- Unit tests fail, Morph in the launcher label, or HushFacebook in English copy: stop. Do not release.
- Never ship Morphe's APK.
- Never push `app-release.json` until the new APK is on the GitHub release.

## Tests

Add:

- Classifier: given `(hushTag, hushPatcherVersion, patcherTag, managerTag)` vs pins, result is `patches-only` | `patcher-bump` | `manager-ui` | none. Cover "HushFacebook moved, patcher stamp unchanged", "patcher stamp newer", "only manager moved", "nothing moved", "both hush stamp and patcher tag newer" (still `patcher-bump`).
- Pins file parses and required keys exist (`hushfacebook.tag`, `hushfacebook.patcherVersion`, `hushfacebook.facebookVersion`, `morphePatcher.tag`, `morpheManager.tag`, each `repo`).

Keep in the protocol's run list (already exist):

- `app.morphe.manager.domain.facebookplus.FacebookPlusFlowTest`
- `app.morphe.manager.domain.facebookplus.FacebookPlusBrandingTest`
- `app.morphe.manager.domain.facebookplus.FacebookPlusPinTest`
- `app.morphe.manager.domain.manager.DownloadUrlResolverTest`
- `app.morphe.manager.domain.manager.ManagerUpdatePolicyTest`
- `app.morphe.manager.util.ManagerUpdateUrlsTest`

## Error handling

- Watcher: fetch errors are recorded on the standing issue; previous pins stay.
- `sync-patcher.sh`: non-zero if tag missing, Maven coordinate cannot be written, or composite mismatches. No partial toml write; write pins last.
- Protocol: any stop rule ends the chat with what failed and what the human should retry. Do not continue to release.

## Out of scope for the first implementation plan

- Downloading the `.mpp` inside Actions (release notes are enough for v1).
- Auto-PRs.
- Watching Morphe desktop.
- Changing the 2.1.2 skip of the Facebook+ patcher-version *dialog*. That skip stays for the already-loaded bundle. A real API mismatch still requires this keep-up path.

## Done when

- `upstream-pins.json` exists and matches what we ship.
- The Action is on Scroll-freedom `main`, cron 6h, standing issue updates in place.
- `scripts/sync-patcher.sh` bumps toml, composite, and pins or fails closed.
- `UPDATE-PROTOCOL.md` and `HANDOFF.md` exist. A new chat that reads the protocol and is told **implement update protocol** can bump, test, and ship without reading the rest of the repo.
- Classifier and pins-parse tests are green.
