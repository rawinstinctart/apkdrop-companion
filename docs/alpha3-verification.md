# Alpha 3 implementation and build verification

Date: 2026-10-07. **Native build PASS** for source commit `eeaf4c4741d1edcea70e1e116f8fb1dee572e915`, with no source patches. Device validation of the Alpha 3 UI and update flow remains open.

## Native build and uploaded artifact

The user supplied `alpha3-build-evidence.txt` and `APKDrop-Companion-v0.1.0-alpha.3-debug.apk`. HIOS's evidence reports a clean isolated checkout of the exact source commit, BUILD SUCCESSFUL (exit 0), and **41 native JUnit tests passed**, zero failures, errors or skips.

| Test class | Passed |
| --- | ---: |
| ApkSignatureChecksTest | 9 |
| AppLibraryTest | 13 |
| InstallPolicyTest | 13 |
| ManifestPermissionsTest | 1 |
| VerifiedApkFilesTest | 5 |

The nine signature tests used real disposable signed APK fixtures and apksig v2 verification/tampering cases. HIOS reports `apksigner verify --verbose --print-certs` PASS, APK Signature Scheme v2, one signer, and an unchanged original debug keystore. No patches were needed.

The uploaded bytes were independently checked here:

- Size: **281,268 bytes**.
- SHA-256: `be7003fe4e7ea0a04b90b1a7afdc14d043acaeff41699631881d4b8caa817fed`.
- ZIP integrity passed; no duplicate entry names.
- Binary AndroidManifest.xml parsed: package `de.rawinstinctai.apkdrop.debug`, versionCode 3, versionName `0.1.0-alpha.3-debug`, minSdk 26, targetSdk 36.
- The v2 signing block contains one signer whose embedded certificate SHA-256 equals `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- Companion declares INTERNET, REQUEST_INSTALL_PACKAGES and QUERY_ALL_PACKAGES; it does **not** declare RECORD_AUDIO. The microphone warning describes the target app's declaration.

The independent embedded-certificate comparison is not a second full cryptographic signature verification; the apksigner PASS and native test results are attributed to the supplied HIOS evidence. The APK is a debug build. No Alpha 3 device test result has yet been supplied.

## Implemented behavior

- Explicit local “Meine Apps” list, capped at 50 apps, persisted in private preferences with a versioned and bounded codec. Corrupt stored data disables writes rather than silently resetting the list.
- Saved slug, package and complete signer set are checked on every later release fetch. Duplicate packages cannot be saved under a second slug. The contract client also requires its returned slug to match the requested one.
- Sequential, cancellable checks of all saved apps. APKs are not downloaded during batch checks. Independent per-app failures, blocked identities and unknown states remain distinct from successful current/update results. Remote results are not persisted as current across process restarts.
- Actual installed versus available version, installed app icons, release notes, added and removed permission declarations, and the existing German microphone explanation.
- “App öffnen” and explicit list removal; removal does not uninstall the app.
- Actual PackageManager state is refreshed on resume and on relevant package broadcasts while visible. Installer launch does not imply success. The pending slug survives process death; a recreated activity fetches a fresh contract and reads actual installed state.
- Fresh policy and full cryptographic APK verification immediately before installer handoff. A cancelled installer may reuse the existing verified candidate; current/blocked states hide the install action.
- Separate immutable download names and read-only installer URIs. Retention keeps one previous completed APK before starting another download, bounds completed files to two, and removes orphaned partial downloads. Another download cannot replace the bytes behind an existing URI.
- Alpha 3 UI and versionCode 3 / versionName `0.1.0-alpha.3`; debug suffix remains `-debug`.

No new account, cloud list, app inventory scan, background scheduler, notification permission, dependency or backend change. The production crypto gate, raw manifest permission comparison and install policy are unchanged.

## Checks performed here

| Check | Result | Scope |
| --- | --- | --- |
| AppLibraryTest | 13 passed | Actual committed test methods: codec round trips, identity pins, bounds, malformed/truncated/duplicate data, immutable collections and removal |
| InstallPolicyTest | 13 passed | Existing actual policy test methods; constructor-only platform/JSON DTO adapters |
| VerifiedApkFilesTest | 5 passed | Actual test methods: unique names, path/partial rejection, immutable bytes, owned-file cleanup, retention and missing-cache rejection |
| Activity/controller/store checks | 13 passed | Actual production orchestration with temporary headless Android and contract API adapters |
| Java syntax | Passed | All available production and test Java files parsed by the JDK compiler |
| Layout resources | Passed | Both layout XML documents parsed; every production Java R.id reference resolves |

The first three rows ran through a small JVM assertion runner with temporary JUnit annotation/assertion adapters, **not Android Gradle/JUnit**. They total 31 passing test methods. The temporary constructor-only DTOs preserve the constructors used by the policy tests. These adapters and runners are not included in the repository.

The thirteen orchestration checks cover current-version action hiding, explicit saving and duplicate prevention, list restoration after activity recreation, cancelled installer return, actual version change after installer return, a fresh installed-signer mismatch before handoff, independent batch failure, pinned release signer change, corrupt preferences preservation, ignoring unrelated broadcasts, foreground tracked-package refresh and receiver unregistration, and added/removed permission wording.

The headless adapters for APK download, cryptographic verification and installer handoff throw if invoked. No successful signature result or successful native installation was mocked. A candidate file was supplied only as an explicit precondition for the installer-return UI checks. These checks do not verify Android rendering, actual SharedPreferences disk persistence, platform URI grants or the system installer.

A simultaneous local JVM invocation failed in HotSpot PerfMemory setup because processes shared the performance-data file. Re-running the affected orchestration checks with `-XX:-UsePerfData` passed all thirteen; no source or assertions changed.

## Reproducing the verified native build

HIOS reused `/tmp/apkdrop-companion-build.XA95do` with Temurin JDK 17.0.20.1, Gradle 8.13, SDK 36 and Build Tools 35.0.0. Caches and temporary files remained isolated. The original debug keystore digest matched before and after the build. To reproduce the exact source build, run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
```

