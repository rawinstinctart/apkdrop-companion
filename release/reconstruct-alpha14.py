#!/usr/bin/env python3
"""Reconstruct the exact locally signed Alpha 14 APK, without the keystore."""
import base64
import hashlib
import pathlib
import subprocess
import sys

if len(sys.argv) != 4:
    raise SystemExit("Usage: reconstruct-alpha14.py ALIGNED_APK BASE64_DELTA SIGNED_OUTPUT")
base,encoded,output=map(pathlib.Path,sys.argv[1:])
delta=output.with_suffix(".patch.zst")
def sha(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(65536),b""):
            h.update(chunk)
    return h.hexdigest()
if sha(base)!="2988bcae409a9252c6f124091761828aa1aa6e3bbf7f93babef413ebc045917a":
    raise SystemExit("Unexpected tested aligned Alpha 14 base APK")
delta.write_bytes(base64.b64decode(encoded.read_text(encoding="ascii"),validate=False))
try:
    if sha(delta)!="61ccf21cab4ccba8aef2a8addddfd9bbd5fdb84356b0963f67d39f458d24cfbe":
        raise SystemExit("Signed APK delta checksum mismatch")
    subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(delta),"-o",str(output)],check=True)
finally:
    delta.unlink(missing_ok=True)
if sha(output)!="c5d30661aba74148e89812ddc7821148bfb7c72af2ca71ad3adfe557e314e3a0":
    output.unlink(missing_ok=True)
    raise SystemExit("Reconstructed APK differs from original local signing")
print("ALPHA14 BYTE-IDENTICAL ORIGINAL-SIGNED APK VERIFIED")
