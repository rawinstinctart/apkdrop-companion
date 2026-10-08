#!/usr/bin/env python3
"""Reconstruct the exact locally signed Alpha 13 APK, without the keystore."""
import base64
import hashlib
import pathlib
import subprocess
import sys

if len(sys.argv) != 4:
    raise SystemExit("Usage: reconstruct-alpha13.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
base,encoded,output=map(pathlib.Path,sys.argv[1:])
delta=output.with_suffix(".patch.zst")
def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(65536),b""):
            h.update(chunk)
    return h.hexdigest()
if sha(base)!="6f056f1791477210519c3cc5653c4ce9488552738f6c3eaf829f6cd6d47fb6e7":
    raise SystemExit("Unexpected tested aligned Alpha 13 base APK")
delta.write_bytes(base64.b64decode(encoded.read_text(encoding="ascii"),validate=False))
try:
    if sha(delta)!="4b44bc8a3798bde8d186a29f11b5eb27b3ac21346db27605763e921027e0f542":
        raise SystemExit("Signed APK delta checksum mismatch")
    subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(delta),"-o",str(output)],check=True)
finally:
    delta.unlink(missing_ok=True)
if sha(output)!="2794815ae0b58180e9cedc53cd0f450d1c2946c371b40df67797a9c3447789d9":
    output.unlink(missing_ok=True)
    raise SystemExit("Reconstructed APK differs from original local signing")
print("ALPHA13 BYTE-IDENTICAL ORIGINAL-SIGNED APK VERIFIED")
