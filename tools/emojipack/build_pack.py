"""Build the downloadable emoji pack and publish its checked release manifest.

Images live outside the APK: `images/` holds the former drawable resources, while the
official Niuniu artwork is kept in the ignored `_rev/emojipack/niuniu/` directory.

    python tools/emojipack/build_pack.py --version 1
    python tools/emojipack/build_pack.py --version 1 --upload

`--upload` needs `_rev/win_ref/s3v4.py` (local credentials, never committed) and writes
`server/emojipack/stable.json` for deployment. Every image is re-decoded after encoding and
compared pixel by pixel, so a lossy or corrupted conversion fails the build.
"""
import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SPEC = Path(__file__).with_name('spec.json')
IMAGES = Path(__file__).with_name('images')
NIUNIU = ROOT / '_rev/emojipack/niuniu'
DIST = ROOT / '_rev/emojipack/dist'
MANIFEST = ROOT / 'server/emojipack/stable.json'
BASE_URL = 'https://cdn1.houlangs.cn/c001apk/emojipack'
BUCKET = 'houlangs'
KEY_PREFIX = 'c001apk/emojipack'


def source(entry):
    name = entry['file']
    for base in (IMAGES, NIUNIU):
        path = base / name
        if path.is_file():
            return path
    raise SystemExit('missing source image for %s: %s' % (entry['code'], name))


def webp(path, entry):
    with Image.open(path) as image:
        original = image.convert('RGBA')
        buffer = io.BytesIO()
        original.save(buffer, format='WEBP', lossless=True, method=6)
    data = buffer.getvalue()
    with Image.open(io.BytesIO(data)) as decoded:
        restored = decoded.convert('RGBA')
    # WebP may normalize the colour of fully transparent pixels; nothing visible may change.
    if restored.size != original.size or visible(original) != visible(restored):
        raise SystemExit('lossless round trip changed %s (%s)' % (entry['code'], path.name))
    return data


def visible(image):
    data = bytearray(image.tobytes())
    for index in range(3, len(data), 4):
        if not data[index]:
            data[index - 3:index] = b'\0\0\0'
    return bytes(data)


def build(version):
    spec = json.loads(SPEC.read_text(encoding='utf-8'))
    # The artwork catalog stores bare names; wire codes must match [name] in App text.
    entries = [dict(e, code='[' + e['code'] + ']') for e in spec['entries']]
    codes = [e['code'] for e in entries]
    if any(not re.fullmatch(r'\[[^\[\]\r\n]{1,46}\]', code) for code in codes):
        raise SystemExit('invalid emoji code in spec.json; enter bare names without brackets')
    if len(set(codes)) != len(codes):
        raise SystemExit('duplicate emoji codes in spec.json')
    groups = []
    for group in spec['groups']:
        members = [e['code'] for e in entries if e['group'] == group['id']]
        groups.append({'id': group['id'], 'title': group['title'], 'codes': members})
    files = {}
    payload = {}
    for entry in entries:
        files[entry['code']] = 'images/' + Path(entry['file']).stem + '.webp'
        payload[entry['code']] = webp(source(entry), entry)
    index = {
        'schema': 1,
        'version': version,
        'count': len(entries),
        'groups': groups,
        'files': files,
    }
    index_bytes = (json.dumps(index, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')
    DIST.mkdir(parents=True, exist_ok=True)
    name = 'emoji-pack-%d.zip' % version
    target = DIST / name
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for arcname, data in [('index.json', index_bytes)] + [
            (files[code], payload[code]) for code in sorted(payload)
        ]:
            info = zipfile.ZipInfo(arcname, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data)
    raw = target.read_bytes()
    return {
        'schema': 1,
        'version': version,
        'url': '%s/%s' % (BASE_URL, name),
        'sha256': hashlib.sha256(raw).hexdigest(),
        'sizeBytes': len(raw),
        'count': len(entries),
        'publishedAt': datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z'),
    }, target, raw


def upload(target, raw, manifest):
    sys.path.insert(0, str(ROOT / '_rev/win_ref'))
    import os
    import s3v4  # local credentials; ignored by git

    # The shared signing helper uses the ambient proxy; this host needs a direct connection.
    os.environ['NO_PROXY'] = '*'

    key = '%s/%s' % (KEY_PREFIX, target.name)
    response = s3v4.request('PUT', '/%s/%s' % (BUCKET, key), body=raw, extra_headers={
        'Content-Type': 'application/zip',
        'Cache-Control': 'public, max-age=31536000, immutable',
    })
    if response.status_code not in (200, 201):
        raise SystemExit('upload failed: HTTP %s %s' % (response.status_code, response.text[:200]))
    import urllib.request
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(manifest['url'], timeout=60) as published:
        if published.status != 200:
            raise SystemExit('published URL returned HTTP %s' % published.status)
        if hashlib.sha256(published.read()).hexdigest() != manifest['sha256']:
            raise SystemExit('published bytes do not match the local pack')
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', type=int, required=True)
    parser.add_argument('--upload', action='store_true')
    args = parser.parse_args()
    if args.version < 1:
        raise SystemExit('version must be positive')
    manifest, target, raw = build(args.version)
    print('pack %s: %d bytes, %d emoji, sha256 %s' % (
        target.name, manifest['sizeBytes'], manifest['count'], manifest['sha256']))
    if args.upload:
        upload(target, raw, manifest)
        print('published', manifest['url'])
        print('wrote', MANIFEST.relative_to(ROOT))


if __name__ == '__main__':
    main()
