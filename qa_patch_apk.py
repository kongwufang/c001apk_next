"""Change exactly the binary AndroidManifest versionCode, leaving other APK payloads intact."""
import hashlib
import re
import struct
import sys
import zipfile
from pathlib import Path

SOURCE_HASH = '5a01ac93bca04de8609e7bb2e9108853f33506d15ccc17dd17442b022114948d'
source, dest = map(Path, sys.argv[1:])
assert hashlib.sha256(source.read_bytes()).hexdigest() == SOURCE_HASH

def patch(data):
    data = bytearray(data)
    def u16(i): return struct.unpack_from('<H', data, i)[0]
    def u32(i): return struct.unpack_from('<I', data, i)[0]
    assert u16(0) == 3 and u32(4) == len(data)
    resources = []
    edits = 0
    off = u16(2)
    while off < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, off)
        assert 8 <= header <= size <= len(data) - off
        if kind == 0x0180:
            resources = [u32(i) for i in range(off + header, off+size, 4)]
        elif kind == 0x0102:
            first, stride, count = struct.unpack_from('<HHH', data, off+24)
            assert stride >= 20 and 16+first+stride*count <= size
            for i in range(count):
                p = off + 16 + first + stride*i
                ns, name, raw = struct.unpack_from('<III', data, p)
                if name < len(resources) and resources[name] == 0x0101021b:
                    assert edits == 0 and ns != 0xffffffff and raw == 0xffffffff
                    assert data[p+15] == 0x10 and u32(p+16) == 10011
                    struct.pack_into('<I', data, p+16, 10012)
                    edits += 1
        off += size
    assert edits == 1
    return data

with zipfile.ZipFile(source) as src, zipfile.ZipFile(dest, 'w') as out:
    assert src.namelist().count('AndroidManifest.xml') == 1
    for entry in src.infolist():
        if re.fullmatch(r'META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))', entry.filename, re.I):
            continue
        data = src.read(entry)
        if entry.filename == 'AndroidManifest.xml': data = patch(data)
        out.writestr(entry, data)
