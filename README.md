# APKDrop Companion

Native Android companion for verified APKDrop installs and updates.

## Alpha 17 — GitHub onboarding and discovery

An original-signed, non-debuggable Android preview with cancellable GitHub onboarding, separately granted private-import capability and a fresh APK confirmation before each private draft. Failed or abandoned consent upgrades restore the previous device connection. Public app descriptions expand with developer attribution, empty profiles/catalogs explain private drafts, and shared GitHub repository roots use the verified public mapping and fresh install contract.

- Preview package: `de.rawinstinctai.apkdrop.debug` (preserved for existing alpha upgrades).
- Version: `0.1.0-alpha.17-preview`, versionCode **23**, Android 8.0+.
- Original alpha certificate: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- Android CI checks debug and preview tests, lint and builds. The release workflow independently verifies the exact signed artifact and its public download; signing keys never enter GitHub.
- Physical-device and real GitHub account acceptance remains open. Roadmap: #31 (onboarding/discovery acceptance), #32 (reliability), #33 (1.0 release gate). This preview does not claim production readiness.

See [changes and physical acceptance](docs/alpha17-experience.md). Historical release notes below describe their respective versions.

## Alpha 7: useful everyday shortcuts (source candidate)

Home now opens the three most relevant saved apps directly, starts the existing guided update overview when updates are known, cancels metadata checks, and offers link import and developer shortcuts. Discover keeps search/category/page/feed choice across rotation, adds reset, manual refresh and name sorting within the current page. Four bounded catalog responses remain readable for up to seven days offline with timestamp and explicit fresh-check requirement. Public display metadata never authorizes an APK download; withdrawn (404/410), malformed or security-invalid live responses clear the cached display.

Following distinguishes unseen releases from explicitly viewed items, offers a new-only view and explicit mark-as-read, and preserves follow removal offline. Read history stays bounded and local. Trust Center now summarizes pending versus completed local APK verification, permissions and blocked reasons. The download labels fresh contract, download and local verification separately. Optional images use a 12 MiB memory cache.

The non-debuggable preview keeps the original alpha package/certificate, now versionCode **9**, `0.1.0-alpha.7-preview`. Preflight defaults to `alpha7-preview`; all historical alpha profiles remain available. Original-key signing, a physical-device upgrade test and publication remain separate steps. See [Alpha 7 acceptance and signing](docs/alpha7-acceptance.md).

## Alpha 6.1: non-debuggable preview (source prepared)

The `preview` build type uses release settings with debugging disabled, retains the installed alpha package `de.rawinstinctai.apkdrop.debug` and pins the original alpha certificate for in-place updates. Its identity is versionCode **8**, versionName `0.1.0-alpha.6.1-preview`. It is unsigned in CI and must be signed in the original-key environment before publishing. The `alpha6.1-preview` preflight rejects debuggable APKs, identity/signature mismatches and unexpected or missing permissions.

This is build hardening, not a guaranteed fix for Google's unknown-developer Play Protect warning. No Google approval or physical-device result is implied. See [the Play Protect audit and HIOS handoff](docs/play-protect.md). The already published Alpha 6 APK remains unchanged.

## Alpha 6: Everyday Experience

Alpha 6 introduces a native Home with live, **locally derived** app/update counts and three clear shortcuts; Android `ACTION_SEND` text/plain handling for APKDrop links; and a visible cancel control while downloading/verifying an APK. Cancelled operations discard their candidate and **never** count as a verified or installed release. The signature/identity checks and Android system installer remain mandatory.

The debug package stays `de.rawinstinctai.apkdrop.debug`, Alpha 6 versionCode **7**, versionName `0.1.0-alpha.6-debug`. Use `--profile alpha6` for the published Alpha 6 APK and `--profile alpha5` for historical Alpha 5 verification. The Alpha 6.1 profiles are `alpha6.1-preview` and `alpha6.1-debug`; the current Alpha 7 default is `alpha7-preview`. The prior original debug signer must still be used for an in-place upgrade. This branch has **not** passed physical Android acceptance, original-key signing, or hosted CI merely by changing source.

