#!/usr/bin/env python3
"""Reconstruct an already locally signed APK. The signing key is never in GitHub."""
import base64
import hashlib
import pathlib
import subprocess
import sys

if len(sys.argv)!=4:
    raise SystemExit("Usage: reconstruct-alpha11.py ALIGNED_APK BASE64_PUBLIC_PATCH SIGNED_OUTPUT")
base=pathlib.Path(sys.argv[1]); patch_text=pathlib.Path(sys.argv[2]).read_text(encoding="ascii")
output=pathlib.Path(sys.argv[3])
patch=output.with_suffix(".patch.zst")
def sha(path):
    digest=hashlib.sha256()
    with open(path,"rb") as source:
        for chunk in iter(lambda:source.read(65536),b""):
            digest.update(chunk)
    return digest.hexdigest()
if sha(base)!="2ecedd39bcea7f3bc3a23d193769e6f48400d25fed6b88bdb6e3601fcbb71257":
    raise SystemExit("The aligned Alpha11 unsigned artifact differs; refusing publication.")
decoded=base64.b64decode(patch_text,validate=False)
patch.write_bytes(decoded)
if sha(patch)!="733d99101aaebd22438a36e6687574582488e96b3b80109d7e6c66e72ab07ca8":
    patch.unlink(missing_ok=True)
    raise SystemExit("Public signed delta digest mismatch.")
try:
    subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(patch),"-o",str(output)],check=True)
finally:
    patch.unlink(missing_ok=True)
if sha(output)!="d05665a7340eff6dc88c8d3d563f31444477ff2b1e7470b82676051d924895c5":
    output.unlink(missing_ok=True)
    raise SystemExit("Reconstructed APK does not match the original signed preview.")
print("EXACT SIGNED ALPHA11 RECONSTRUCTION: PASS",sha(output))
