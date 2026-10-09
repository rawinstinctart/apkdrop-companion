#!/usr/bin/env python3
"""Reconstruct exactly the offline original-signed Alpha 16 APK without private keys."""
import base64
import hashlib
from pathlib import Path
import subprocess
import sys

ALIGNED_SHA = "85cd109fbd71e9562d5e32020f77db42fe1041090192a8ca1c2d29714a7a6f6b"
PATCH_SHA = "e2b4d8ec06c4c370d60c2c0bde0232cff4377e9dd26c09b434aec101e667728d"
SIGNED_SHA = "d4225a61761c25a884d4e9e127862ea78e88383e8a88ceec92d8bfa2d4c72d6f"

def checksum(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if len(sys.argv)!=4:
        raise SystemExit("Usage: reconstruct-alpha16.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
    base,encoded,output=(Path(value) for value in sys.argv[1:])
    if checksum(base)!=ALIGNED_SHA:
        raise SystemExit("Wrong tested and zip-aligned Alpha 16 input")
    patch=output.with_suffix(".patch.zst")
    patch.write_bytes(base64.b64decode("".join(encoded.read_text(encoding="ascii").split()),validate=True))
    try:
        if checksum(patch)!=PATCH_SHA:
            raise SystemExit("Alpha 16 keyless signed patch differs")
        subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(patch),"-o",str(output)],check=True)
        if output.stat().st_size!=324872 or checksum(output)!=SIGNED_SHA:
            output.unlink(missing_ok=True)
            raise SystemExit("Alpha 16 reconstructed APK is not exact locally signed bytes")
    finally:
        patch.unlink(missing_ok=True)
    print("ALPHA 16: exact original-signed bytes verified; key never uploaded")

if __name__=="__main__":
    main()
