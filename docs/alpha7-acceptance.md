# Alpha 7: Everyday usability

This extends the merged Alpha 6.1 source. It does not change Cloudflare, FREY, production App Links or the mandatory installer/verification policy.

## User-visible behavior

- Home: direct entry to the top three saved apps, including their real status and cached qualifier; known updates open the guided overview; running metadata checks can be stopped. Link import and followed developers are one tap away.
- Discover: reset search/category, refresh manually, sort the current page alphabetically; query/category/page/profile/following choice survives rotation.
- Offline catalog: the last four responses, at most 128 KiB of text each, remain available for up to seven days. Timestamp and stale display status remain visible. Only transport failures retain the displayed cache. Missing/withdrawn (404/410) or malformed live metadata clears it. Pagination/query keys are separate. No cached data is read by ContractClient, APK verification or InstallerHandoff.
- Following: an item is new until explicitly opened or marked read. Version, channel, publication and artifact hash distinguish releases. Names do not reset read status. History is capped at 600 local digests. This is a read status, never installed/verified state. Follow removal works with no network.
- Trust Center: release contract versus completed local APK verification, sensitive permission changes and blocked reason appear without expanding raw proofs. A new release, cancellation or failure cannot retain the verified status.
- Download: separate fresh-contract, percent/total-size download, and indeterminate local verification stages. Cancel remains available. No speculative ETA or silent install.
- Optional display images use a 12 MiB memory LRU; no new dependency, permission, analytics or account.

## Automated acceptance

Run the release/parser/publisher tests and both Android build variants:

```bash
python3 -m unittest discover -s scripts/tests -v
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -I scripts/unsigned-debug.init.gradle
gradle --no-daemon :app:testPreviewUnitTest :app:lintPreview :app:assemblePreview
```

`EverydayExperienceTest` covers query/expiry isolation, cache and history bounds, explicit unread semantics, channel/version differences, Home link input, state restoration, sorting and verification-stage text. Existing install, cancellation, signature, library and update lifecycle tests must still pass.

## Original-key artifact handoff

Use an isolated `/tmp` checkout at the exact tested commit in HIOS, JDK 17, Gradle 8.13, Android SDK 36 / Build Tools 35.0.0. Keep the existing alpha key and secret environment variables; record keystore hashes before/after signing without revealing secrets.

```bash
scripts/sign-existing-debug.sh \
  app/build/outputs/apk/preview/app-preview-unsigned.apk \
  /tmp/APKDrop-Companion-alpha7-preview.apk alpha7-preview
python3 scripts/release-preflight.py \
  /tmp/APKDrop-Companion-alpha7-preview.apk --profile alpha7-preview \
  --report /tmp/alpha7-preview-check.json
```

Pinned identity: `de.rawinstinctai.apkdrop.debug`, versionCode `9`, `0.1.0-alpha.7-preview`, debugging disabled, original alpha certificate `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`. Debug builds use `alpha7-debug` and cannot be published by the manual publisher. Do not replace existing Alpha 6 or 6.1 assets.

## Physical Android acceptance: OPEN until actually performed

Upgrade the installed alpha without uninstalling; verify saved apps and follows persist. Test Home direct app/updates/link/follow shortcuts, rotation, font scaling, search/reset/sort/next page, and refresh. Load Discover online, then disable networking and reopen the same query/page; see dated cached cards and a retry control. Attempting an app action must still require a fresh contract. Check another uncached query has an honest error state. Restore networking and refresh.

Follow a developer, view a feed release, refresh, switch to new-only and mark all read. A changed version/channel must return as new. Offline follow removal must remain usable. Start/cancel/retry a download; pending trust status must never be displayed as verified. Finish verification, open installer, cancel in Android, and return: read/verified must not imply installed. Switch apps and confirm trust/proofs reset. Play Protect stays enabled; no claim about its warning outcome follows from a successful build.

Signed APK creation and public download checks are performed only in the existing original-key environment. CI artifacts are unsigned candidates, not installable releases.
