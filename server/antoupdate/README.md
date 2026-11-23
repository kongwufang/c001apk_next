# Android automatic-update contract

`GET /c001apk/antoupdate/` (exact spelling and trailing slash) serves metadata for the current production-signed Android APK. It is a separate endpoint from `/c001apk/update/` (called `/update/` below). It does not require client headers, credentials, or query parameters; query parameters do not select a release. Updated clients reuse the same first-party metadata request headers and server Cookie handling as `/update/`. On a valid GET, app-shaped requests with a well-formed SSAID or app-random ID are passed to the site's existing `access_record('antoupdate')` before sending the response. Other callers still receive the manifest without entering the shared statistics path. The optional shared library is `$DOCUMENT_ROOT/c001apk/access_lib.php`; its Cookie handling can add the standard top-level `ck` field. The GitHub/APK request uses a separate client and never carries the metadata identity headers or Cookie. Map only the exact public path to [`index.php`](<index.php>). Do not install aliases or redirect legacy routes to it. If using a directory index, configure the web server not to redirect the no-slash variant to the endpoint.

Responses use `Content-Type: application/json; charset=utf-8` and `Cache-Control: no-store`.

**HTTP 200:**

```json
{
  "code": 0,
  "stable": {
    "versionName": "1.0.12",
    "versionCode": 10012,
    "changelog": "Production release notes (plain text or Markdown).",
    "downloadUrl": "https://downloads.example.invalid/c001apk-10012-release.apk",
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "sizeBytes": 123
  }
}
```

- `versionName`: non-blank string from the APK; `versionCode`: positive JSON integer from the APK. A successful response describes the stable release regardless of what version the client has installed.
- `changelog`: UTF-8 string, which may be empty.
- `downloadUrl`: absolute HTTPS URL without embedded credentials or a fragment. Publish a public, direct, versioned APK URL, not a web page or a mutable `latest` URL.
- `sha256`: SHA-256 of the complete APK as exactly 64 hexadecimal characters (either case); `sizeBytes`: exact size of the APK in bytes as a positive JSON integer (at most 512 MiB). These describe the same APK served at `downloadUrl`.

**HTTP 503 (disabled, unconfigured, or invalid config):**

```json
{"code":403,"message":"Automatic update is unavailable."}
```

This response has no `stable` field. No legacy, debug, or other release metadata is substituted. If the handler is invoked on another path it returns HTTP/JSON 404; non-GET on the exact path returns HTTP/JSON 405 with `Allow: GET`.

## Private configuration and publication

1. Use [`config.example.json`](<config.example.json>) as a schema example, not as live metadata. It intentionally starts with `enabled: false` and an invalid placeholder byte count so switching `enabled` alone does not publish it. Set `C001APK_ANTOUPDATE_CONFIG` in the PHP worker environment to the **absolute local path** of a UTF-8 JSON file outside the document root (and outside any public alias). PHP must provide `DOCUMENT_ROOT`, and its worker must be able to read the config but should not be able to write it. The endpoint refuses a missing path, a path resolving within the document root, or a config larger than 64 KiB. Do not put the config or sample in the served tree.
2. Build and verify the production-signed Android APK. Confirm its application ID, signing certificate, and version fields, and derive `sizeBytes` and `sha256` from the final signed bytes. Upload that immutable APK and verify the HTTPS URL is publicly reachable and serves those exact bytes before preparing metadata. The endpoint checks metadata format, not the remote artifact or its signing certificate.
3. Prepare both metadata files from the same production release. Keep the Android `versionName`, `versionCode`, and `changelog` aligned with `/update/`'s Android `stable` metadata. `/update/` retains its existing download-line format and behavior; the new route has one direct APK URL plus size and hash. Publish `/update/`'s Android stable metadata and verify it advertises the intended production release before enabling or advancing the automatic-update manifest. Coordinate both publications as one release step: each file can be replaced atomically, but there is no atomic transaction across the two endpoints.
4. Prepare the complete private config with `"enabled": true` and `"channel": "production"`. Validate it before publication, then atomically rename a complete temporary file in the same private directory over the configured path (using an atomic replacement supported by the host filesystem). Do not truncate or edit the active file in place. After publication, verify both routes advertise the same release and that the new URL, hash, and byte count match the published APK. To turn off automatic updates, atomically replace the config with one where `"enabled": false`; leave `/update/` unchanged. Never repoint this endpoint to a debug/beta build or have it infer metadata from another endpoint, a channel flag supplied by the client, or whichever file was uploaded most recently.
