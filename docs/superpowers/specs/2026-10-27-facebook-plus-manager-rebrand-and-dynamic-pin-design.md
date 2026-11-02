# Facebook+ Manager: rebrand, dynamic pin, and Settings cut

Date: 2026-10-27

Supersedes, for the topics below, the 2026-10-25 design at `docs/superpowers/specs/2026-10-25-facebook-plus-manager-design.md`. Unchanged sections of that spec (Reels toggles, legal/risk, one-tap wizard, HushFacebook-only source, no silent APK fetch) still apply.

## Goal

Ship one APK of Facebook+ Manager that:

1. Looks like Facebook+ Manager, not Morphe (launcher, wordmark, debug label).
2. Never installs official Morphe as a self-update.
3. Pins the Facebook APK from the loaded HushFacebook bundle, not a hardcoded version.
4. Opens the exact APKMirror variant the way Morphe already does for YouTube Music.
5. Shows only Settings that Facebook+ actually uses.
6. Credits projectAdnan, 8BiT Softworks, and RageDoll in Manager About only.

A non-technical user taps Make Facebook+, is told the exact unpatched version, lands on that APKMirror file page, downloads it, and ends with Facebook+ on the home screen.

## Non-goals (this pass)

- Writing or forking HushFacebook patches
- Credits inside Facebook / Hushfacebook settings
- Silent download of Facebook from APKMirror or any CDN
- Expert patch picker, YouTube, Reddit, extra patch sources, Root Mount / Magisk
- 32-bit Facebook
- Play Store listing
- Patching Facebook from inside Facebook
- Changing Morphe upstream
- Shipping Meta's APK

## Two apps

| App | Launcher icon | Launcher label | Package |
|---|---|---|---|
| Facebook+ Manager | User-supplied red `f+Mgr` | Facebook+ Manager (debug: same; never "Morph Debug") | `app.eightbitsoftworks.facebookplus.manager` (debug suffix `.debug`) |
| Facebook+ | User-supplied blue `f+` | Facebook+ | `com.facebook.katana` |

Facebook+ is produced on the user's device at patch time. We never ship Meta's binary. Kotlin packages stay `app.morphe.manager`. Unused Morphe UI stays in the tree and is hidden, not deleted.

This pass **overrides** the 2026-10-25 rule "Facebook+ keeps Meta's original icon." Facebook+ uses the blue `f+` icon set at patch time.

Asset sources (user-supplied, this session):

- Manager: red `f+Mgr` on black
- Facebook+: white `f` plus white `+` on Facebook blue, caption FACEBOOK PLUS is not part of the icon

## Pin from the HushFacebook bundle

Do not hardcode Facebook version `584.0.0.51.56`, version code `475817393`, or an APKMirror URL.

At runtime the pin is a snapshot of `AppVersionCatalog` for `com.facebook.katana`:

- Version name: `recommendedVersion("com.facebook.katana")` (newest installable non-experimental target from the enabled HushFacebook bundle)
- Version codes: the bundle's `versionCodes` set for that version, when present. Accept if the APK's version code is in that set. If the bundle lists none, validate name + ABI only. Density (`480dpi` and so on) is not a validator.
- ABI: `arm64-v8a` (unchanged)
- Package: `com.facebook.katana` (unchanged)
- Display name: `Facebook+` (unchanged)
- Source: `SysAdminDoc/HushFacebook` (unchanged)

Until the bundle is loaded, Make Facebook+ waits. Same idea as Morphe waiting on sources. Copy if it never arrives: "Can't make Facebook+ until HushFacebook finishes loading."

When HushFacebook ships a new bundle, Manager's existing source auto-update loads it. The pin and the APKMirror handoff move with that recommended version. Do not patch a newly supported Facebook version with an old bundle, or an old APK with a new bundle that dropped that version.

Saved original APK is reused only when its version still matches the current pin.

## Find APK like YouTube Music

Facebook uses Morphe's existing "Need help finding an original unpatched APK?" step. It must show the bundle version (example: `584.0.0.51.56`) the same way YouTube Music shows `9.20.53`.

Do **not** skip that dialog for Facebook. Do **not** special-case Facebook in `DownloadUrlResolver` with a hardcoded APKMirror listing.

Flow:

1. Gates: 64-bit, ~2 GB free, stock Facebook uninstalled (same-package signature conflict).
2. Dialog: need unpatched APK version `{pin.versionName}`. Primary: "Yes, help me find an APK." Secondary: pick a file.
3. `DownloadUrlResolver.follow()` with `packageName=com.facebook.katana` and `version={pin.versionName}`. That hits `api.morphe.software/v2/web-search/com.facebook.katana~{version}~arm64-v8a` and follows redirects to the exact APKMirror variant page (ABI + dpi already selected), matching Morphe's YouTube Music handoff.
4. User taps APKMirror's Download. Manager waits, or they pick the file.
5. Validate package, version name, ABI `arm64-v8a`, and version code if the bundle listed one. Wrong file: refuse, show the same dialog again, reopen the same resolved URL.
6. Patch with HushFacebook defaults (every patch except `AMOLED black theme`, `Material You theme`, `Disable Play Store updates`). Set display name to `Facebook+` via Morphe's existing custom app name option. Set launcher icon to the blue `f+` via Morphe's existing custom branding / app icon option (the FACEBOOK PLUS caption on the source image is not part of the icon). Sign with Manager's key. Install.
7. Done: **Open Facebook+**. Once per install, prompt to export the signing key.

If the Morphe web-search API times out or returns nothing useful: open a constructed APKMirror **version** URL (`facebook-{version-with-dashes}-release/`) and tell the user to pick the `arm64-v8a` / `480dpi` row, not the first download. That fallback is not the happy path.

