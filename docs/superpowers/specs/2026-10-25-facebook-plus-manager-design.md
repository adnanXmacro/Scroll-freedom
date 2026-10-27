# Facebook+ Manager

Date: 2026-10-25

## Goal

Ship one Android app, Facebook+ Manager, that turns a user-downloaded Facebook APK into Facebook+: patched Facebook with launcher name Facebook+, original Facebook icon, and HushFacebook's existing in-app toggles (including Reels). Users never pick patches, add sources, or hunt versions.

## Non-goals (v1)

- Writing or forking HushFacebook patches
- Credits inside Facebook / Hushfacebook settings
- Changing the Facebook icon
- Silent download of Facebook from APKMirror or any CDN
- Expert patch picker, YouTube, Reddit, or extra patch sources
- Root Mount / Magisk install
- 32-bit (armeabi-v7a) Facebook
- Play Store listing
- Patching Facebook from inside Facebook

## Two apps

| App | What it is | Who ships it |
|---|---|---|
| Facebook+ Manager | Fork of Morphe Manager, rebranded | We do |
| Facebook+ | Facebook APK patched with HushFacebook defaults | User's device, at patch time |

Facebook+ keeps package `com.facebook.katana`. It is not a new APK we distribute. We never ship Meta's binary.

## Branding

- Manager launcher name: Facebook+ Manager.
- Patched Facebook launcher name: Facebook+.
- Do not use "Morphe" in the product name, logos, or titles (Morphe GPLv3 section 7).
- Icon of Facebook+: unchanged Facebook icon.
- Display name of Facebook+: `Facebook+`, set at patch time via Morphe's existing custom app name option.
- Manager About only: `powered by projectAdnan`, `Publisher: 8BiT Softworks`.
- Keep GPLv3 notices for Morphe Manager and HushFacebook in Manager About / license screens.

## Upstream pins (v1)

These are HushFacebook v0.18.0 pins. Manager must refuse any other file.

- Patch source: `SysAdminDoc/HushFacebook` (GitHub), only source, built in
- Facebook version: `584.0.0.51.56`
- ABI: `arm64-v8a`
- Density: `480dpi`
- Version code: `475817393`
- Package: `com.facebook.katana`
- Device: Android 11+, 64-bit. 32-bit phones get "This phone isn't supported yet."

When HushFacebook releases a new bundle, Manager updates the source and the pin (version / version code / APKMirror URL) together. Do not patch a newly supported Facebook version with an old bundle, or an old APK with a new bundle that dropped that version.

## First-run flow

Home is one screen: greeting plus a primary button **Make Facebook+**. No patch list, no other apps, no source manager.

