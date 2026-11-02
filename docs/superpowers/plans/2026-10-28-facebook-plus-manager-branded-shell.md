# Facebook+ Manager branded shell

Spec: `docs/superpowers/specs/2026-10-28-facebook-plus-manager-branded-shell-design.md`

Work in `/workspace/facebook-plus-manager`. Kotlin packages stay `app.morphe.manager`. `applicationId` stays `app.eightbitsoftworks.facebookplus.manager`. Do not change `patcher-core` unless a compile error forces it. Do not fork HushFacebook.

This pass replaces Morphe `HomeScreen` as the launcher. It does **not** delete Morphe screens; they must be unreachable from shipped navigation.

TDD for JVM-testable domain code: write the failing test, run it, then the minimum production change. UI is configuration; cover update URL / package / storage with unit tests.

## Why the last APK still installed Morphe

Three leaks, all still in the tree:

1. `MANAGER_REPO_URL` in `app/src/main/java/app/morphe/manager/util/Constants.kt` is `https://github.com/MorpheApp/morphe-manager`. `MorpheAPI.managerConfig` parses that. `getAppUpdate()` falls back to `getManagerFromGitHub()` when JSON fails **or** when `USE_MANAGER_DIRECT_JSON` is false.
2. GitHub `main` `app-release.json` was still Morphe 2.1.0 until this workspace rewrote it, and that rewrite was **not pushed**. The phone reads raw GitHub, not this working tree.
3. `UpdateViewModel.downloadUpdate` / `installUpdate` never checks the APK package against `BuildConfig.APPLICATION_ID`, so a Morphe APK installs if the URL says so.

Launcher name `Facebook+ Manager` is already in `strings.xml`. Morph on the home screen is Morphe `HomeScreen` + wordmark (now text) + generated adaptive icons. This pass puts a new home on the NavHost and kills the update fallback.

## Order

1. Update policy (TDD) — stop Morph installs even if JSON is stale
2. Storage gate 1 GB (TDD)
3. New home as NavHost startDestination
4. Settings already cut; keep it, hide leftover Morphe entry points
5. `app-release.json` + assembleDebug + **push JSON to GitHub `main`** + replace release APK

## 1. Manager updates cannot install Morphe

### Tests first

Edit `app/src/test/java/app/morphe/manager/util/ManagerUpdateUrlsTest.kt`.

Keep existing URL assertions. Add:

- `MANAGER_REPO_URL` contains `adnanXmacro/Scroll-freedom` and does **not** contain `MorpheApp/morphe-manager`.
- `USE_MANAGER_DIRECT_JSON` is true.

Add `app/src/test/java/app/morphe/manager/domain/manager/ManagerUpdatePolicyTest.kt`.

Extract a pure object `ManagerUpdatePolicy` (new file) and test:

- `isAllowedDownloadUrl("https://github.com/adnanXmacro/Scroll-freedom/releases/download/facebook-plus-manager-v2.1.0-debug/facebook-plus-manager-2.1.0-debug.apk")` is true.
- `isAllowedDownloadUrl("https://github.com/MorpheApp/morphe-manager/releases/download/v2.1.0/morphe-manager-2.1.0.apk")` is false.
- `isAllowedDownloadUrl("https://raw.githubusercontent.com/MorpheApp/morphe-manager/main/app-release.json")` is false.
- Empty / blank / non-https URL is false.
- `packageMatches(downloadedPackage, expectedPackage)` true only on exact match.
- `shouldInstall(downloadUrl, downloadedPackage, expectedPackage)` true only when URL is allowed **and** packages match.

Run (expect fail):

```
cd /workspace/facebook-plus-manager && ./gradlew :app:testDebugUnitTest --tests 'app.morphe.manager.util.ManagerUpdateUrlsTest' --tests 'app.morphe.manager.domain.manager.ManagerUpdatePolicyTest' --offline
```

### Production

`Constants.kt`:

```
MANAGER_REPO_URL = "https://github.com/adnanXmacro/Scroll-freedom"
```

Keep JSON URLs as they are (already Scroll-freedom `main` `app-release.json`).

`MorpheAPI.getAppUpdate()`:

- Use JSON only. Delete the `fallbackTo { getManagerFromGitHub() }` branch and the `else getManagerFromGitHub()` branch.
- After mapping JSON, if `!ManagerUpdatePolicy.isAllowedDownloadUrl(candidate.downloadUrl)` return null. Log a warning. Do not install.
- Leave `getManagerFromGitHub()` in the file unused if compile needs it for other callers; if it is only used here, stop calling it from `getAppUpdate`. Changelog may still read this repo via `managerConfig` once `MANAGER_REPO_URL` is ours.

`UpdateViewModel.downloadUpdate` / `installUpdate`:

- Before download: refuse if URL fails `isAllowedDownloadUrl`.
- After download, before `installUpdate`: read the APK package (existing APK parser / `PackageManager.getPackageArchiveInfo`). If it is not `BuildConfig.APPLICATION_ID`, delete the file, do not install, show a generic failure (do not mention Morphe).

Re-run the two test classes until green.

## 2. Storage hard stop is 1 GB

### Tests first

Edit `FacebookPlusFlowTest.kt`:

