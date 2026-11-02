# Facebook+ Manager rebrand, dynamic pin, and Settings cut

Spec: `docs/superpowers/specs/2026-10-27-facebook-plus-manager-rebrand-and-dynamic-pin-design.md`

Work in `/workspace/facebook-plus-manager`. Do not change `patcher-core` unless a compile error forces it. Hide unused Morphe UI; do not delete it. Kotlin packages stay `app.morphe.manager`.

TDD for JVM-testable domain code: write the failing test, run it, then the minimum production change. UI/resources/icons are configuration; cover them with string/URL unit tests where a pure function exists.

User-supplied icons (this session):

- Manager: `.monkeycode-tmp-files/eed09158-1793123357415-2.jpg` (red `f+Mgr`)
- Facebook+: `.monkeycode-tmp-files/b74734cb-1793123479727-1.jpg` (blue `f+`; drop the FACEBOOK PLUS caption)

## 1. Dynamic pin from the HushFacebook catalog

`FacebookPlusPin` is a snapshot, not hardcoded `584.0.0.51.56` / `475817393` / APKMirror URL. Constants that stay: `packageName`, `abi`, `displayName`, `sourceRepo`.

### Tests first

Edit `app/src/test/java/app/morphe/manager/domain/facebookplus/FacebookPlusPinTest.kt`.

- Remove `pin targets HushFacebook and the supported Facebook build` assertions on hardcoded version / versionCode / APKMirror URL.
- Add `fromCatalog` (name it to match the production API) that takes version name plus optional version-code set.
- Matching APK still `Ok`.
- Wrong package / wrong version name / ABI without `arm64-v8a` still refuse.
- When the catalog lists version codes, an APK whose code is in the set is `Ok`; a code outside the set is `WrongVersion`.
- When the catalog lists no codes, version name + ABI is enough (`versionCode` on the APK is ignored).
- `deviceSupported` unchanged.

Edit `app/src/test/java/app/morphe/manager/domain/facebookplus/FacebookPlusFlowTest.kt`.

- `reuseSavedApk(packageName, savedVersion, pinVersion)`: true only when package is Facebook and `savedVersion == pinVersion`.
- `apkSourceStep(Facebook)` is `ApkAvailability`, not `DownloadInstructions`. `skipApkAvailability` is false for Facebook.
- `nextOnPinFailure()` is `ApkAvailability` (reopen the find-APK dialog), not `DownloadInstructions`.
- Storage / gate tests unchanged.

Run (expect fail):

```
cd /workspace/facebook-plus-manager && ./gradlew :app:testDebugUnitTest --tests 'app.morphe.manager.domain.facebookplus.*'
```

### Production

`app/src/main/java/app/morphe/manager/domain/facebookplus/FacebookPlusPin.kt`

- Drop `versionName`, `versionCode`, `dpi`, `apkMirrorUrl` constants.
- Add a data class for the live pin, e.g. `data class FacebookPlusPinSnapshot(val versionName: String, val versionCodes: Set<Long> = emptySet())`.
- `fun fromCatalog(versionName: String, versionCodes: Set<Long> = emptySet()) = FacebookPlusPinSnapshot(...)`.
- `fun validate(apk: FacebookPlusApk, pin: FacebookPlusPinSnapshot): Result`.
- Version check: name must match. If `pin.versionCodes` is non-empty, `apk.versionCode` must be in that set. Density is not validated.

`app/src/main/java/app/morphe/manager/domain/facebookplus/FacebookPlusFlow.kt`

- `skipApkAvailability` always false (or delete and inline).
- `apkSourceStep(Facebook)` = `ApkAvailability`.
- `reuseSavedApk(packageName, savedVersion, pinVersion)`.
- `nextOnPinFailure()` = `ApkAvailability`.

`app/src/main/java/app/morphe/manager/ui/viewmodel/PatchFlow.kt`

- Build the snapshot from `session.recommendedVersion` and that target's `buildCodes` (from `compatibleVersions`). If recommended version is null, do not start the wizard; show "Can't make Facebook+ until HushFacebook finishes loading."
- `processSelectedApp`: `FacebookPlusPin.validate(apk, snapshot)`.
- Stop branching on `skipApkAvailability`. Facebook uses the same ApkAvailability path as YTM. Keep stock-Facebook / storage / ABI gates.

`app/src/main/res/values/strings.xml`

- `home_facebook_plus_wrong_apk`: drop "pinned file"; say to download the version Manager showed.

Re-run the facebookplus tests until green.

## 2. Download URL: no Facebook bypass

### Tests first

Add `app/src/test/java/app/morphe/manager/domain/manager/DownloadUrlResolverTest.kt`.

