#!/usr/bin/env python3
"""Reconstruct exactly the offline original-signed Alpha 16.1 APK without private keys."""
import base64
import hashlib
from pathlib import Path
import subprocess
import sys

ALIGNED_SHA = "376e34696d8632250f7799a8e922a7690639c717fec9f4654a7dca93a8c62d64"
PATCH_SHA = "84ebbf3a8f945ad08f3f488d6b1d7d568f8d74d08cc37f6309a3d038b4f0bfa2"
SIGNED_SHA = "c89676f8416c8e49d4b7c623de5007d853c70daaea9e6db80f7138999549c9a8"

def checksum(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if len(sys.argv)!=4:
        raise SystemExit("Usage: reconstruct-alpha16-1.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
    base,encoded,output=(Path(value) for value in sys.argv[1:])
    if checksum(base)!=ALIGNED_SHA:
        raise SystemExit("Wrong tested and zip-aligned Alpha 16.1 input")
    patch=output.with_suffix(".patch.zst")
    patch.write_bytes(base64.b64decode("".join(encoded.read_text(encoding="ascii").split()),validate=True))
    try:
        if checksum(patch)!=PATCH_SHA:
            raise SystemExit("Alpha 16.1 keyless signed patch differs")
        subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(patch),"-o",str(output)],check=True)
        if output.stat().st_size!=329167 or checksum(output)!=SIGNED_SHA:
            output.unlink(missing_ok=True)
            raise SystemExit("Alpha 16.1 reconstructed APK is not exact locally signed bytes")
    finally:
        patch.unlink(missing_ok=True)
    print("ALPHA 16.1: exact original-signed bytes verified; key never uploaded")

if __name__=="__main__":
    main()
