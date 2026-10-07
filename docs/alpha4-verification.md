# Alpha 4 — Smart Update Center verification

Date: 2026-10-07. Base: Alpha 3 PR #1 head `1e49111250a59375d4801b8f8df3741f7ff4716c`.

## Native checks

An isolated JDK 17.0.20.1, Gradle 8.13, Android SDK 36 and build-tools 35.0.0 were installed under `/tmp/apkdrop-alpha4/tools`. Tests ran through Android Gradle/JUnit, not constructor-only or fake JUnit adapters.

| Test class | Passed | Failed / errors / skipped |
| --- | ---: | --- |
| InstallPolicyTest | 13 | 0 / 0 / 0 |
| ApkSignatureChecksTest | 9 | 0 / 0 / 0 |
| ManifestPermissionsTest | 1 | 0 / 0 / 0 |
| AppLibraryTest | 13 | 0 / 0 / 0 |
| VerifiedApkFilesTest | 5 | 0 / 0 / 0 |
| ReleaseIntelligenceTest | 10 | 0 / 0 / 0 |
| ReleaseSnapshotTest | 9 | 0 / 0 / 0 |
| UpdateQueueTest | 5 | 0 / 0 / 0 |
| UpdateCenterAndroidTest | 16 | 0 / 0 / 0 |
| **Total** | **81** | **0 / 0 / 0** |

The nine signature tests generate and verify real disposable signed APKs and test modified bytes. No signature verification or native installation result is replaced by a successful mock.

The sixteen Android JVM tests use Robolectric 4.17 with real production Activity/controller/store classes, Android resources, SharedPreferences, PackageManager and JobScheduler. They cover last-known restoration, sensitive update sorting, failed snapshots, actual installed-version refresh, removed-entry races, older responses, changed pins, scheduler constraints/disable, corrupt-library preservation, persisted queue/installer state, unsafe links, denied/allowed notifications, cancelled installer status, successful installed-version completion and signer mismatch.

Installer launch is supplied only as an explicit precondition in the queue status tests. The system installer is not actually opened in these JVM tests. A new version is supplied through PackageManager state to verify the consumer behavior; no APK crypto gate is bypassed for an installation.

Native Android view rendering was visually inspected at normal and 1.4× font scale with sample fixtures. This is a JVM rendering check, not a photograph or screenshot of a physical phone. Physical keyboard/system-bar/installer behavior remains part of device acceptance.

`assembleDebug` and `lintDebug` pass. Lint reports 0 errors and 64 warnings (primarily inherited hardcoded text/style/API cleanup). The intentionally required `QUERY_ALL_PACKAGES` permission has a narrowly scoped lint explanation: arbitrary user-pinned packages cannot be represented as static manifest queries. No broad baseline or security-check disabling is used. Explicit Android 12+ cloud and device-transfer exclusions protect local pins and snapshots.

The Alpha 3 APK verifier, apksig adapter, raw manifest permission parser, install policy, immutable cache and read-only provider/installer classes are byte-identical to the verified base. Fresh network-contract revalidation is added around their existing mandatory gate.

## Build and signing

Build an explicitly unsigned debug candidate:

```bash
gradle --no-daemon -I scripts/unsigned-debug.init.gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Output: `app/build/outputs/apk/debug/app-debug-unsigned.apk`.

Artifact identity: `de.rawinstinctai.apkdrop.debug`, versionCode 4, `0.1.0-alpha.4-debug`, minSdk 26, targetSdk 36. The unsigned artifact is **not installable until signed**. ZIP integrity passes. No production key is included.

The local native validation initially also produced a disposable debug-signed build under isolated `ANDROID_USER_HOME`; that certificate is not the established Alpha 3 certificate and that APK is not distributed as an upgrade. Final unsigned output is intentionally rebuilt before handoff.

The established debug signer required for an in-place Alpha 3 upgrade is:

```text
6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e
```

This original private debug key is not available in this execution environment. External signing must use the original key and verify the exact fingerprint, without generating/replacing it. A normal debug build in a fresh environment generates a different key and cannot replace the existing installation.

## Remaining acceptance

Alpha 3 native build evidence remains valid; **Alpha 3 physical device validation is still open**. Alpha 4 does not retroactively complete it. `adb devices` reports no connected devices, and no emulator is configured.

With a correctly signed Alpha 4 APK, verify on a phone:

1. Upgrade Alpha 3 in place; saved pins survive and the actual installed FREY version is read correctly. Keep FREY source unchanged.
2. Restart and confirm last-known labels/date/time, separate error/current/update states, sensitive priority and large-text scrolling.
3. Enable notifications and background checks; verify an actual metadata job, permission denial and disabled scheduling. Android may defer the periodic job; no APK should be downloaded automatically.
4. For a real newer release, explicitly download/verify, cancel Android installation, return and remain on the same item. Complete installation and return: actual PackageManager state must advance the queue. Also check unknown-source settings and process recreation.
5. Verify sensitive target-app permission wording, new/removed permissions, release receipt and baseline attribution. A confirmation never grants Android permissions.
6. Open an APKDrop install link and verify the correct slug. Production verified App Links require the separate release gate below.

## Production App Links gate

Cloudflare Worker `apkdrop` settings were read directly. None of `COMPANION_PACKAGE`, `COMPANION_CERT_SHA256`, `COMPANION_DOWNLOAD_URL` are configured. No settings were changed.

The platform already implements gated `assetlinks.json`, `/install/<slug>` and the web CTA. Activate only after the actual externally signed production package and public APK download exist. Use the actual production package/certificate, not the debug package/certificate. Never put the private key/password in GitHub or Worker vars.

Current deliverable: implemented and natively checked Alpha 4 source plus unsigned debug build. Remaining: original-key signing, physical device acceptance, and eventual production signing/App Links activation. No paid billing settings, Stripe, FREY source, production deployment or automatic installs were changed.