`DownloadUrlResolver` needs `MorpheAPI`. If that is hard to construct on the JVM, extract URL builders to a pure object (e.g. `DownloadSearchUrls`) and test that:

- `apiSearchUrl("com.facebook.katana", "584.0.0.51.56")` contains `MORPHE_API_URL` and `com.facebook.katana` and the version. It is **not** `apkmirror.com/apk/facebook-2/facebook/facebook-584-0-0-51-56-release/`.
- `webSearchUrl("com.facebook.katana", "584.0.0.51.56")` is a search or a constructed version listing, never Morphe's manager APK URL.
- Fallback helper (if extracted): `apkMirrorVersionUrl("584.0.0.51.56")` = `https://www.apkmirror.com/apk/facebook-2/facebook/facebook-584-0-0-51-56-release/`.

Run (expect fail):

```
./gradlew :app:testDebugUnitTest --tests 'app.morphe.manager.domain.manager.DownloadUrlResolverTest'
```

### Production

`app/src/main/java/app/morphe/manager/domain/manager/DownloadUrlResolver.kt`

- Delete the three `if (packageName == FacebookPlusPin.packageName) return FacebookPlusPin.apkMirrorUrl` branches.
- Facebook goes through `follow()` / `apiSearchUrl()` like every other package (`{package}~{version}~arm64-v8a`).
- On timeout / no redirect: existing `webSearchUrl`, or the constructed APKMirror version URL for Facebook only as fallback. Happy path is the API.

`PatchFlow.processSelectedApp` on pin failure: `dialogs.advanceTo(HomeDialog.ApkAvailability)` (or whatever `nextOnPinFailure` maps to), then `resolveDownloadRedirect()` so the same version is sought again.

Re-run resolver + facebookplus tests.

## 3. Manager self-update points at this repo

### Tests first

Add `app/src/test/java/app/morphe/manager/util/ManagerUpdateUrlsTest.kt`:

- `MANAGER_RELEASE_JSON_URL` contains `adnanXmacro/Scroll-freedom` and `app-release.json`.
- `MANAGER_PRERELEASE_JSON_URL` contains `adnanXmacro/Scroll-freedom` (same `main` file until a prerelease branch exists).
- Neither URL contains `MorpheApp/morphe-manager`.

Run (expect fail), then:

`app/src/main/java/app/morphe/manager/util/Constants.kt`

```
MANAGER_RELEASE_JSON_URL = https://raw.githubusercontent.com/adnanXmacro/Scroll-freedom/refs/heads/main/app-release.json
MANAGER_PRERELEASE_JSON_URL = same URL
```

Replace `/workspace/app-release.json` (GitHub raw serves this from `adnanXmacro/Scroll-freedom`) so it describes Facebook+ Manager, not Morphe 2.1.0. Mirror the same content in `facebook-plus-manager/app-release.json` if that copy is still referenced. `download_url` must be a GitHub release asset on `adnanXmacro/Scroll-freedom`, never `morphe-manager-*.apk`. After this pass's debug APK is built and released, update `download_url` to that asset. Until then, `version` can stay the current Manager version with a placeholder download URL that is still on this repo (not Morphe).

`MorpheAPI` already refuses a bad JSON. If an applicationId check is missing on the downloaded APK, add it in the existing update installer path: if the APK's package is not `BuildConfig.APPLICATION_ID`, do not install.

Re-run `ManagerUpdateUrlsTest`.

## 4. Launcher identity: red f+Mgr, no Morph Debug

Configuration / assets. No JVM test required beyond `app_name`.

- `app/src/main/res/values/strings.xml`: `app_name` is already `Facebook+ Manager`. Keep it for debug and release.
- Convert the red `f+Mgr` JPEG into:
  - `ic_launcher_foreground.xml` (or png mipmaps) replacing Morphe's mark
  - wordmark drawables used by `MorpheWordmark` (`ic_morphe_wordmark_letter`, `ic_morphe_wordmark_text`) so HomeHeader shows Facebook+ Manager, not the Morphe M
- `themes.xml` splash still uses `@drawable/ic_launcher_foreground`.
- Do not add a debug `resValue` that suffixes Morph / Debug.
- Leave `applicationIdSuffix = ".debug"`. The Morph Debug look is the old wordmark, not the suffix.

`HomeHeader` can keep calling `MorpheWordmark` if the drawables are replaced. Optionally pass `stringResource(R.string.app_name)` as a text title instead of the wordmark if the JPEG will not convert cleanly to a vector; the launcher icon must still be the red mark.

