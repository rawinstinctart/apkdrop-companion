#!/usr/bin/env python3
"""Reconstruct exactly the offline original-signed Alpha 17 APK without private keys."""
import base64
import hashlib
from pathlib import Path
import subprocess
import sys

ALIGNED_SHA = "359014c994b8fd7b430f537fd7eb567854e93e08ed125383b7ede3ad6609672e"
PATCH_SHA = "8cc7f5a5cacf31af293d5b683fbc12dc4c3b2aa354009edc1b0d6a631585cf51"
SIGNED_SHA = "5424c8092a3ee365efd46559ecf9289db67921832a03cbdb849c03923bd4926d"

def checksum(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if len(sys.argv)!=4:
        raise SystemExit("Usage: reconstruct-alpha17.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
    base,encoded,output=(Path(value) for value in sys.argv[1:])
    if checksum(base)!=ALIGNED_SHA:
        raise SystemExit("Wrong tested and zip-aligned Alpha 17 input")
    patch=output.with_suffix(".patch.zst")
    patch.write_bytes(base64.b64decode("".join(encoded.read_text(encoding="ascii").split()),validate=True))
    try:
        if checksum(patch)!=PATCH_SHA:
            raise SystemExit("Alpha 17 keyless signed patch differs")
        subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(patch),"-o",str(output)],check=True)
        if output.stat().st_size!=333263 or checksum(output)!=SIGNED_SHA:
            output.unlink(missing_ok=True)
            raise SystemExit("Alpha 17 reconstructed APK is not exact locally signed bytes")
    finally:
        patch.unlink(missing_ok=True)
    print("ALPHA 17: exact original-signed bytes verified; key never uploaded")

if __name__=="__main__":
    main()
