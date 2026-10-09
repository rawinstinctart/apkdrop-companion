#!/usr/bin/env python3
"""Reconstruct exactly the locally signed Alpha 15.2 APK; never needs a keystore."""
import base64
import hashlib
from pathlib import Path
import subprocess
import sys

BASE_SHA = "b9ba26540e279f10a87b134941069a37596d409006360308b2cddc36aed5362c"
PATCH_SHA = "5432a896448f4240c0544e1f4bc9ae1fac34e0313424592660222ed97cc67b9b"
SIGNED_SHA = "482f05647140de4462186b74fa700d2639613d50e39be57acc219bb4893c9cf4"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if len(sys.argv) != 4:
        raise SystemExit("Usage: reconstruct-alpha15-2.py ALIGNED_APK BASE64_DELTA SIGNED_APK")
    base, encoded, output = (Path(p) for p in sys.argv[1:])
    if sha(base) != BASE_SHA:
        raise SystemExit("Unexpected tested and aligned Alpha 15.2 base APK")
    patch = output.with_suffix(".patch.zst")
    patch.write_bytes(base64.b64decode("".join(encoded.read_text(encoding="ascii").split()), validate=True))
    try:
        if sha(patch) != PATCH_SHA:
            raise SystemExit("Signed Alpha 15.2 delta checksum mismatch")
        subprocess.run(["zstd", "-q", "-d", "--patch-from=" + str(base), str(patch), "-o", str(output)], check=True)
        if output.stat().st_size != 324872 or sha(output) != SIGNED_SHA:
            output.unlink(missing_ok=True)
            raise SystemExit("Reconstruction differs from exact original-key signed APK")
    finally:
        patch.unlink(missing_ok=True)
    print("ALPHA 15.2 SIGNED APK BYTE-IDENTICAL TO VERIFIED LOCAL SIGNATURE")

if __name__ == "__main__":
    main()