- Rename / rewrite `facebook needs two gigabytes and cannot patch anyway`:
  - `storageThresholdBytes(Facebook)` = `1_000_000_000L`
  - `storageOk(1_000_000_000L, Facebook)` true
  - `storageOk(999_999_999L, Facebook)` false
  - `allowsPatchAnywayOnLowDisk(Facebook)` still false
- `gate blocks low storage` already uses `500_000_000L`; keep it.
- `gate is ready` can keep `3_000_000_000L` or drop to `1_000_000_000L`.

Run facebookplus tests (expect fail on the 2 GB assertion).

### Production

`FacebookPlusFlow.kt`: `FACEBOOK_STORAGE_THRESHOLD_BYTES = 1_000_000_000L` (same as default). No low-storage warning UI.

Re-run facebookplus tests.

## 3. New home is the launcher

Morphe `HomeScreen` is a three-page pager (Sources / Apps / Settings) plus app cards for every patchable package. That is the Morph product. Do not use it as `startDestination`.

### New files

`app/src/main/java/app/morphe/manager/ui/screen/facebookplus/FacebookPlusHomeScreen.kt`

One screen:

- Title: `stringResource(R.string.app_name)` (`Facebook+ Manager`)
- Gear opens existing `SettingsPage` (navigate `SettingsDetail` or a dedicated Settings route already used)
- Body:
  - If HushFacebook recommended version is null: primary button disabled, text `home_facebook_plus_bundle_loading`
  - Else if Facebook+ (Manager-signed `com.facebook.katana`) not installed: **Make Facebook+** → existing `homeViewModel.patchFlow` start path used by the Facebook card today
  - Else: **Open Facebook+** (`home_facebook_plus_open`) and, if catalog version != installed version, **Update Facebook+** (`home_facebook_plus_update`)
- Reuse existing Facebook+ dialogs already in `HomeDialogs.kt` (unsupported, uninstall stock, risk, APK availability). Host them from this screen the same way `HomeScreen` hosts `DialogHost` / `patchFlow` dialogs. If wiring all of `HomeScreen`'s dialog stack is cheaper than duplicating, extract the Facebook+ dialog host into a shared composable and call it from the new home. Do **not** show Sources, other-app cards, batch, onboarding tour, or Morphe greeting phrases.

`MainActivity.kt` NavHost:

- `startDestination` = new route (e.g. `FacebookPlusHome`) that composes `FacebookPlusHomeScreen`
- Keep `Patcher`, `SettingsDetail`, `Licenses`
- Do not register `HomeScreen`, `BatchPatcher`, `PatchSelections` as reachable destinations (leave the composable in the file if Nav types still exist; do not navigate there)
- Back from Settings / Patcher / Licenses pops to `FacebookPlusHome`, not `HomeScreen`

Launcher / splash: already `app_name` + `ic_launcher_foreground` (red f+Mgr PNG). Do not restore `MorpheWordmark` on this home.

No JVM test required for the composable. Manual check is the done-when.

## 4. Settings stay as already cut

Do not re-add PATCH_OPTIONS, helpers, icon picker, GitHub PAT, Translate Morphe.

About already has projectAdnan / 8BiT / RageDoll. Confirm CreditsDialog does not deep-link the user into installing Morphe Manager.

`MANAGER_REPO_URL` change in task 1 also stops About/changelog from pointing at MorpheApp.

## 5. Build, push JSON, ship

```
cd /workspace/facebook-plus-manager
./gradlew :app:testDebugUnitTest --tests 'app.morphe.manager.domain.facebookplus.*' --tests 'app.morphe.manager.domain.manager.DownloadUrlResolverTest' --tests 'app.morphe.manager.util.ManagerUpdateUrlsTest' --tests 'app.morphe.manager.domain.manager.ManagerUpdatePolicyTest' --offline
./gradlew :app:assembleDebug --offline
```

Then, in this order, no skip:

1. Upload `app/build/outputs/apk/debug/facebook-plus-manager-2.1.0-debug.apk` to GitHub release `facebook-plus-manager-v2.1.0-debug` (`--clobber`).
2. Set workspace `app-release.json`, `facebook-plus-manager/app-release.json`, and `facebook-plus-manager/app/app-release.json` `download_url` to that asset. `version` must be **strictly newer than the APK already on the phone** if we want this build to replace via in-app update; for a sideload replace, same `2.1.0` is fine because `applicationId` matches. Prefer bumping `app/gradle.properties` `version` if the phone still has 2.1.0 of the leaky fork and we need the in-app updater to offer this APK. If the user sideloads this APK over the leaky one, version can stay `2.1.0`.
3. **Commit and push `app-release.json` on GitHub `main`.** Raw GitHub is what the phone fetches. A local-only JSON does not count. Never leave `morphe-manager-*.apk` in that file.

Manual on a 64-bit Android 11+ device:

- Uninstall is not required; install over the leaky fork (same applicationId)
- Launcher: Facebook+ Manager, red f+Mgr, not Morph
- Home is Make/Open/Update Facebook+ only — no Sources, no YouTube, no Morphe wordmark
- Opening the app does **not** offer an update that downloads `morphe-manager-*.apk`
- Make Facebook+ still uses APKMirror / web-search pin
- Settings: theme, notifications, installer, patcher, updates, storage, key, About

## Done when

The phone that already had the leaky fork installs this APK, sees Facebook+ Manager / red f+Mgr, never gets Morphe as an update, and can still Make Facebook+ from the HushFacebook pin.
