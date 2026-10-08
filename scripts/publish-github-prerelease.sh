#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/publish-github-prerelease.sh TAG SIGNED_APK PROFILE TITLE NOTES_FILE TESTED_SOURCE_COMMIT [EXPECTED_CERT_SHA256 VERSION_CODE VERSION_NAME]

This is a manually invoked release gate, never a commit-triggered workflow.
It runs release tests, native tests/lint/unsigned build, verifies the supplied
signed APK with release-preflight, confirms Android sources match the tested
commit, creates a GitHub pre-release and verifies its public APK download hash.
EOF
}

if [[ $# -lt 6 || $# -gt 9 ]]; then usage; exit 2; fi
TAG=$1
APK=$(realpath "$2")
PROFILE=$3
case "$PROFILE" in
  alpha5|alpha6|alpha6.1-preview|alpha7-preview|alpha8-preview|alpha9-preview|alpha9.1-preview|production) ;;
  *) echo 'Unknown release profile.' >&2; exit 2 ;;
esac
TITLE=$4
NOTES=$(realpath "$5")
TESTED_SOURCE=$6
EXPECTED_CERT=${7:-}
VERSION_CODE=${8:-}
VERSION_NAME=${9:-}

ROOT=$(git rev-parse --show-toplevel)
REPOSITORY=rawinstinctart/apkdrop-companion

