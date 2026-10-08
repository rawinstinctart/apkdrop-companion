#!/usr/bin/env bash
set -euo pipefail
# Run only in the environment that owns the original debug key. Never commit it.
: "${APKDROP_KEYSTORE:?Path to the original debug keystore required}"
: "${APKDROP_KEY_ALIAS:?Original key alias required}"
: "${APKDROP_STORE_PASSWORD:?Original store password required}"
: "${APKDROP_KEY_PASSWORD:?Original key password required}"
: "${ANDROID_HOME:?Android SDK required}"
candidate="${1:?Unsigned candidate APK required}"
output="${2:?Signed output APK path required}"
# Preserve the historical two-argument Alpha 6 call; new previews select their pinned profile.
profile="${3:-alpha6}"
case "$profile" in
  alpha5|alpha6|alpha6.1-debug|alpha6.1-preview|alpha7-debug|alpha7-preview) ;;
  *) echo 'Only original-key alpha profiles are supported.' >&2; exit 2 ;;
esac
build_tools="${APKDROP_BUILD_TOOLS:-35.0.0}"
signer="$ANDROID_HOME/build-tools/$build_tools/apksigner"
align="$ANDROID_HOME/build-tools/$build_tools/zipalign"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
test -f "$candidate" && test -f "$APKDROP_KEYSTORE"
test "$candidate" != "$output"
test ! -e "$output"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
"$align" -p -f 4 "$candidate" "$scratch/aligned.apk"
"$signer" sign --ks "$APKDROP_KEYSTORE" --ks-key-alias "$APKDROP_KEY_ALIAS" \
  --ks-pass env:APKDROP_STORE_PASSWORD --key-pass env:APKDROP_KEY_PASSWORD \
  --out "$scratch/signed.apk" "$scratch/aligned.apk"
python3 "$script_dir/release-preflight.py" "$scratch/signed.apk" \
  --profile "$profile" --sdk "$ANDROID_HOME" --build-tools "$build_tools" > "$scratch/release-check.json" || {
  echo 'Release identity or signature check failed; output blocked.' >&2; exit 1;
}
cp "$scratch/signed.apk" "$output"
sha256sum "$output"
echo 'Original debug signature verified. Physical device acceptance remains required.'

