## APKDrop Companion Alpha 6 — 0.1.0-alpha.6

**Pre-release / debug build.** This is not a production release. Physical device acceptance remains OPEN.

### Changes
- Native Home dashboard summarizes locally saved apps and known update states.
- Five navigation tabs: Home, Discover, My Apps, Updates and Settings.
- Android Sharesheet import for validated APKDrop links.
- APK downloads can be cancelled; partial and late files are cleaned up and the update queue remains available.
- Home distinguishes blocked and cached update states from fresh checks.

### Verification
- Source commit used for the Android build: `aa80683fbe95693c83856ffc9a3e0e7e498fc49d`.
- Native tests: 98 passed; release-preflight tests: 19 passed.
- Lint: 0 errors, 43 warnings.
- Package `de.rawinstinctai.apkdrop.debug`; versionCode 7; versionName `0.1.0-alpha.6-debug`; minSdk 26; targetSdk 36.
- Exactly one signer; SHA-256 certificate fingerprint: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- APK SHA-256: `4c79c4016dc3b2b91dc17f16e79f08e5d33b8a6659b9c83cc7acbf1693cca15f`.
- Local signed-artifact Alpha 6 release-preflight: PASS.

GitHub Actions did not run job steps because GitHub reported an account payment/spending-limit problem. This release does not claim a passing hosted CI run, physical-device acceptance, production approval or malware certification.