See [Alpha 6 acceptance plan](docs/alpha6-acceptance.md). Source development and signing are intentionally separate; no Cloudflare publication is part of this change.

## Alpha 5: native store

Alpha 5 adds separate Entdecken, Meine Apps, Updates and Einstellungen tabs. The catalog uses APKDrop's opt-in Discover API; app cards open the existing fresh-contract installation flow. Updates show a count and keep the guided round's skip/stop controls visible during app review.

Public DropID profiles and developer follows work without an account. Follows use immutable GitHub IDs, stay in private backup-disabled preferences and can be removed even when a profile is no longer public. The release feed shows currently public releases; opening one checks the app's effective default channel. It never silently switches channels or installs from feed metadata.

The APK Trust Center distinguishes publisher/repository evidence, release-contract facts and the actual local APK verification result. Full signer/hash evidence and permission declarations are expandable. App icons and developer screenshots are optional display media; the Android client accepts only APKDrop image routes. The server proxies bounded PNG/JPEG/WebP screenshots only from the app's linked public GitHub repository, without redirects. A missing image or profile cannot authorize installation.

The Alpha 4.1 OEM JobScheduler crash fix from main is preserved. Alpha 5 uses versionCode 6 (`0.1.0-alpha.5-debug` for the debug package).

Signing, real Android device acceptance and production App Links remain separate release gates. See [Alpha 5 verification](docs/alpha5-verification.md). GitHub CI produces an explicitly unsigned candidate and validation reports; sign in the original-key environment with `scripts/sign-existing-debug.sh`. No private signing material is stored here.

The signing script now checks the complete Alpha 5 artifact identity before emitting output. Run the same independent check with Python 3.11+:

```bash
python3 scripts/release-preflight.py APKDrop-Companion-alpha5-debug.apk \
  --profile alpha5 --report alpha5-release-check.json
```

`ANDROID_HOME` must point to an SDK with build-tools 35.0.0. Exit 0 means only the signed artifact passed; physical device acceptance and hosted CI remain open. Reports are created without overwriting previous evidence. For the separately signed production APK, use the explicit production profile described in [the release handoff](docs/alpha5-release-handoff.md). Debug keys and debuggable APKs cannot pass that profile.

## Alpha 4: Smart Update Center

Alpha 4 adds local release explanations, a last-known release cache, metadata-only background checks and an explicit update round. No new runtime dependency, account, Google service, analytics or AI request is used.

- Saved apps sort by new sensitive permissions, ordinary update, blocked/failed check, not installed, unknown, current. Every restored result is labeled as the last known release with a date and time; failed checks cannot masquerade as successful updates.
- Release Radar compares permissions and SDK facts against the actual installed package. Size and ABI changes are compared separately against a previously checked release only when that baseline exists. Developer notes remain attributed to the developer. Contract signer continuity is not presented as completed APK verification.
- Android JobScheduler checks saved pins approximately every six hours when network and battery conditions permit; Android may defer execution. The setting is visible and can be disabled. The job never downloads APKs or enumerates the installed-app inventory. Permission-controlled local notifications deduplicate by versionCode and SHA-256 and open the exact app through an explicit intent.
- An update overview starts a persisted round. Each release is fetched freshly, each download and Android installation remains explicit. Cancelled/failed installs retain the same item; only confirmed PackageManager version and signer continuity advance the round automatically. Skip and stop remain available.
- Before download and again before installer handoff, a fresh contract must still describe the same artifact. The existing full APK cryptographic gate remains mandatory. Cached contracts never authorize installation.
- Saved snapshots are bounded, identity-checked and display-only. Removed entries cannot be recreated by an older job response. Snapshots, queue and pins stay in private preferences with backups disabled.
- The release receipt can be opened from app details. HTTPS install links and notification links open the matching slug; automatic verified web-to-app handling still needs production signing and the existing Worker release gate.

Native validation and remaining signing/device requirements are recorded in [Alpha 4 verification](docs/alpha4-verification.md). Alpha 3 device validation remains explicitly open, independently of the Alpha 4 JVM tests.

