# Alpha 5 verification — native store

Base: `main` at `04c9b666765209622a48d02a1771af9fe7753b5d` (Alpha 4.1). The OEM JobScheduler hotfix is preserved byte-for-byte. VersionCode is 6, versionName `0.1.0-alpha.5`; the debug package remains `de.rawinstinctai.apkdrop.debug`.

## Implemented

- Persistent bottom navigation: Entdecken, Meine Apps, Updates, Einstellungen. App detail has a separate back action; update-round skip/stop controls remain visible during review.
- Native opt-in Discover catalog, search, categories, deterministic pages, icons and display-only repository screenshots. Catalog/default-channel facts never authorize installation.
- Public DropID profiles, GitHub/repository evidence, local follows keyed by immutable GitHub ID and a public release feed. Removal works when a profile becomes unavailable. No account, cloud sync or hidden telemetry is added.
- Expandable APK Trust Center shows full contract SHA-256, all signer fingerprints, declared permission changes and separate local verification status. Download percentage and local-verification stage are visible.
- Android 13+ back callback, older Android back handling, safe-area/keyboard insets, navigation state restoration and large-text rendering.
- Existing fresh contract checks, full apksig verification, pinning and Android installer confirmation remain authoritative. No silent update or install is claimed.
- Existing-key signing script requires the original debug key and rejects any other signer. CI builds an unsigned candidate and uploads tests/lint reports, with obsolete runs cancelled.

## Validation

Isolated JDK 17 / Gradle 8.13 / Android SDK 36 build:

```
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug \
  -I scripts/unsigned-debug.init.gradle
```

- 87 native JUnit/Robolectric tests; 0 failures, errors or skips. Includes 9 real cryptographic apksig tests, 16 existing Android lifecycle/storage tests and 6 new catalog/navigation/follow/network-boundary tests.
- `assembleDebug`: PASS. Lint: PASS, 0 errors and 37 warnings (remaining primarily dynamic text/localization and existing style/dependency advisories). Fixed XML and status text moved into string resources.
- Actual native view rendering inspected with isolated catalog fixtures at 360 dp and 1.4× font scale. No production data or FREY mutation was used.
- Unsigned candidate: 390,244 bytes; SHA-256 `a2508dab7fee53b3427660aa6f61efd4625a41d8cc16925b91d5e1dee32af5c9`.
- Platform counterpart: 127 Node tests pass. Public store route, publication/profile privacy, bounded media, redirect/SVG blocking and Android intent fallbacks covered.

Proxy settings used to resolve dependencies are temporary environment-specific build settings outside the repository. They are not application configuration.

## Signed debug candidate — HIOS evidence, 8 October 2026

The user supplied `alpha5-final-report.txt` and `alpha5-release-check(1).json` for clean source commit `4c21b61478e75307d27a5eb1152f023f35306e29`. HIOS reports successful assembly, 87 native tests, 17 release-preflight tests and lint with 0 errors / 37 warnings.

- Signed file: `/tmp/APKDrop-Companion-alpha5-debug.apk`, 327,772 bytes.
- SHA-256: `dd64fa7ad4b04c50951d852e7322335a57cfcc8ba5764931e5b1219087bb925d`.
- Package `de.rawinstinctai.apkdrop.debug`; versionCode 6; versionName `0.1.0-alpha.5-debug`; minSdk 26; targetSdk 36.
- Actual apksigner verification: reported PASS, one signer and a valid v2 signature. Certificate SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- Release-preflight: reported exit 0, artifactStatus passed; signature, identity, SDK and artifact-unchanged checks passed.
- Existing Gradle debug signing configuration reused. An external init guard pinned only storeFile to the verified original keystore, retained alias/password configuration, and checked the original file baseline before and after. No replacement key was created.

This resolves the earlier manual-script password-variable blocker through the established Gradle route. The earlier unsigned hashes describe historical builds. These are uploaded HIOS results; the signed APK binary was not attached for independent re-verification here.

The user-provided phone screenshot shows Discover with the real FREY catalog entry and the four navigation tabs. It is visual evidence for that screen, not proof of APK identity, an upgrade, completed installation or update acceptance.

## Still open — release gates

1. Physically upgrade Alpha 4.1 to Alpha 5 with the unchanged signer. Check launch, navigation, real catalog/profile data, offline/retry behavior, permission changes, installer cancellation/success and a two-app update round. JVM rendering cannot substitute for these device checks.
2. Produce a separately signed production APK before setting `COMPANION_PACKAGE`, `COMPANION_CERT_SHA256` and `COMPANION_DOWNLOAD_URL`. Debug signing is not production signing. The production App Links gate remains disabled.
3. GitHub-hosted Android CI has previously been blocked by exhausted included minutes. The new [Alpha 5 run](https://github.com/rawinstinctart/apkdrop-companion/actions/runs/37758642079) also fails before any job steps; GitHub exposes no logs for that job. The exact current failure annotation is unavailable through this connector. Its quota/billing was not changed; local native validation is the evidence here. This is not a green hosted CI result.

Companion PR #3 and platform PR #32 are merged into main. The platform Worker is deployed and its health, Discover, public DropID and native store metadata were checked live. Production App Links and install landing remain gated and return 404.

The original-signed debug candidate is ready according to the supplied HIOS evidence. No public beta APK is published by this documentation update. Physical acceptance, hosted CI resolution and the separate production artifact/App Links gates remain open.