Copy the JPEG into `facebook-plus-manager/app/src/main/res/` (or `artwork/`) so the build does not depend on `.monkeycode-tmp-files`.

## 5. Facebook+ blue f+ icon at patch time

Before generating mipmaps, inspect the loaded HushFacebook bundle (or its GitHub patch list) for a custom-name / custom-icon option. Morphe's YouTube keys (`Custom branding`, `customName`, `customIcon`, `appIcon=custom`) may not exist on Facebook patches.

- If HushFacebook declares equivalent options, use those keys. Copy the blue `f+` JPEG (crop out FACEBOOK PLUS) into the folder layout that option documents. Default custom name remains `Facebook+`.
- If it only has a display-name option, set `Facebook+` and stop. Do not invent a HushFacebook fork (non-goal). Tell the user the blue f+ icon cannot be applied until HushFacebook exposes an icon option.
- If it has no branding options, name stays stock Facebook; same stop.

Do not enable AMOLED black, Material You, or Disable Play Store updates. Icon files ship inside Manager assets, not as a Facebook APK we distribute.

## 6. Strip Morphe product copy

User-visible strings only. Do not rewrite GPLv3, NOTICE, license screens, or source copyright headers.

`app/src/main/res/values/strings.xml` (English first; other locales can keep Morphe until translated):

- Product name Morphe → Facebook+ Manager
- Exceptions: About / licenses that must say this is a modified Morphe Manager and is not Morphe
- Download instructions "return to Morphe" → Facebook+ Manager
- Update banners "Morphe update available" → Facebook+ Manager
- Patcher toasts that say Morphing / Morphe as the product
- `home_facebook_plus_wrong_apk` as in task 1

Do not mass-edit 40+ locale files in this pass unless a string is referenced without an English fallback.

## 7. Settings cut

Hide, do not delete.

`SettingsPage.kt`

- Keep General (Appearance, Notifications), Patching (Installer, Patcher), Updates, Files (storage, signing key import/export), About.
- Do not add PATCH_OPTIONS or APK_DOWNLOAD_HELPERS rows.

`AppearanceSettings.kt`

- Theme only. Hide `AppIconSelector` (Morphe marks) and "Help translate Morphe".

`UpdatesItems.kt`

- Keep manager prereleases + metered. Hide `GitHubPatSettingsItem` even in expert mode (expert is already off).

`FilesSection.kt`

- Keep storage + key export/import + manager settings backup.
- Hide APK download helpers row.

`SettingsSubpage.kt` / `SubpageMatches`

- PATCH_OPTIONS and APK_DOWNLOAD_HELPERS not searchable / not listed.

About (`AboutSettings.kt`, `CreditsDialog.kt`, manager description string):

- `powered by projectAdnan`
- `Publisher: 8BiT Softworks`
- `RageDoll engine by projectAdnan`
- GPLv3 Morphe Manager + HushFacebook
- Modified Morphe Manager, not Morphe
- About icon uses the new launcher foreground, not Morphe's bitmap

## 8. Build, test, ship

```
cd /workspace/facebook-plus-manager
./gradlew :app:testDebugUnitTest --tests 'app.morphe.manager.domain.facebookplus.*' --tests 'app.morphe.manager.domain.manager.DownloadUrlResolverTest' --tests 'app.morphe.manager.util.ManagerUpdateUrlsTest'
./gradlew :app:assembleDebug
```

Manual on a 64-bit Android 11+ device:

- Launcher says Facebook+ Manager, red f+Mgr, not Morph Debug
- Make Facebook+ shows the bundle version, then Morphe web-search opens the exact APKMirror variant
- Wrong APK refused; same find-APK step
- Patched app label Facebook+, blue f+ icon
- Settings: no Expert, helpers, icon picker, Translate Morphe, GitHub PAT
- About: projectAdnan, 8BiT, RageDoll
- Manager update JSON is Scroll-freedom; tapping update must not fetch `morphe-manager-*.apk`

Publish the debug APK as a GitHub release on `adnanXmacro/Scroll-freedom` and point `app-release.json` `download_url` at that asset.

## Order

1. Dynamic pin + flow tests (TDD)
2. DownloadUrlResolver tests + delete Facebook bypass (TDD)
3. Self-update URLs + app-release.json (TDD)
4. Manager icon / wordmark
5. Facebook+ icon assets + patch options
6. English copy
7. Settings hide + About credits
8. Unit tests, assembleDebug, release JSON

## Done when

A 64-bit phone installs Facebook+ Manager (red f+Mgr), taps Make Facebook+, is shown the HushFacebook version, lands on that APKMirror variant, and gets Facebook+ with the blue f+ icon. Manager updates come only from this repo. Settings and About match the spec.
