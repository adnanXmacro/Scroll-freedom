# Facebook+ Manager: branded shell on the existing engine

Date: 2026-10-28

Replaces the hide-Morphe-UI approach in `docs/superpowers/specs/2026-10-27-facebook-plus-manager-rebrand-and-dynamic-pin-design.md` for product identity, screens, and self-update. Pin, APKMirror handoff, HushFacebook-only source, legal/risk, and one-tap wizard from that spec and `docs/superpowers/specs/2026-10-25-facebook-plus-manager-design.md` still apply except where this document overrides them.

## Goal

Ship one APK of Facebook+ Manager that:

1. Is a branded Facebook+-only app. The user never sees Morphe / Morph as the product name, launcher label, or an installed update.
2. Opens, gets the pinned Facebook APK (APKMirror / Morphe web-search), patches it in the background with HushFacebook, installs Facebook+.
3. Keeps a small Settings/About (theme, notifications, installer, patcher, manager updates, storage, signing key, About).
4. Replaces the leaky fork on the phone: same `applicationId` `app.eightbitsoftworks.facebookplus.manager`.

A non-technical user installs this APK, taps Make Facebook+, lands on the catalog version's APKMirror page, and ends with Facebook+ on the home screen. Tapping a manager update never installs official Morphe.

## Why this pass exists

The Morphe-manager fork leaked identity twice:

- Launcher / wordmark still Morphe ("Morph").
- Self-update JSON on GitHub `main` still described Morphe 2.1.0, and/or GitHub-API fallback resolved to `MorpheApp/morphe-manager`, so "Update Facebook+ Manager" installed Morphe.

Hiding Morphe UI without deleting it, while still shipping Morphe's home, update client, and JSON fallback, is the failure mode. This pass does not ship that shell.

## Non-goals

- Writing or forking HushFacebook patches
- Credits inside Facebook / HushFacebook settings
- Silent download of Facebook from APKMirror or any CDN
- Expert patch picker, YouTube, Reddit, extra patch sources, Root Mount / Magisk
- 32-bit Facebook
- Play Store listing
- Patching Facebook from inside Facebook
- Changing Morphe upstream
- Shipping Meta's APK
- New `applicationId` (would leave the leaky app on the phone still able to update to Morph)
- Continuing to hide-not-delete Morphe HomeScreen as the launcher

## Two apps

| App | Launcher icon | Launcher label | Package |
|---|---|---|---|
| Facebook+ Manager | Red `f+Mgr` on black | Facebook+ Manager (debug: same; never Morph / Morph Debug) | `app.eightbitsoftworks.facebookplus.manager` (debug suffix `.debug`) |
| Facebook+ | Blue `f+` if HushFacebook has an icon option; otherwise stock Facebook icon | Facebook+ | `com.facebook.katana` |

Facebook+ is produced on the device at patch time. We never ship Meta's binary.

Kotlin packages may stay `app.morphe.manager`. That name is not user-visible. Source copyright headers and GPLv3 notices stay. User-visible chrome (strings, icons, About, update URLs) is ours.

## Architecture

Same Gradle app module. New Compose UI is the launcher. Morphe `HomeScreen`, sources management, patch list, Expert, icon picker, GitHub PAT, and Translate Morphe are not reachable from the shipped UI.

Keep as engine (no Morphe chrome):

- `patcher-core` and the embedded JVM
- HushFacebook as the only patch source, loaded in the background
- `FacebookPlusPin` / `FacebookPlusFlow` (catalog snapshot, not hardcoded `584…`)
- `DownloadSearchUrls` plus the existing APK picker / web-search handoff
- Signing key, session / Shizuku / root installer
- Theme and notification preferences

New, ours:

- Home, patch progress, Settings/About
- Update checker that cannot install Morphe
- All user-visible strings and the red `f+Mgr` launcher / splash

`applicationId` stays `app.eightbitsoftworks.facebookplus.manager` so this APK replaces the leaky fork. Debug keeps `applicationIdSuffix = ".debug"`. No debug `resValue` that suffixes Morph / Debug.

## Screens and flow

### Home

One screen. Title `Facebook+ Manager`. Red `f+Mgr` icon. One primary action.

- HushFacebook still loading: primary action disabled. Copy: `Can't make Facebook+ until HushFacebook finishes loading.`
- Facebook+ not installed: **Make Facebook+**
- Facebook+ installed: **Open Facebook+**, plus **Update Facebook+** if the catalog recommended version moved

### Make / Update Facebook+

1. Gates, in order:
   - Device ABI includes `arm64-v8a`. 32-bit: refuse.
   - Free storage at least **1 GB**. Under 1 GB: hard stop. No extra low-storage warning.
   - Stock Facebook (`com.facebook.katana` not signed by Manager) uninstalled. If present: tell the user to uninstall it first.