The source contains **41 JUnit test methods**: 13 policy, 9 real signed-fixture/tampering, 1 manifest adapter, 13 app-list and 5 cache tests. All 41 passed in the supplied native build evidence. The nine apksig tests use real disposable signed APK fixtures.

Expected artifact identity: `de.rawinstinctai.apkdrop.debug`, versionCode 3, `0.1.0-alpha.3-debug`, minSdk 26, targetSdk 36. Expected debug signer SHA-256:

```text
6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e
```

The supplied APK and evidence match this source commit without any source patch. Any future build with a source patch must identify that patch separately.

The previously supplied 23-test Alpha 2 build belongs to `6d2644b09131ed232ba3b1e3140d0c36440c5ad8` plus the committed readAllBytes compatibility fix. It does not validate Alpha 3. Its provenance and earlier user-reported successful FREY installation remain in [the Alpha 2 record](alpha2-verification.md).

## Alpha 3 device checks still open

1. Update Companion Alpha 2 to Alpha 3 with the same signer. Check the screen at the device's text size, status bar, keyboard and scroll positions.
2. Check installed `frey-messenger`, explicitly add it once, restart Companion and confirm the list survives. Same installed version should show AKTUELL without an install action.
3. Check all saved apps. Verify available versions, unknown/error states, one unavailable release alongside another successful result, and cancellation without late results overwriting a new selection.
4. Verify release notes and that the sensitive warning names FREY and describes microphone access without granting a permission.
5. For a genuine newer release, cancel the Android installer and return: the app must remain an update candidate. Complete installation and return: actual installed version changes and the install action disappears. Also check return from the unknown-source settings screen.
6. Test fresh Android installer access through the unique read-only provider URI, a second app's download, list removal without uninstalling, and restoration after Companion process recreation.

GitHub-hosted CI remains blocked by exhausted included Actions minutes and a $0 additional budget. PR remains draft; no merge, release, billing, production signing, Cloudflare, Stripe or FREY source change.
