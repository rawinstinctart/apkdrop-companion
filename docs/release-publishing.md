# Verified Companion pre-release publishing

The release publisher is deliberately **manual**, not connected to `push`, `pull_request`, or a commit event. It never stores or uploads a signing key or password.

## Gate sequence

1. Build and test the Android source in an isolated environment with JDK 17, Gradle 8.13, Android SDK 36 and Build Tools 35.0.0.
2. Sign locally with the established original Android signing key. Keep that key and all credentials outside GitHub.
3. Record the tested source commit. The release script requires the target commit to contain the same `app/` tree as that tested commit.
4. Run the manual publisher from a clean checkout of this repository:

   ```bash
   scripts/publish-github-prerelease.sh \
     v0.1.0-alpha.N \
     /absolute/path/to/signed.apk \
     alpha6 \
     "APKDrop Companion v0.1.0-alpha.N" \
     docs/releases/alpha6.md \
     <tested-android-source-commit>
   ```

   Choose a profile supported by `scripts/release-preflight.py`. The production profile additionally requires expected certificate SHA-256, version code and version name arguments. Update the profile policy and its tests before adopting a new fixed Alpha profile.

5. The script runs the release Python tests, native JUnit tests, lint, the matching unsigned build and signed APK release-preflight. The `alpha6.1-preview` profile uses Preview tasks; production uses Release tasks; historical debug profiles use Debug tasks. It refuses to publish on any failure, a dirty worktree, changed Android source, existing tag/release, or unauthenticated `gh` session. After creating the pre-release, it downloads the APK without authentication and checks its byte size and SHA-256 against the preflight report.

The temporary preflight/download evidence is created under `/tmp` and removed after the run. The release asset contains the signed APK and its preflight JSON. Device acceptance remains a separately reported gate; this workflow does not claim production approval or malware certification.

## Alpha 6 notes

For Alpha 6, the signed APK was built from source commit `aa80683fbe95693c83856ffc9a3e0e7e498fc49d`. A later release-publishing commit may contain tooling/documentation changes, but the script verifies the `app/` source tree is identical before tagging.

## Alpha 6.1 preview

For public alpha downloads, use the non-debuggable `preview` build and profile `alpha6.1-preview`. Build/sign the versionCode 8 candidate with the existing alpha key outside GitHub, and publish under `v0.1.0-alpha.6.1`. The package and signer stay compatible with the installed Alpha 6; the signed APK preflight additionally enforces the permission allowlist. CI artifacts are unsigned and cannot replace the signed release asset. See [the Play Protect audit and handoff](play-protect.md). No build setting can guarantee the removal of a Google Play Protect warning.
