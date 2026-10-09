#!/usr/bin/env python3
"""Reconstruct the exact locally signed Alpha 15 APK, without the keystore."""
import base64
import hashlib
import pathlib
import subprocess
import sys

if len(sys.argv) != 4:
    raise SystemExit("Usage: reconstruct-alpha15.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
base,encoded,output=map(pathlib.Path,sys.argv[1:])
delta=output.with_suffix(".patch.zst")
def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(65536),b""):
            h.update(chunk)
    return h.hexdigest()
if sha(base)!="8e25d3c5815781e1bad5e44d95ae35cd364de26ed5ebb10f506077f611203596":
    raise SystemExit("Unexpected tested aligned Alpha 15 base APK")
delta.write_bytes(base64.b64decode(encoded.read_text(encoding="ascii"),validate=False))
try:
    if sha(delta)!="bedb0e64fda72fdc884f131b53f622f4366307eab96f69d4c6c0da352a40be49":
        raise SystemExit("Signed APK delta checksum mismatch")
    subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(delta),"-o",str(output)],check=True)
finally:
    delta.unlink(missing_ok=True)
if sha(output)!="a0bb5148b0fb79bddcbf4592f5d5e27caf19d619a4f011a8968446433eb54286":
    output.unlink(missing_ok=True)
    raise SystemExit("Reconstructed APK differs from original local signing")
print("ALPHA15 BYTE-IDENTICAL ORIGINAL-SIGNED APK VERIFIED")
