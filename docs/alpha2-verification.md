# Alpha 2 verification

## Confirmed device path

On 2026-10-07 the user's screenshots showed the sensitive-permission confirmation,
successful local verification of FREY v0.9.1-beta.1, and the Android installation action.
The user subsequently confirmed installation succeeded and the installed app works.
The header no longer overlaps the status bar on that device.

## Update and tampering regression suite

`InstallPolicyTest` covers current numeric versionCode, newer/older versions,
long versionCodes, exact signer sets, package identity, platform compatibility,
and the sensitive-permission delta for updates. Version labels do not decide updates.

`ApkSignatureChecksTest` generates a disposable RSA test key with the JDK's keytool,
signs a ZIP fixture with the existing apksig dependency using APK Signature Scheme v2,
and runs the same file-level verifier used by `ApkVerifierUtil`. Its positive control
must pass before negative outcomes count as evidence. The binary manifest fixture
comes from the uploaded Alpha 2 Companion APK; the ZIP is a signature test fixture,
not an installable app or a FREY release. Test keys and fixtures are temporary and
are deleted after the test class. No production/debug signing key is used by tests.

Negative cases include wrong size/hash, truncation, same-size byte tampering,
tampering with a recomputed hash, an unsigned archive with a correct hash,
an unexpected certificate and an additional expected signer. The genuine fixture
is verified with platform minima 26, 28 and 36.

The existing size/hash/apksig/exact-signer code was moved unchanged into
`ApkSignatureChecks` so the tests invoke the production gate without Android
PackageManager. Archive identity, SDK, ABI, permissions and installed-app checks
remain in `ApkVerifierUtil`; this suite does not claim to exercise those Android APIs.

## Validation status of this test expansion

Locally, the actual 13 policy test methods passed with a lightweight JVM assertion
runner and constructor-only copies of the Android/JSON data holders. The shared
SHA-256 matched the uploaded Alpha 2 APK. Four actual early size/hash rejection
paths passed, including a same-size mutation and truncation of a temporary copy.
The new test sources also passed a syntax check using fail-fast apksig API doubles.
Those doubles never return a successful verification result and are not committed.

The real apksig fixture tests and Android Gradle build have **not** run for this
test-expansion commit in this environment: Android SDK, Gradle and the apksig JAR
are unavailable here. HIOS must run the exact commit with the original isolated
toolchain:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The previous Alpha 2 build's six passing JUnit tests apply to commit
`b1b5d28abea6af92b1a18850c91fc6a59ee38783`, not to this later test expansion.

## Remaining device checks

1. With FREY now installed, reopen Companion, enter `frey-messenger`, and choose
   **Release prüfen**. Expect **AKTUELL**, the already-installed explanation,
   and no download/install action for that version.
2. Test an actual newer release with the same signer in a separate authorized
   test setup. Expect **UPDATE** and an accurate permission delta. No new FREY
   release is created just for this check.
3. Update/downgrade/mismatch device tests, production signing and verified HTTPS
   App Links remain open. The isolated cryptographic tests do not modify APKDrop
   production or distribute a manipulated APK to users.
