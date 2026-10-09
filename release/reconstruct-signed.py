#!/usr/bin/env python3
"""Reconstruct an already signed public APK. Contains no signing key or key operation."""
import base64
import hashlib
import json
import sys
from pathlib import Path

def reconstruct(unsigned, patch):
    if hashlib.sha256(unsigned).hexdigest() != patch['unsigned_sha256']:
        raise ValueError('Unsigned candidate hash mismatch')
    output = bytearray()
    for operation in patch['operations']:
        if operation[0] == 'copy' and len(operation) == 3:
            start, size = operation[1:]
            if not isinstance(start, int) or not isinstance(size, int) or start < 0 or size < 0 or start + size > len(unsigned):
                raise ValueError('Invalid copy range')
            output.extend(unsigned[start:start + size])
        elif operation[0] == 'data' and len(operation) == 2:
            output.extend(base64.b64decode(operation[1], validate=True))
        else:
            raise ValueError('Invalid operation')
        if len(output) > patch['size']:
            raise ValueError('Output exceeds pinned size')
    if len(output) != patch['size'] or hashlib.sha256(output).hexdigest() != patch['signed_sha256']:
        raise ValueError('Signed APK hash or size mismatch')
    return output

if __name__ == '__main__':
    result = reconstruct(Path(sys.argv[1]).read_bytes(), json.loads(Path(sys.argv[2]).read_text()))
    destination = Path(sys.argv[3])
    with destination.open('xb') as output:
        output.write(result)
    print(hashlib.sha256(result).hexdigest())
