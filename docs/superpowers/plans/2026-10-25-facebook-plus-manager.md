# Facebook+ Manager implementation plan

Spec: `docs/superpowers/specs/2026-10-25-facebook-plus-manager-design.md`

Work in a clone of `https://github.com/MorpheApp/morphe-manager` (GPLv3). Do not clone or fork HushFacebook patches in v1. Consume `SysAdminDoc/HushFacebook` as the only remote patch bundle.

Do not change `patcher-core` unless a compile error forces it. All product changes live in `app/`.

## 0. Clone and baseline

- Clone Morphe Manager into this workspace (replace or sit beside the empty root; keep `docs/superpowers/` from this repo).
- Keep LICENSE. Rewrite README and NOTICE for Facebook+ Manager. NOTICE must still say this is a modified Morphe program and that "Morphe" is not granted as a trademark.
- Confirm the project builds (`./gradlew :app:assembleDebug`) before any product edit.

## 1. Rebrand the Manager APK

Change identity so this is not installable over official Morphe:

| What | From | To |
|---|---|---|
| `applicationId` / `namespace` | `app.morphe.manager` | `app.eightbitsoftworks.facebookplus.manager` (keep Kotlin packages as `app.morphe.manager` in v1 to avoid a mass rename; only applicationId must change) |
| `app_name` | Morphe | Facebook+ Manager |
| `rootProject.name` | morphe-manager | facebook-plus-manager |
| Theme / launcher strings that say Morphe | Morphe | Facebook+ Manager |

Files: `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml` (label already uses `@string/app_name`).

Do not ship Morphe logos as the Manager icon long-term; v1 may keep a distinct non-Morphe color variant of the existing launcher generator rather than designing a new mark.

Remove or disable Firebase (`google-services.json`, `MorpheFcmService`) so we do not reuse Morphe's FCM project. Source updates use GitHub HTTP like Morphe already does without FCM.

## 2. Pin HushFacebook as the only source

Find where Morphe seeds default patch bundles (domain under `app/src/main/java/app/morphe/manager/domain/bundles/`, plus first-run in `ManagerApplication.kt`). Replace default YouTube/Music/Reddit sources with one GitHub source:

- Owner/repo: `SysAdminDoc/HushFacebook`
- Package target: `com.facebook.katana` only

Hide UI to add, remove, or enable other sources. Hide YouTube / Music / Reddit home cards.

Add a single pin object (new file, e.g. `FacebookPlusPin.kt`):

```
packageName = com.facebook.katana
versionName = 584.0.0.51.56
versionCode = 475817393
abi = arm64-v8a
dpi = 480dpi
apkMirrorUrl = <exact file page HushFacebook README names>
displayName = Facebook+
```

When the HushFacebook bundle updates, this pin must update in the same change.

## 3. One-tap home

Replace the multi-app home with one screen:

- If ABI is not `arm64-v8a`: "This phone isn't supported yet." No download.
- If `com.facebook.katana` is installed and not signed with this Manager's key: "Uninstall Facebook first, then come back."
- Else primary button **Make Facebook+**.
- If Facebook+ is already installed with this key: **Open Facebook+** plus **Update Facebook+** when the bundle is newer.
- First launch, before Make Facebook+: one short risk screen (Meta ToS / account risk / ignore Play Store Facebook updates). Must accept once.

Reuse Morphe simple-mode patching, not Expert mode. Force recommended patches (HushFacebook `use: true`; skip AMOLED black, Material You, Disable Play Store updates). Set the existing custom app-name patch option to `Facebook+`. Do not set a custom icon.

## 4. APKMirror handoff and validation

Reuse Morphe's existing APKMirror guide dialog (Download button screenshot already in the app). Copy:

- Title/body: "We'll open APKMirror. Tap the blue Download button."
- Secondary: "Already have it? Pick the file."

Open `FacebookPlusPin.apkMirrorUrl`, not a search page.

After the user returns with a file (download watcher or picker):

1. Reject corrupt / truncated archives.
2. Require package `com.facebook.katana`, versionName and versionCode from the pin, and arm64-v8a.
3. Wrong file: refuse and reopen the same URL.
4. Low storage (~2 GB): stop before patching.

Then show "Working some magic..." and run the existing patcher + signer + system installer. On failure, keep the original APK; do not install a half-patched file.

## 5. Done state and key backup

After a successful install:

- Button **Open Facebook+** (`com.facebook.katana`).
- Once-per-install prompt to export the signing key (Morphe already has Settings → Import & export → Signing key). Explain that losing the key means uninstall and Facebook data loss.
- About screen: `powered by projectAdnan`, `Publisher: 8BiT Softworks`, plus GPLv3 / Morphe / HushFacebook notices. No credits inside Facebook.

## 6. Updates

Keep Morphe's patch-source refresh, locked to HushFacebook.

- If installed Facebook+ was patched with an older bundle: home banner **Update Facebook+**.
- If the saved original APK still matches the pin, re-patch it. If the pin moved to a new Facebook version, run the APKMirror wizard again.
- Install over the existing app with the same key.

Do not add an in-Facebook updater. Do not fork HushFacebook to retarget its "Morphe Manager" notice in v1.

## 7. Strip default UI (hide, don't delete)

Hide from the default UI, leave code in place:

- Expert mode / per-patch picker
- Add patch source / morphe.software add-source deep link
- Batch patching of other apps
- Root Mount install path
- YouTube-only options (Shorts widget, etc.)

This keeps upstream merges possible.

## 8. Tests and checks

Unit-test the pin validator (right APK / wrong version / wrong package / non-arm64).

Manual (device or 64-bit emulator, Android 11+):

- 32-bit / non-arm64 refused before download
- Stock Facebook blocks install
- Wrong APK refused, same APKMirror URL reopened
- Pinned 584.0.0.51.56 patches with defaults
- Launcher label `Facebook+`, Facebook icon unchanged
- Re-patch with the same key keeps Facebook data
- About shows projectAdnan / 8BiT Softworks

Build: `./gradlew :app:assembleDebug` and existing `:app:test` if it still passes after branding.

## Order of work

1. Clone + baseline build
2. applicationId, app name, NOTICE/README, disable FCM
3. HushFacebook-only source + pin
4. Home / risk screen / Make Facebook+
5. APKMirror dialog copy + validator
6. Display name option + recommended patches
7. Done / key export / About
8. Update banner
9. Hide Expert / sources / other apps
10. Tests

## Done when

A 64-bit phone can install Facebook+ Manager, tap Make Facebook+, download the file Manager opened, and get Facebook+ with HushFacebook toggles, without seeing a patch list.
