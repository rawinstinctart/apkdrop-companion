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

## Still open — release gates

1. Sign the candidate in the environment holding the original debug key, using `scripts/sign-existing-debug.sh`. This environment has no original private key; the candidate is intentionally unsigned and is not installable.
2. Physically upgrade Alpha 4.1 to Alpha 5 with the unchanged signer. Check launch, navigation, real catalog/profile data, offline/retry behavior, permission changes, installer cancellation/success and a two-app update round. JVM rendering cannot substitute for these device checks.
3. Produce a separately signed production APK before setting `COMPANION_PACKAGE`, `COMPANION_CERT_SHA256` and `COMPANION_DOWNLOAD_URL`. Debug signing is not production signing. The production App Links gate remains disabled.
4. GitHub-hosted Android CI has previously been blocked by exhausted included minutes. Its quota/billing was not changed; local native validation is the evidence here.

No public beta APK is released by this change. The code and website integration are preparatory until signing and physical acceptance are complete.
