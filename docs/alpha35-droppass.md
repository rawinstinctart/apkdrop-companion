# Alpha 35: DropPass consumer

Version 35 / 0.1.0-alpha.35 extends only the manual local APK report. Saved apps,
update flows and installation gates are unchanged. Choose an APK under Mehr,
then optionally choose “DropPass (JSON) wählen” in its report. Details expand in
the same report. Cancellation adds no dialog. APK bytes never leave the device;
only passId is sent to the status endpoint after all local checks pass.
The selected APK is reopened, Android signature checked and freshly hashed on
pass selection; private temporary copies are removed in the existing finally path.

## Trust and states

The exact eight envelope fields, v1 schemas, Ed25519 domain prefix, signature,
bundled issuer trust, receipt digest/id, freshly read APK hash and Android package
must match. Unknown/revoked issuer keys are INVALID, distinct from a cryptographically
valid pass revoked by the online service (REVOKED). Issuers must be active and
validFrom <= issuedAt < retiredAt when retiredAt exists. Time is exact UTC with
three millisecond digits. We use **zero clock skew**: issuedAt <= now < expiresAt,
with positive duration <= 30 days. Incorrect device time can therefore yield INVALID.

All local checks run before networking. Any failed local check yields INVALID and
no status request. Locally valid + status ok yields VERIFIED; revoked yields REVOKED
and cannot be treated as approval; unknown, missing service, 404, timeout, malformed
response or offline yields UNKNOWN. Missing pass/APK/registry/local algorithm yields
UNVERIFIABLE_NOW. The report is inspection only and never enables installation.

JSON parsing rejects duplicate keys (including escaped duplicates), trailing data,
invalid syntax, oversized inputs (>256 KiB), depth >48, fractional/exponent numbers,
negative zero and integers outside ±(2^53−1). Receipts use backend-safe integers.
Integer-index object keys are rejected because JS property enumeration can reorder
them even after recursive sorting. This deliberately rejects ambiguous inputs
rather than guessing signature bytes. Other keys sort by UTF-16; JSON strings use
JSON.stringify escapes (including lone surrogate escapes), arrays retain order.
The synthetic backend-signed vectors are the cross-language acceptance oracle.

## Registry sync obligation

`app/src/main/assets/droppass-issuer-keys.json` is a byte-identical copy of
`backend-config/droppass-issuer-keys.json`, which HIOS supplies from the backend.
**Whenever the backend registry changes, update the bundled app asset and ship an
app update.** Never load trust roots from a pass or the network. The asset includes
the isolated staging pilot public issuer only; it contains no private key. Tests
assert schema, active key presence, absence of `d` recursively and exact sync.

## Android Ed25519

Prefer platform `Signature` and `KeyFactory` Ed25519 (Android API 33+, JVM).
If either algorithm is unavailable, explicitly instantiate the vetted Bouncy Castle
JCA provider `org.bouncycastle:bcprov-jdk18on:1.86`. This is the current stable
[upstream Java release](https://www.bouncycastle.org/download/bouncy-castle-java/).
The provider is never registered globally and does not replace Android providers.
Only the missing Ed25519 algorithm uses the fallback on API 26–32; no custom curve
crypto is implemented. The RFC 8410 public-key wrapper is fixed encoding only.
Golden vector tests exercise both platform JCA and forced explicit BC JCA paths.
Physical API 26–32 and API 33+ validation remains part of HIOS acceptance.

## Status origin and pilot

Release and preview always use `https://apkdrop.rawinstinctai.de`.
The production endpoint currently returns 404 with the feature flag disabled:
that honestly displays UNKNOWN, never VERIFIED. Debug builds alone can set:

```
gradle :app:assembleDebug -PdroppassDebugOrigin=https://apkdrop-staging-droppass.rawinstinctart.workers.dev
```

Only that exact staging origin is accepted as override; other values use production.
There are no credentials, auth, cookies or redirects in the anonymous POST. Status
shape is exactly one status field with ok/revoked/unknown. Connect/read each use
1.2s and the caller has a 2.8s total deadline (including DNS), below the 3s contract.
No actual production or staging requests are made by unit tests.

Visible detail footnote: Ein gültiger DropPass belegt Herkunft und Integrität
innerhalb der definierten Vertrauenskette, niemals Malware-Freiheit.
Android-Signatur, frisches Datei-Hashing und die Zustimmung im Android-Installer
bleiben unabhängige Prüfungen.

## HIOS acceptance

Re-run unit tests, assembleDebug and lintDebug with the specified JDK/SDK and
workspace-local `.gradle` cache. Verify picker cancellation, expanding details,
rotation/back navigation, offline/404 UNKNOWN, and a synthetic staging pass against
its matching APK (and matching bundled issuer). The binary vector is synthetic
hash input, not an installable Android APK. Test the real manual inspection on API
26–32 and 33+, including provider availability and fresh APK reads. Confirm saved
apps and update/install flows behave as before. No real FREY data belongs here.

The existing release-preflight CLI supports signed-artifact validation only and
has no unsigned mode. Its `alpha35-debug`/`alpha35-preview` profiles were added with
the alpha 35 release-gate alignment. Run its non-signing Python validation
unit tests; do not run release/signing workflows or create original-signer artifacts.
release-preflight signing stage skipped - original signing only via secure process.

Use the workspace's existing unsigned init script for every Android gate to avoid
any signing/key access:

```sh
export JAVA_HOME=/tmp/apkdrop-companion-build.XA95do/toolchain/jdk-17.0.20.1+1
export ANDROID_HOME=/tmp/apkdrop-companion-build.XA95do/sdk
export GRADLE=/tmp/apkdrop-companion-build.XA95do/toolchain/gradle-8.13/bin/gradle
export GRADLE_USER_HOME="$PWD/.gradle"
"$GRADLE" -I scripts/unsigned-debug.init.gradle :app:testDebugUnitTest
"$GRADLE" -I scripts/unsigned-debug.init.gradle :app:assembleDebug
"$GRADLE" -I scripts/unsigned-debug.init.gradle :app:lintDebug
python3 -m unittest discover -s scripts/tests -p test_release_preflight.py -v
```

Robolectric's writable `user.home` is `.gradle/test-home` (Android SDK artifacts
remain cached there for HIOS). The initial environment used `/nonexistent` and
could not create Robolectric's download lock; the workspace cache resolves it.
The non-signing manifest validation uses cached AGP aapt2 because the supplied SDK
aapt executable is not executable by this workspace user. aapt2's `minSdkVersion`
label is normalized to aapt's `sdkVersion` before calling preflight's
`manifest_facts`; its seven identity/SDK/debug/permission checks pass.