[[ -f "$APK" ]] || { echo 'Signed APK is missing.' >&2; exit 2; }
[[ -f "$NOTES" ]] || { echo 'Release notes file is missing.' >&2; exit 2; }
[[ -f "$ROOT/scripts/release-preflight.py" ]] || { echo 'Run from the Companion repository.' >&2; exit 2; }
[[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] || { echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT.' >&2; exit 2; }
command -v python3 >/dev/null
command -v gradle >/dev/null
command -v gh >/dev/null

git diff --quiet || { echo 'Working tree has unstaged changes; refusing release.' >&2; exit 2; }
git diff --cached --quiet || { echo 'Index has staged changes; refusing release.' >&2; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo 'Working tree is not clean; refusing release.' >&2; exit 2; }

SOURCE_COMMIT=$(git rev-parse HEAD)
TESTED_SOURCE=$(git rev-parse --verify "${TESTED_SOURCE}^{commit}")
git merge-base --is-ancestor "$TESTED_SOURCE" "$SOURCE_COMMIT" || {
  echo 'Tested Android source commit is not an ancestor of the release target.' >&2; exit 2;
}
git diff --quiet "$TESTED_SOURCE" "$SOURCE_COMMIT" -- app/ || {
  echo 'Android app source differs from the tested source commit.' >&2; exit 2;
}
CURRENT_REPOSITORY=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
[[ "$CURRENT_REPOSITORY" == "$REPOSITORY" ]] || {
  echo "Unexpected repository: $CURRENT_REPOSITORY" >&2; exit 2;
}
gh auth status >/dev/null 2>&1 || { echo 'Existing gh authentication is unavailable.' >&2; exit 2; }

if [[ "$PROFILE" == production ]]; then
  [[ -n "$EXPECTED_CERT" && -n "$VERSION_CODE" && -n "$VERSION_NAME" ]] || {
    echo 'Production profile requires expected certificate, version code and version name.' >&2; exit 2;
  }
fi
if gh api "repos/$REPOSITORY/git/ref/tags/$TAG" >/dev/null 2>&1; then
  echo "Tag already exists: $TAG" >&2; exit 2
fi
if gh api "repos/$REPOSITORY/releases/tags/$TAG" >/dev/null 2>&1; then
  echo "Release already exists: $TAG" >&2; exit 2
fi

WORK_TMP=$(mktemp -d /tmp/apkdrop-release.XXXXXX)
trap 'rm -rf "$WORK_TMP"' EXIT
REPORT="$WORK_TMP/release-preflight.json"

echo 'Running release-preflight Python tests…'
python3 -m unittest discover -s scripts/tests -v

BUILD_VARIANT=Debug
LINT_VARIANT=debug
if [[ "$PROFILE" == alpha6.1-preview || "$PROFILE" == alpha7-preview || "$PROFILE" == alpha8-preview || "$PROFILE" == alpha9-preview || "$PROFILE" == alpha9.1-preview ]]; then
  BUILD_VARIANT=Preview
  LINT_VARIANT=preview
elif [[ "$PROFILE" == production ]]; then
  BUILD_VARIANT=Release
  LINT_VARIANT=release
fi
echo "Running native tests, lint and unsigned $LINT_VARIANT build…"
gradle --no-daemon -I scripts/unsigned-debug.init.gradle \
  ":app:test${BUILD_VARIANT}UnitTest" ":app:lint${BUILD_VARIANT}" ":app:assemble${BUILD_VARIANT}"

python3 - "$ROOT/app/build/reports/lint-results-$LINT_VARIANT.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
errors = sum(1 for issue in root.findall('.//issue') if issue.get('severity') == 'Error')
if errors:
    raise SystemExit(f'Lint error count is {errors}; refusing release.')
print('Lint errors: 0')
PY

PREFLIGHT=(python3 "$ROOT/scripts/release-preflight.py" "$APK" --profile "$PROFILE" --report "$REPORT")
if [[ "$PROFILE" == production ]]; then
  PREFLIGHT+=(--expected-cert-sha256 "$EXPECTED_CERT" --version-code "$VERSION_CODE" --version-name "$VERSION_NAME")
fi
"${PREFLIGHT[@]}"
python3 - "$REPORT" <<'PY'
import json, sys
report = json.load(open(sys.argv[1], encoding='utf-8'))
if report.get('artifactStatus') != 'passed':
    raise SystemExit('Signed artifact did not pass release-preflight.')
print('Signed-artifact release-preflight: PASS')
PY

ASSET_NAME=$(basename "$APK")
ASSET_LABEL="APKDrop Companion signed APK ($PROFILE)"
echo "Creating pre-release $TAG from $SOURCE_COMMIT…"
gh release create "$TAG" "$APK#$ASSET_LABEL" "$REPORT#Release preflight JSON" \
  --title "$TITLE" --notes-file "$NOTES" --prerelease --target "$SOURCE_COMMIT"

RELEASE_JSON="$WORK_TMP/release.json"
gh api "repos/$REPOSITORY/releases/tags/$TAG" > "$RELEASE_JSON"
python3 - "$RELEASE_JSON" "$ASSET_NAME" <<'PY'
import json, sys
release = json.load(open(sys.argv[1], encoding='utf-8'))
assert release.get('prerelease') is True and release.get('draft') is False
asset = next((x for x in release.get('assets', []) if x.get('name') == sys.argv[2]), None)
if asset is None:
    raise SystemExit('Expected APK asset was not attached to the release.')
print('Release:', release['html_url'])
print('APK asset:', asset['browser_download_url'])
print('Asset bytes:', asset['size'])
PY

python3 - "$RELEASE_JSON" "$ASSET_NAME" "$REPORT" <<'PY'
import hashlib, json, os, sys, urllib.request
release = json.load(open(sys.argv[1], encoding='utf-8'))
asset = next(x for x in release['assets'] if x['name'] == sys.argv[2])
expected = json.load(open(sys.argv[3], encoding='utf-8'))['artifact']
request = urllib.request.Request(asset['browser_download_url'], headers={'User-Agent': 'APKDrop-Release-Verify/1'})
with urllib.request.urlopen(request, timeout=60) as response:
    digest = hashlib.sha256()
    size = 0
    while True:
        chunk = response.read(1024 * 1024)
        if not chunk:
            break
        digest.update(chunk)
        size += len(chunk)
actual = digest.hexdigest()
if actual != expected['sha256'] or size != expected['size']:
    raise SystemExit('Public APK download does not match the preflight artifact.')
print('Public APK download verified; SHA-256:', actual, 'bytes:', size)
PY
