# Update protocol

Trigger phrases (same meaning):

- implement update protocol
- run the update protocol
- swap the patcher
- keep up with upstream

After you have read this file, those phrases mean: do the numbered steps below in order. Do not add features. Do not skip stop rules.

This is Facebook+ Manager, not Morphe. Package `app.eightbitsoftworks.facebookplus.manager`. Never ship Morphe's APK. Never leak Morph in the launcher. Never name HushFacebook in user-facing English copy.

## Stop rules

- GitHub fetch fails: stop, do not guess tags.
- Pins, `libs.versions.toml`, and any local `morphe-patcher` checkout disagree: stop, do not compile.
- Tests fail, Morph in the launcher label, or HushFacebook in English copy: stop, do not release.
- Never ship Morphe's APK.
- Never push `app-release.json` until the new APK is on the GitHub release.
- No extra features in the same pass.

## Steps

1. Read `facebook-plus-manager/upstream-pins.json`.

2. Fetch latest *stable* releases (no draft, no prerelease, no `-dev`) for `SysAdminDoc/HushFacebook`, `MorpheApp/morphe-patcher`, `MorpheApp/morphe-manager` via `gh` or the GitHub API (`/releases/latest`).

3. Parse HushFacebook release notes the same way as `scripts/upstream_keep_up.py` (`Morphe patcher X.Y.Z`, `Facebook A.B.C.D.E`). If the notes omit the patcher stamp, treat it as `unknown`. Classify with `scripts/upstream_keep_up.py`. Print the class set.

4. If the class set is empty: stop, say up to date.

5. If the class set is only `patches-only`: stop. The phone already downloads the `.mpp`. Do not compile.

6. If the class set is only `manager-ui`: stop unless the human asked for Morphe UI/installer fixes.

7. If the class set does not contain `patcher-bump`: stop. Print the classes. Do not compile. If it does contain `patcher-bump`: continue. Ignore `manager-ui` unless the human asked.

8. Bump `facebook-plus-manager/app/gradle.properties` `version` to the next patch (example: `2.1.2` -> `2.1.3`).

9. From `facebook-plus-manager/`, run `./scripts/sync-patcher.sh v<required>` where required is the newer of HushFacebook's `Patcher-Version` and the latest stable morphe-patcher tag. That script writes `gradle/libs.versions.toml` and `upstream-pins.json` `morphePatcher.tag`, and checks out `../morphe-patcher` when that directory exists.

10. Run:

```
# Classifier tests
python3 /workspace/scripts/test_upstream_keep_up.py

# Facebook+ unit tests
cd /workspace/facebook-plus-manager
./gradlew :app:testDebugUnitTest --offline --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusFlowTest' --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusBrandingTest' --tests 'app.morphe.manager.domain.facebookplus.FacebookPlusPinTest' --tests 'app.morphe.manager.domain.manager.DownloadUrlResolverTest' --tests 'app.morphe.manager.domain.manager.ManagerUpdatePolicyTest' --tests 'app.morphe.manager.util.ManagerUpdateUrlsTest'

# Debug APK
./gradlew :app:assembleDebug --offline
```

11. Verify the debug APK at `facebook-plus-manager/app/build/outputs/apk/debug/facebook-plus-manager-<version>-debug.apk`: package `app.eightbitsoftworks.facebookplus.manager.debug`, `versionName` matches `app/gradle.properties`, launcher label `Facebook+ Manager`, no Morph / Morph Debug, English `app/src/main/res/values/strings.xml` does not contain `HushFacebook`.

12. Create GitHub release `facebook-plus-manager-v<version>-debug` on `adnanXmacro/Scroll-freedom` with that APK. Do not upload Morphe's APK.

13. Only after the release asset exists: write `app-release.json` at repo root, `facebook-plus-manager/app-release.json`, and `facebook-plus-manager/app/app-release.json` to that asset URL and version. Commit those three files (and `upstream-pins.json` if hushfacebook fields still need the new tag / patcherVersion / facebookVersion). Push `main`. Raw GitHub is what the phone fetches.

14. Stop. Tell the human the release URL and sha256.
