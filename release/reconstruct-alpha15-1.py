#!/usr/bin/env python3
"""Reconstruct exactly the locally signed Alpha 15.1 APK, without private key material."""
import base64
import hashlib
from pathlib import Path
import subprocess
import sys

BASE_SHA = "f4086e93bce2e6f335e370562ff08f13133fc13293ecff013100bbfacf03d3ec"
PATCH_SHA = "bbd8b33fbcf354a60bacb1ecc7c1af4cb0c40a076a0e1a40068c15201562a38c"
SIGNED_SHA = "b4800e348cd86d18683bdab0e9c33cfcccb01cd7387425d54470b75adf6039f3"

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if len(sys.argv) != 4:
        raise SystemExit("Usage: reconstruct-alpha15-1.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
    base, encoded, output = (Path(arg) for arg in sys.argv[1:])
    if digest(base) != BASE_SHA:
        raise SystemExit("Unexpected tested Alpha 15.1 aligned APK")
    patch = output.with_suffix(".patch.zst")
    patch.write_bytes(base64.b64decode("".join(encoded.read_text(encoding="ascii").split()), validate=True))
    try:
        if digest(patch) != PATCH_SHA:
            raise SystemExit("Alpha 15.1 signed APK patch checksum mismatch")
        subprocess.run(["zstd", "-q", "-d", "--patch-from=" + str(base), str(patch),
                        "-o", str(output)], check=True)
        if output.stat().st_size != 324872 or digest(output) != SIGNED_SHA:
            output.unlink(missing_ok=True)
            raise SystemExit("Reconstructed APK differs from the original locally signed artifact")
    finally:
        patch.unlink(missing_ok=True)
    print("ALPHA 15.1 VERIFIED: exact original-signed APK, signer never uploaded")

if __name__ == "__main__":
    main()
