# Play Protect warning and preview handoff

## Observed Alpha 6 warning

On 2026-10-08 the user photographed Google Play Protect blocking the initial APKDrop installation on a Samsung device. The dialog says that Play Protect knows no other apps from this developer and that the app could be unsafe. It offers an install-anyway action.

This establishes a Google Play Protect warning, not a Samsung Auto Blocker diagnosis, a confirmed malware detection, or proof that the debug flag caused the warning. The complete Google classification and device logs are unavailable. Signature and SHA-256 checks authenticate the artifact; they do not certify that an APK is harmless or approved by Google.

Alpha 6 is a debuggable, original-debug-key-signed pre-release. Do not claim that changing a build flag, version, package, certificate or file name will make the warning disappear. Do not instruct users to disable Play Protect or bypass its checks.

## Reviewed permission and installation boundary

The source manifest declares exactly:

| Permission | Purpose in APKDrop |
| --- | --- |
| INTERNET | Fetch APKDrop contracts, catalog metadata and explicitly requested APKs over HTTPS. |
| RECEIVE_BOOT_COMPLETED | Declared for background update scheduling. |
| POST_NOTIFICATIONS | User-controlled local update notifications; not notification-listener access. |
| REQUEST_INSTALL_PACKAGES | Open Android's system installer after explicit download and local APK verification. No silent installation. |
| QUERY_ALL_PACKAGES | Look up exact packages from user-selected contracts or saved entries to compare versions and signing identities. No installed-app enumeration or inventory upload. |

No SMS permission, accessibility service, notification-listener service, device administrator, advertising SDK or analytics dependency is declared. These are source-audit findings, not a malware scan. The signed-artifact preflight now checks the final APK's exact permission allowlist as well as identity, SDK and signature.

## Alpha 6.1 preview build

The new `preview` build type inherits the release build configuration, disables debugging and stays unsigned until signing in the original-key environment. It deliberately retains the installed Alpha package and its original certificate to preserve the upgrade path and private preferences. The package suffix identifies the existing alpha channel; it does not enable debugging.

| Field | Required value |
| --- | --- |
| Build task | `:app:assemblePreview` |
| Package | `de.rawinstinctai.apkdrop.debug` |
| VersionCode | `8` |
| VersionName | `0.1.0-alpha.6.1-preview` |
| Debuggable | `false` |
| Certificate SHA-256 | `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e` |
| Preflight profile | `alpha6.1-preview` |

This remains a pre-release using the original alpha key, not a production release. Production still requires the existing separate package/signing policy. Do not create a replacement key or uninstall Alpha 6 to sidestep a failed upgrade.

## Build, sign and publish in HIOS

Use an isolated checkout under `/tmp`, JDK 17, Gradle 8.13, Android SDK 36 and Build Tools 35.0.0. No HIOS runtime, Gateway, Cloudflare or FREY changes are needed.

```bash
python3 -m unittest discover -s scripts/tests -v
gradle --no-daemon :app:testPreviewUnitTest :app:lintPreview :app:assemblePreview
```

Check and record the existing keystore hash before and after signing. Use the established external Gradle signing guard for `preview`, or the existing signer script with the explicit new profile and existing secret environment variables. Never print passwords or upload private signing material.

```bash
scripts/sign-existing-debug.sh \
  app/build/outputs/apk/preview/app-preview-unsigned.apk \
  /tmp/APKDrop-Companion-alpha6.1-preview.apk \
  alpha6.1-preview

python3 scripts/release-preflight.py \
  /tmp/APKDrop-Companion-alpha6.1-preview.apk \
  --profile alpha6.1-preview \
  --report /tmp/alpha6.1-preview-check.json
```

Record the exact tested commit and artifact SHA-256. On an authorized Samsung device, update Alpha 6 without uninstalling, confirm saved apps/follows, and repeat Home, share import, cancel/download, verification and installer flows with Play Protect enabled. Record the exact new dialog or absence of it. Without this test, device acceptance and Play Protect outcome remain OPEN.

Publish only after the available build/signature gates pass, using the manual publisher with profile `alpha6.1-preview`, tag `v0.1.0-alpha.6.1`, appropriate factual notes and the tested source commit. Never replace the existing Alpha 6 asset or mislabel an unsigned CI candidate as installable. The public download must match the signed artifact's hash.

## Official Google review

Google's [developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance) distinguishes unknown-app scan prompts from a harmful-app classification. Sending an unknown app for a security scan can address the unknown-app prompt; an appeal is not a remedy for that prompt. If an erroneous blocking classification persists after the review, use the official [Play Protect appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals).

The form currently asks for email, package name, the SHA-256 of the APK uploaded to VirusTotal and supporting information. Prepare that evidence for the **exact affected APK**; the APK hash is not the signing-certificate fingerprint. Do not claim that an upload, malware review or appeal has happened until it actually has. No external upload or appeal was submitted as part of this source change.

Known Alpha 6 evidence:

- Source used to build: `aa80683fbe95693c83856ffc9a3e0e7e498fc49d`.
- [Published Alpha 6 release](https://github.com/rawinstinctart/apkdrop-companion/releases/tag/v0.1.0-alpha.6).
- APK SHA-256: `4c79c4016dc3b2b91dc17f16e79f08e5d33b8a6659b9c83cc7acbf1693cca15f`.
- Certificate SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- Reported native tests: 98 passed; preflight tests: 19 passed; lint: 0 errors, 43 warnings.
- Source audit: exact-package queries, explicit download, local hash/signature/package/version verification, Android system installer; no silent installs.

A new preview has a different APK hash. Do not reuse Alpha 6's artifact hash in a review of Alpha 6.1.
