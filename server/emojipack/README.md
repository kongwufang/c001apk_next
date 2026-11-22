# Emoji pack release endpoint

`GET /c001apk/emojipack/stable.json` describes the current downloadable emoji pack. The client
checks it once per process start, then downloads the immutable archive from the storage bucket
only when its `version` is newer than the installed one.

```json
{
  "schema": 1,
  "version": 2,
  "url": "https://cdn1.houlangs.cn/c001apk/emojipack/emoji-pack-2.zip",
  "sha256": "f0243208c02ebe5a0775d5c2ce1ddcb22cc11e68ebc71afc81fb28cfd6d59fb9",
  "sizeBytes": 1322866,
  "count": 183,
  "publishedAt": "2026-11-22T06:09:49Z"
}
```

- `url` must be an absolute HTTPS URL to an immutable, versioned archive. The same pack bytes must
  stay reachable for as long as this manifest advertises them.
- `sha256` and `sizeBytes` describe that exact archive. The client rejects a download whose length
  or digest differs, and never activates a pack whose `index.json` fails validation.
- `count` is the number of emoji in the pack; the client shows nothing from it, it is only a sanity
  check for the publisher.
- Serve the file as `application/json` and avoid long-lived caching: the client adds a unique query
  string per check, but a network cache that ignores query strings would delay updates.

## Publishing a new pack

Run from the repository root:

```powershell
python tools/emojipack/build_pack.py --version <n> --upload
python _rev/srv.py put server/emojipack/stable.json <WEBROOT>/emojipack/stable.json
```

`build_pack.py` re-encodes every source image as lossless WebP, verifies the decoded pixels are
unchanged, writes `emoji-pack-<n>.zip`, uploads it to the `houlangs` bucket under
`c001apk/emojipack/`, verifies the public URL byte-for-byte, and only then rewrites
`server/emojipack/stable.json`. Credentials come from the ignored local S3 helper
(`_rev/win_ref/s3v4.py`); they are never part of this repository.

Update `version` monotonically. A client that already runs version `n` ignores any manifest whose
`version` is not greater, so never overwrite an existing versioned ZIP. To restore previous artwork,
build it as a **new higher version** and publish that manifest; moving the manifest backwards will
not revert clients that already upgraded.

## Client policy

- Checks the manifest after onboarding on every process start; a failure keeps the installed pack.
- Downloads automatically on an unmetered network. On a metered network it waits for Wi-Fi unless
  the user chose “流量下也自动下载” during the fourth onboarding page.
- Stores packs in `filesDir/emoji_pack/v<version>/`, activates only after size, SHA-256, index and
  per-file checks pass, and keeps the previous version until the new one is validated.
- Without any pack, text falls back to showing the raw emoji code and the panel shows an empty
  placeholder tab.
