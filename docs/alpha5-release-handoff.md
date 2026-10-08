# Alpha 5 signing and acceptance handoff

The Alpha 5 app source is in `main`, based on merge `9a27f990d67ec525aa56aa8129588895f4902c74`. VersionCode is 6, versionName `0.1.0-alpha.5-debug`, package `de.rawinstinctai.apkdrop.debug`. The existing native validation is 87 passing tests, successful assembly, 0 lint errors and 37 warnings; see `alpha5-verification.md`. Release-tool checks are separate from those native tests.

## Original key and build

Use only the established Alpha 2/3/4 debug keystore. Its public certificate SHA-256 is `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`. A prior build report located it in `/tmp/apkdrop-companion-build.XA95do/android-user/debug.keystore`; that temporary directory is absent from the current environment. The key cannot be recovered from the signed APK. Do not create a replacement, uninstall the previous app to conceal signer mismatch, or upload private key/password material to Git, reports or Worker variables.

In the environment that retains the original key, check out the reviewed main revision under `/tmp`. Use JDK 17, Gradle 8.13, SDK 36 and build-tools 35.0.0:

```bash
gradle --no-daemon -I scripts/unsigned-debug.init.gradle \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 -m unittest discover -s scripts/tests -v
```

Use the actual source revision and generated APK SHA-256 in the evidence. A rebuild may change the file hash; the previous unsigned hash alone is not provenance for a new build. Do not run normal debug assembly using a newly generated default key for distribution.

Read `APKDROP_KEYSTORE`, `APKDROP_KEY_ALIAS`, `APKDROP_STORE_PASSWORD`, `APKDROP_KEY_PASSWORD` through the established protected environment mechanism. Do not print their values. With `ANDROID_HOME` set and Python 3.11+ available:

```bash
bash scripts/sign-existing-debug.sh \
  app/build/outputs/apk/debug/app-debug-unsigned.apk \
  /tmp/APKDrop-Companion-alpha5-debug.apk
python3 scripts/release-preflight.py /tmp/APKDrop-Companion-alpha5-debug.apk \
  --report /tmp/alpha5-release-check.json
```

The script emits output only after successful cryptographic verification, one original signer, exact package/version, debug flag and SDK checks. An independent recheck returns JSON and exit code 0 for the artifact alone. Exit code 2 is blocked. Existing output/report files are preserved. The checker never signs, installs, changes billing or activates App Links.

## Physical device acceptance remains open

On 8 October 2026, a fresh `adb devices -l` check in this environment found zero devices. No device tests were performed. The artifact checker always leaves physical acceptance open; successful APK verification cannot complete it.

On an authorized real Android device, retain the previous installation and its saved app list. Verify the installed baseline's package, version and signer before upgrading. Upgrade with the original-signed Alpha 5 APK and document:

- Startup and preserved saved apps; all four tabs, detail/back navigation, large text and system/keyboard insets.
- Real Discover, DropID and developer follows; removal after profile unavailability, offline errors and retry.
- Full signer/hash evidence, permission deltas and correct target-app attribution. A permission declaration is not a grant or a safety rating.
- A genuine update: download, local verification, installer cancellation and successful installation. Confirm actual installed version and signer; opening the installer alone is not success.
- A two-app update round, skip/stop, unknown-source settings return and process restart. Keep a cancelled item pending.
- Notification permission allowed/denied and visible background-check opt-out. Background checks must not download APKs.

Record device model/Android version, tested APK hash, installed version before/after and PASS/FAIL/OPEN for each check. Do not mark a case PASS when no matching real update is available. FREY may be used as existing read-only test data; do not alter its source/releases to manufacture a scenario.

## Production is a separate artifact

Externally sign the release variant with a separately managed production key. The production checker fixes package `de.rawinstinctai.apkdrop`, rejects the original debug certificate, rejects debuggable APKs and requires explicit version and certificate values:

```bash
python3 scripts/release-preflight.py /tmp/APKDrop-Companion-production.apk \
  --profile production \
  --expected-cert-sha256 "$APKDROP_PRODUCTION_CERT_SHA256" \
  --version-code 6 --version-name 0.1.0-alpha.5 \
  --report /tmp/production-release-check.json
```

The certificate fingerprint is public verification data, not a private signing key. Only after actual device acceptance, hosted CI resolution and a genuinely public production APK download should `COMPANION_PACKAGE`, `COMPANION_CERT_SHA256` and `COMPANION_DOWNLOAD_URL` be configured. Then check the public assetlinks statement, Android domain verification and website-to-install flow on a real device. No production settings are changed by this handoff.

## Validation of the release tools

17 Python policy/parser/control-flow tests pass. They test fail-closed behavior and are not cryptographic APK fixtures. Separately, Google's actual build-tools verified the existing Alpha 4 APK's v2 signature and original certificate; the release check correctly rejected its versionCode 4 / Alpha 4 name as an Alpha 5 candidate. A modified copy was also checked with the actual apksigner and rejected cryptographically. The original APK was unchanged.

Hosted Android CI remains blocked before build steps. Earlier evidence recorded 2,000/2,000 included minutes, a zero additional budget and Stop usage; those account settings were not changed. This update adds release-tool tests to the workflow but does not claim a successful hosted run.