The anonymous status transport fails closed to UNKNOWN if an ambient CookieHandler
is installed; it never changes global networking configuration. Fixed-length POST
streaming and disabled redirects prevent automatic authenticated/replayed requests.

## Changed files

- `app/build.gradle`
- `app/src/main/assets/droppass-issuer-keys.json`
- `app/src/main/java/de/rawinstinctai/apkdrop/DropPassJson.java`
- `app/src/main/java/de/rawinstinctai/apkdrop/DropPassStatusClient.java`
- `app/src/main/java/de/rawinstinctai/apkdrop/DropPassVerifier.java`
- `app/src/main/java/de/rawinstinctai/apkdrop/LocalApkInspector.java`
- `app/src/main/java/de/rawinstinctai/apkdrop/MainActivity.java`
- `app/src/test/java/de/rawinstinctai/apkdrop/DropPassStatusClientTest.java`
- `app/src/test/java/de/rawinstinctai/apkdrop/DropPassVerifierTest.java`
- `app/src/test/java/de/rawinstinctai/apkdrop/LocalApkInspectionUiTest.java`
- `app/src/test/resources/droppass-vectors/apk-fixture.bin`
- `app/src/test/resources/droppass-vectors/expected.json`
- `app/src/test/resources/droppass-vectors/pass-expired.json`
- `app/src/test/resources/droppass-vectors/pass-revoked-key.json`
- `app/src/test/resources/droppass-vectors/pass-tampered.json`
- `app/src/test/resources/droppass-vectors/pass-valid.json`
- `app/src/test/resources/droppass-vectors/public-active.jwk.json`
- `app/src/test/resources/droppass-vectors/receipt.json`
- `app/src/test/resources/droppass-vectors/registry.json`
- `docs/alpha35-droppass.md`

## Final gate evidence (2026-10-15)

All gates use the specified JDK 17.0.20.1+1, SDK and Gradle 8.13 with
`GRADLE_USER_HOME=$PWD/.gradle`, plus the existing unsigned debug init script.

| Gate | Count/result | Exit |
| --- | --- | --- |
| `:app:testDebugUnitTest` (unfiltered) | 243 tests, 0 failed/errors/skipped; 209 existing + 34 added | 0 |
| `:app:assembleDebug` | 1 unsigned debug APK, versionCode 35 / 0.1.0-alpha.35-debug | 0 |
| `:app:lintDebug` | 0 errors, 118 warnings | 0 |
| `python3 scripts/release-preflight.py --help` (run first) | CLI inspected; no unsigned/Alpha 35 profile | 0 |
| Preflight Python unit tests | 38 passed | 0 |
| Non-signing manifest validation | 7 passed | 0 |
| Fixture byte identity | all 9 provided files unchanged | 0 |
| Registry sync | byte-identical; public-only asset test passes | 0 |

Local gate logs and counts: `app/build/reports/alpha35/`. Unit results:
`app/build/reports/tests/testDebugUnitTest/index.html`. Lint:
`app/build/reports/lint-results-debug.html`. Manifest checks:
`app/build/reports/alpha35-manifest-check.json`. All are ignored build artifacts.
The extra report UI test initially needed bounded Handler processing rather than
full animation queue draining; its corrected version and the full suite pass.

Release-preflight signing stage skipped - original signing only via secure process.
No signing scripts, keystores, release workflows, credentials or pushes were used.
Production services, Cloudflare configuration and real FREY data remain untouched.
Only the supplied synthetic fixture passes/APK bytes are committed; test signing
uses ephemeral synthetic keys in memory, without key files or committed secrets.

Remaining acceptance risks: no physical-device or live staging check was performed.
HIOS must verify API 26–32 fallback and API 33+ platform behavior, staging status
with a matching synthetic APK/pass, offline/404 states, and saved-app/update flows.
Registry updates require an app asset update; wrong device time fails closed with
zero skew. Future receipt types outside the documented strict JSON subset require
consumer review before they can verify.