1. If `com.facebook.katana` is already installed and signed by Meta (or any key that is not this Manager's key): stop. Copy: uninstall Facebook first, then come back. Do not try to install beside it.
2. Else show: "We'll open APKMirror. Tap the blue Download button." Show the same Download button image Morphe Manager already uses. Open the pinned APKMirror file URL, not a search page.
3. Wait for that download. Secondary action: "Already have it? Pick the file."
4. Validate package, version name, version code, and ABI. Wrong file: refuse and reopen the same APKMirror page.
5. Copy: "Working some magic..." Apply HushFacebook recommended/default patches (every patch except `AMOLED black theme`, `Material You theme`, `Disable Play Store updates`). Set display name to `Facebook+`. Do not change icon. Sign with Manager's key. Install.
6. Done: **Open Facebook+**. Once per install, prompt to export the signing key. Without that key, later updates require uninstall and Facebook data is lost.

Patching runs on device. APKs and personal data are not uploaded.

## Updates

Facebook cannot re-patch itself. New HushFacebook releases are applied only by Manager.

- Manager checks the built-in HushFacebook GitHub source (same cadence as Morphe's source updates).
- If the installed Facebook+ was patched with an older bundle: home banner **Update Facebook+**.
- Update reuses the first-run wizard (APKMirror for the new pinned build if the installed Facebook version is no longer the supported one; otherwise re-patch the saved original APK if it still matches).
- Install over the existing Facebook+ with the same signing key so app data stays.

HushFacebook's in-app "check for new Hushfacebook releases" may still notify. That notice must send the user to Facebook+ Manager, not to patch from inside Facebook. v1 does not change HushFacebook code; if the stock notice still says "Morphe Manager", leave it. Document that users update through Facebook+ Manager.

`Disable Play Store updates` stays off (HushFacebook default). Play Store may offer a Facebook update that would overwrite Facebook+. First-run copy: ignore Play Store updates for Facebook; only update through Facebook+ Manager.

## Reels and other toggles

Unchanged HushFacebook settings. No one switch covers all Reels. Users turn them on/off after install:

- Hide Reels in the feed
- Hide the Reels tab (restart)
- Hide the Reels tab dot
- Hide reel interest prompts
- Tap to play, Marketplace only, Events only, ads, download, speed, skip, and the rest of HushFacebook's switches

Switches apply without re-patching. Tab/font changes still need a Facebook restart, as upstream already does.

## Manager product surface (v1)

Keep from Morphe, stripped to Facebook-only:

- Simple-mode patching pipeline, split APK merge, on-device signer, system installer
- APKMirror guide dialog with Download button screenshot
- Signing key export/import
- Patch-source update check, locked to HushFacebook
- About / licenses

Remove from the default UI:

- YouTube, YouTube Music, Reddit, universal "pick any app"
- Adding or removing patch sources
- Expert mode / per-patch picker
- Batch patching of other apps

## Legal and risk

- Meta owns the Facebook APK. Users download it themselves from APKMirror. We do not host or bundle it.
- Meta ToS disallows modifying the official app. Account risk exists. Manager first-run must say this in one short screen before Make Facebook+.
- Morphe Manager and HushFacebook are GPLv3. Shipping Manager means publishing its source. Derivative must use distinct branding (no "Morphe" in the name).
- HushFacebook is GPLv3. We consume their release bundles; we do not relicense them.

## Architecture

```
Facebook+ Manager (fork of MorpheApp/morphe-manager)
  - UI: one-tap wizard
  - Built-in source: SysAdminDoc/HushFacebook
  - Pin: version + versionCode + APKMirror URL
  - Patcher core: unchanged Morphe patcher
  - Signer: Manager's on-device keystore
  - Installer: Android package installer

On device after success:
  Facebook+ = com.facebook.katana, label Facebook+, Meta icon
  Hushfacebook settings live inside that app
```

## Error handling

| Case | Behavior |
|---|---|
| 32-bit device | Block with "This phone isn't supported yet." |
| Stock Facebook installed | Block until user uninstalls |
| Wrong APK | Refuse; reopen pinned APKMirror URL |
| Corrupt / incomplete APK | Refuse; ask to download again |
| Patch failure | Show failure, keep original APK, do not install a half-patched file |
| Three Facebook crash loop | Unchanged HushFacebook pause/safe-mode |
| New Manager key after reinstall | Cannot update; tell user to import the old key or uninstall Facebook+ (data loss) |
| Storage low | Stop before patching; ask for ~2 GB free |

## Testing

- Wizard copy and APKMirror handoff on a 64-bit Android 11+ emulator or device
- Reject a wrong-version APK
- Successful patch of the pinned 584.0.0.51.56 build with default HushFacebook patches
- Launcher label is Facebook+, icon is Facebook's
- Install blocked while stock Facebook is present
- Re-patch with the same key does not wipe Facebook data
- 32-bit / unsupported ABI is refused before download

## Success

A non-technical user installs Facebook+ Manager, taps one button, downloads the file Manager opened, and ends with Facebook+ on the home screen. They can hide Reels from Hushfacebook settings. When HushFacebook ships patches, they tap Update Facebook+ in Manager and keep their login.