Patching stays on device. APKs and personal data are not uploaded.

## Manager self-update

`MANAGER_RELEASE_JSON_URL` and `MANAGER_PRERELEASE_JSON_URL` must point at `adnanXmacro/Scroll-freedom`, not `MorpheApp/morphe-manager`. Until this repo has a prerelease branch, both URLs read the same `app-release.json` on `main`.

In-tree `app-release.json` currently describes Morphe `2.1.0` and downloads `morphe-manager-2.1.0.apk`. Replace it so Manager never offers that APK.

Ship and keep a Facebook+ Manager `app-release.json` on this repo (`version`, `download_url` of our APK, `created_at`, `description`, optional `signature_download_url`). Debug and release builds both read that file.

If the JSON is missing, unreadable, or the APK is not our `applicationId`, do not download or install. Never fall back to Morphe's JSON.

No change to Morphe upstream.

## Branding and copy

- Launcher / splash / in-app wordmark: convert the supplied red `f+Mgr` image into the Manager adaptive icon and wordmark. Replace Morphe's mark. Debug builds use the same mark.
- `app_name` stays `Facebook+ Manager` for debug and release. The Morph Debug look comes from Morphe's wordmark/logo, not from a separate debug string; both go away.
- User-visible "Morphe" becomes "Facebook+ Manager" except:
  - GPLv3 / license / NOTICE
  - About line that this is a modified Morphe Manager and is not Morphe
  - Source copyright headers in code (not shown to users)
- Home, download instructions, patcher toasts, notifications, and Settings strings that name Morphe as the product must be rewritten.

## Settings (v1)

Keep, hide unused rows rather than deleting code:

- Appearance (theme only; hide Morphe icon picker / "how Morphe looks")
- Notifications
- Installer
- Patcher (memory / JVM)
- Manager + HushFacebook updates (URLs are ours)
- Storage / saved APK
- Signing-key export / import
- About

Hide:

- Patch options / Expert mode
- APK download helpers
- Icon picker (Morphe marks)
- YouTube / Reddit / other-app rows
- Add-source / community patches
- Translate Morphe
- GitHub PAT (HushFacebook is a public GitHub source)

## About credits

Manager About only:

- `powered by projectAdnan`
- `Publisher: 8BiT Softworks`
- `RageDoll engine by projectAdnan`
- GPLv3 notices for Morphe Manager and HushFacebook
- This is a modified Morphe Manager and is not Morphe

Nothing inside Facebook+ / Hushfacebook settings.

## Architecture

```
Facebook+ Manager (fork of MorpheApp/morphe-manager)
  - UI: one-tap wizard, Facebook only
  - Built-in source: SysAdminDoc/HushFacebook (auto-update on)
  - Pin: AppVersionCatalog.recommendedVersion(com.facebook.katana)
  - Find APK: Morphe web-search API, same path as YouTube Music
  - Patcher core: unchanged Morphe patcher
  - Signer: Manager's on-device keystore
  - Installer: Android package installer
  - Self-update: app-release.json on adnanXmacro/Scroll-freedom

On device after success:
  Facebook+ = com.facebook.katana, label Facebook+, blue f+ icon
  Hushfacebook settings live inside that app
```

`FacebookPlusPin` is a snapshot built from the catalog, not a bag of constants for version / versionCode / APKMirror URL. Package, display name, ABI, and source repo stay constants.

`DownloadUrlResolver` treats Facebook like any other package.

`FacebookPlusFlow` keeps skip-of-other-apps, storage (2 GB, no patch-anyway), ABI, stock-Facebook, and saved-APK-reuse-on-pin-match. It no longer skips Morphe's version / find-APK dialog.

## Error handling

| Case | Behavior |
|---|---|
| Bundle not loaded / no Facebook versions | Wait; then "Can't make Facebook+ until HushFacebook finishes loading." |
| 32-bit device | "This phone isn't supported yet." |
| Stock Facebook installed | Block until the user uninstalls. No side-by-side. |
| Web-search API timeout | Constructed APKMirror version URL + pick `arm64-v8a` / `480dpi`. |
| Wrong APK | Refuse; same find-APK step; reopen the resolved URL. |
| Corrupt / incomplete APK | Refuse; ask to download again. |
| Patch failure | Show failure; keep original APK; do not install a half-patched file. |
| Storage low | Stop; ask for ~2 GB free; no "patch anyway". |
| New Manager key after reinstall | Cannot update; import the old key or uninstall Facebook+ (data loss). |
| Self-update JSON missing or APK not our applicationId | Do not install. Never Morphe's APK. |

## Testing

- Pin tests take a catalog version (and codes when present); reject other package / version / ABI. No assertion that the pin is hardcoded `584.0.0.51.56`.
- Saved APK reused only when it matches the current pin.
- `DownloadUrlResolver` does not return a hardcoded Facebook APKMirror listing; Facebook uses the same follow/search path as other apps.
- Manager update URLs are `adnanXmacro/Scroll-freedom`; never `MorpheApp/morphe-manager`.
- Settings: hidden extras absent from the default page; About contains projectAdnan, 8BiT Softworks, RageDoll, GPLv3.
- Debug APK launcher label is Facebook+ Manager; icon is red `f+Mgr`.
- Successful patch sets launcher name Facebook+ and blue `f+` icon.
- Stock Facebook still blocks install.
- 32-bit still refused before download.

## Success

User installs Facebook+ Manager, sees Facebook+ Manager (not Morphe / Morph Debug), taps Make Facebook+, is shown the HushFacebook-supported version, lands on that APKMirror variant, patches, and gets Facebook+ with the blue f+ icon. Manager updates come only from this repo. Settings do not offer YouTube, Expert, or Morphe extras. About names projectAdnan, 8BiT Softworks, and RageDoll.