## Alpha 3: Meine Apps

Apps are added explicitly after checking an APKDrop link. The local list stores only the slug, display name, package and pinned signer fingerprints; it survives app restarts. It contains up to 50 apps, with no account or cloud sync.

“Alle auf Updates prüfen” checks the saved apps in sequence without downloading APKs. Each row shows its actual installed version and the available release, with separate current, update, not-installed, blocked and failed states. A failed check does not discard other results. Checks can be cancelled.

Details show release notes and list added and removed permission declarations. RECORD_AUDIO is displayed as “Mikrofonzugriff (Audio aufnehmen)”, attributed to the target app. Download confirmation does not grant an Android permission.

Returning from the system installer refreshes the actual installed package state. A cancelled installation remains an install/update candidate; success is not inferred from opening the installer. Package changes also refresh tracked apps while the Companion is visible. The install policy and full APK verification run again before installer handoff.

The list menu can open an installed app or remove it from the list. Removing a saved entry does not uninstall the app. Background checks and update notifications are not part of Alpha 3.

## Verified installation flow

`APKDrop link → release facts → permission delta → explicit download → local verification → Android system installer`

The Companion consumes APKDrop's public `apkdrop.install.v1` contract and verifies the actual downloaded APK before Android is allowed to open it.

Local verification covers:

- exact byte size and SHA-256
- APK cryptographic signature through Google's `apksig`
- exact signer-certificate fingerprint set
- package name and numeric versionCode
- minSdk / targetSdk
- native ABIs parsed from the downloaded APK
- declared permissions
- installed signer continuity for updates
- no downgrade/current-version installation

Signer mismatch, hash mismatch, package mismatch, SDK/ABI mismatch and downgrades fail closed.

Sensitive new permissions are shown before the APK is downloaded. APKDrop does **not** invent a security score.

## Network boundary

The app accepts only APKDrop links and talks only to:

`https://apkdrop.rawinstinctai.de`

Redirects are not followed for the install contract or APK download. The APK URL must be the immutable APKDrop release URL from the verified contract.

## Installation boundary

APKDrop does not silently install or silently update apps.

After local verification, APKDrop hands the read-only verified APK to Android's system installer. Android remains authoritative and the user may need to allow APKDrop as an installation source once.

The verified APK is exposed only through a private, non-exported, read-only ContentProvider. Every completed download has a distinct filename and installer URI, so a subsequent download cannot replace another app's verified bytes. The private cache keeps at most two completed downloads; old candidates may need to be downloaded again.

## Build

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK 36

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

The GitHub Actions workflow tests, lints and builds an unsigned debug candidate and publishes short-lived validation artifacts when runner capacity is available. Current Actions runs are blocked by exhausted included minutes; billing is unchanged. Alpha 3 requires the isolated native build described in [the verification record](docs/alpha3-verification.md).

## Release signing

Release signing is intentionally not configured in source.

Never commit keystore, private signing key, passwords or signing properties.

When the first production APK is externally signed, its certificate SHA-256 fingerprint becomes the public value used by APKDrop's gated `/.well-known/assetlinks.json`.

## App Links

The app declares `https://apkdrop.rawinstinctai.de/install/<slug>` with Android App Links verification. APKDrop keeps `assetlinks.json` disabled until the real production signing certificate and signed Companion APK exist.

## Privacy

APKDrop has no account requirement, analytics identifier, advertising SDK, device fingerprinting or hidden telemetry. It declares `QUERY_ALL_PACKAGES` to compare arbitrary target packages. Package queries are limited to the active install contract and explicitly saved entries; APKDrop does not enumerate or upload the installed-app inventory. The saved list stays in private preferences and backups remain disabled.



## Alpha 12 — Premium Experience

The app detail page now has compact version/status information, native formatted release notes with More/Less, and expandable comparison/evidence sections. DropPilot shows real background-run history, download progress and observed waiting conditions instead of a generic active label. Cached downloads remain bound to pinned app identities and are checked again before installation. See [Alpha 12 acceptance](docs/alpha12-acceptance.md).
