#!/usr/bin/env python3
"""Rebuild the exactly verified Alpha 12 APK from a public signed delta.

The private keystore stays outside GitHub. Never reconstruct from another base.
"""
import base64
import hashlib
import pathlib
import subprocess
import sys

if len(sys.argv) != 4:
    raise SystemExit("Usage: reconstruct-alpha12.py ALIGNED_APK BASE64_PUBLIC_PATCH SIGNED_OUTPUT")

base, patch_source, output = map(pathlib.Path, sys.argv[1:])
patch = output.with_suffix(".patch.zst")

def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

if sha256(base) != "7bdb2614b4862de3a8481d1a971f54bdc42d176e65453b58f81165d99447e29f":
    raise SystemExit("The aligned Alpha 12 unsigned CI artifact differs; release blocked.")
patch_bytes = base64.b64decode(patch_source.read_text(encoding="ascii"), validate=False)
patch.write_bytes(patch_bytes)
try:
    if sha256(patch) != "1a60c2f0343341540d621e549463c551198cd585ff7ca460aec820f4c0314497":
        raise SystemExit("Signed delta checksum differs; release blocked.")
    subprocess.run(["zstd", "-q", "-d", "--patch-from="+str(base),
                    str(patch), "-o", str(output)], check=True)
finally:
    patch.unlink(missing_ok=True)

if sha256(output) != "2673a83cd65a1badb3e9f6a12a7720122c0a8879155863d7ba9ef9e1993ba504":
    output.unlink(missing_ok=True)
    raise SystemExit("Reconstructed APK differs from locally signed Alpha 12; blocked.")
print("ALPHA12 ORIGINAL-SIGNED BYTE-IDENTICAL RECONSTRUCTION: PASS")
