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
expected="6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e"
build_tools="${APKDROP_BUILD_TOOLS:-35.0.0}"
signer="$ANDROID_HOME/build-tools/$build_tools/apksigner"
align="$ANDROID_HOME/build-tools/$build_tools/zipalign"
test -f "$candidate" && test -f "$APKDROP_KEYSTORE"
test "$candidate" != "$output"
test ! -e "$output"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
"$align" -p -f 4 "$candidate" "$scratch/aligned.apk"
"$signer" sign --ks "$APKDROP_KEYSTORE" --ks-key-alias "$APKDROP_KEY_ALIAS" \
  --ks-pass env:APKDROP_STORE_PASSWORD --key-pass env:APKDROP_KEY_PASSWORD \
  --out "$scratch/signed.apk" "$scratch/aligned.apk"
"$signer" verify --verbose --print-certs "$scratch/signed.apk" > "$scratch/verification.txt"
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$scratch/verification.txt" | tr '[:upper:]' '[:lower:]')"
test "$actual" = "$expected" || { echo 'Original signer fingerprint mismatch; output blocked.' >&2; exit 1; }
test "$(sed -n 's/^Number of signers: //p' "$scratch/verification.txt")" = 1
cp "$scratch/signed.apk" "$output"
sha256sum "$output"
echo 'Original debug signature verified. Physical device acceptance remains required.'
