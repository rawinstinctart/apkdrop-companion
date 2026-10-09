#!/usr/bin/env python3
"""Reconstruct exactly the offline original-signed Alpha 16 APK without private keys."""
import base64
import hashlib
from pathlib import Path
import subprocess
import sys

ALIGNED_SHA = "d7af328be64c61541667636d7cc92c3d2d8d6cf7fc34e59b1d397203e9d357e4"
PATCH_SHA = "31490eeb411f5b599ee46ab51cc0c58985a7cf073cb20d22d3f1a4aade36c8d5"
SIGNED_SHA = "66936357636f924db01fbfa1b89db09d18a95756c08c1a3d07bf33af81fbd282"

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