2. Show the live pin version from `AppVersionCatalog.recommendedVersion("com.facebook.katana")`. If that is null, do not start; same loading copy as Home.
3. Find-APK step (same as YouTube Music in Morphe): show that version, open Morphe web-search `api.morphe.software/v2/web-search/com.facebook.katana~{version}~arm64-v8a`, follow redirects. On timeout / no redirect, Facebook-only fallback is the constructed APKMirror version page. Never a Morphe manager APK URL.
4. User downloads and returns, or picks a file.
5. Pin-check: package `com.facebook.katana`, version name matches pin, ABI includes `arm64-v8a`, and version code is in the catalog set when that set is non-empty. Density is not validated.
6. Wrong APK: refuse. Stay on find-APK. No patch-anyway.
7. Patch with HushFacebook defaults. Display name `Facebook+`. Blue `f+` launcher only if HushFacebook exposes an icon option; otherwise name only (do not fork HushFacebook).
8. Sign with Manager's key. Install as `com.facebook.katana`.

Saved original APK is reused only when its version still matches the current pin.

### Patch progress

Progress only. No patch list, no Expert, no source picker.

### Settings

Keep:

- Appearance: theme only
- Notifications
- Installer
- Patcher
- Manager updates (prereleases + metered)
- Storage
- Signing key import/export
- About

Do not show: Expert, APK download helpers, icon picker, other-apps, add-source, Translate Morphe, GitHub PAT, PATCH_OPTIONS.

About copy (Manager only):

- `powered by projectAdnan`
- `Publisher: 8BiT Softworks`
- `RageDoll engine by projectAdnan`
- GPLv3 Morphe Manager + HushFacebook
- This is a modified Morphe Manager and is not Morphe

## Manager self-update (must not install Morphe)

Check **only**:

`https://raw.githubusercontent.com/adnanXmacro/Scroll-freedom/refs/heads/main/app-release.json`

Rules:

- No GitHub Releases API fallback for manager updates.
- No `MorpheApp/morphe-manager` URL in shipped code, JSON, or strings that the update path can follow.
- Offer an update only if all of these hold:
  1. JSON `version` is strictly newer than `BuildConfig.VERSION_NAME`
  2. JSON `download_url` host/path is on `adnanXmacro/Scroll-freedom`
  3. The downloaded APK's package name equals `BuildConfig.APPLICATION_ID`
- If any check fails, do not install. Do not fall back to another URL.
- Both stable and prerelease manager JSON URLs are this same `main` file until a prerelease branch exists.

Release process is part of the product, not optional:

1. Build the APK.
2. Upload it as a GitHub release asset on `adnanXmacro/Scroll-freedom`.
3. **Commit and push** `app-release.json` on `main` so GitHub raw cannot still serve Morphe 2.1.0. `download_url` must be that asset. Never `morphe-manager-*.apk`.

The previous leak was JSON on `main` still pointing at Morphe, plus a GitHub-API fallback. Both are forbidden here.

## Pin (unchanged from 2026-10-27 except storage)

`FacebookPlusPin` is a catalog snapshot (`fromCatalog` / `FacebookPlusPinSnapshot`):

- Version name: `recommendedVersion("com.facebook.katana")`
- Version codes: catalog set when present; if empty, name + ABI is enough
- ABI: `arm64-v8a`
- Package: `com.facebook.katana`
- Display name: `Facebook+`
- Source: `SysAdminDoc/HushFacebook`

Density is not a validator. Hardcoded `584.0.0.51.56` / versionCode / APKMirror URL stay deleted.

## Errors the user can see

- HushFacebook not loaded: button disabled, loading copy above
- Stock Facebook installed: uninstall first
- 32-bit device: refuse
- Under 1 GB free: hard stop
- Wrong APK: refuse, stay on find-APK
- Patch or install fail: stay in this app; never open or suggest Morphe

## Testing

JVM tests (TDD where the logic is pure):

- Pin snapshot / validate (already present; keep)
- Download search URLs: Facebook is web-search + APKMirror fallback, never a Morphe manager APK
- Storage gate: stop under 1 GB
- Update JSON URL is `adnanXmacro/Scroll-freedom` `app-release.json`
- Shipped constants contain no `MorpheApp/morphe-manager`
- Update installer refuses an APK whose package is not `BuildConfig.APPLICATION_ID`
- Update installer refuses a `download_url` not on `adnanXmacro/Scroll-freedom`

Manual on a 64-bit Android 11+ device:

- Launcher says Facebook+ Manager, red `f+Mgr`, not Morph / Morph Debug
- Make Facebook+ shows the bundle version, then the APKMirror (or web-search) variant
- Wrong APK refused; same find-APK step
- Patched app label Facebook+
- Settings match the keep-list above
- About: projectAdnan, 8BiT, RageDoll
- Opening the app does not offer an update that installs Morphe
- If an update is offered, the APK package is ours

## Done when

A 64-bit phone that already had the leaky fork installs this APK (same applicationId), sees Facebook+ Manager / red `f+Mgr`, taps Make Facebook+, gets the HushFacebook version via APKMirror, and never downloads or installs `morphe-manager-*.apk`. Settings and About match this spec.

## Overrides

| Topic | Old | This spec |
|---|---|---|
| Product shell | Hide Morphe UI, keep HomeScreen | New Home / patch / Settings; Morphe HomeScreen not the launcher |
| Storage hard stop | ~2 GB | 1 GB |
| Manager update source | JSON on this repo, GitHub API fallback allowed | JSON on this repo only; no GitHub API fallback |
| `app-release.json` on `main` | Easy to leave as Morphe 2.1.0 | Pushing that file is a required release step |
| Facebook+ icon | Blue `f+` if HushFacebook has the option | Unchanged: name `Facebook+`; icon only if the option exists |
