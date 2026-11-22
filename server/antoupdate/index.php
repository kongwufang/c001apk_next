<?php

declare(strict_types=1);

const ANTOUPDATE_PATH = '/c001apk/antoupdate/';
const ANTOUPDATE_CONFIG_ENV = 'C001APK_ANTOUPDATE_CONFIG';
const ANTOUPDATE_CONFIG_MAX_BYTES = 65536;

function antoupdateRespond(int $status, array $body): void
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}

function antoupdateLoadStable(): ?array
{
    $configuredPath = getenv(ANTOUPDATE_CONFIG_ENV);
    if ($configuredPath === false || $configuredPath === '' || strpos($configuredPath, "\0") !== false) {
        return null;
    }

    // Read only an absolute local path that resolves outside the web document root.
    $windows = DIRECTORY_SEPARATOR === '\\';
    $absolutePath = $windows ? str_replace('\\', '/', $configuredPath) : $configuredPath;
    if ($windows ? !preg_match('~\A[A-Za-z]:/~', $absolutePath) : $absolutePath[0] !== '/') {
        return null;
    }
    $documentRoot = $_SERVER['DOCUMENT_ROOT'] ?? '';
    $documentRoot = $documentRoot !== '' ? @realpath($documentRoot) : false;
    $configFile = @realpath($configuredPath);
    if ($documentRoot === false || !is_dir($documentRoot) || $configFile === false) {
        return null;
    }

    $root = $documentRoot;
    $file = $configFile;
    if ($windows) {
        $root = strtolower(str_replace('\\', '/', $root));
        $file = strtolower(str_replace('\\', '/', $file));
    }
    if (strpos($file, rtrim($root, '/') . '/') === 0 || !is_file($configFile)) {
        return null;
    }

    $raw = @file_get_contents($configFile, false, null, 0, ANTOUPDATE_CONFIG_MAX_BYTES + 1);
    if ($raw === false || strlen($raw) > ANTOUPDATE_CONFIG_MAX_BYTES) {
        return null;
    }
    $config = json_decode($raw, true);
    if (!is_array($config) || ($config['enabled'] ?? null) !== true || ($config['channel'] ?? null) !== 'production') {
        return null;
    }
    $stable = $config['stable'] ?? null;
    if (!is_array($stable)) {
        return null;
    }

    $versionName = $stable['versionName'] ?? null;
    $versionCode = $stable['versionCode'] ?? null;
    $changelog = $stable['changelog'] ?? null;
    $downloadUrl = $stable['downloadUrl'] ?? null;
    $sha256 = $stable['sha256'] ?? null;
    $sizeBytes = $stable['sizeBytes'] ?? null;
    if (!is_string($versionName) || trim($versionName) === ''
        || !is_int($versionCode) || $versionCode <= 0
        || !is_string($changelog)
        || !is_string($downloadUrl) || strpos($downloadUrl, '\\') !== false
        || filter_var($downloadUrl, FILTER_VALIDATE_URL) === false
        || !is_string($sha256) || preg_match('/\A[0-9a-fA-F]{64}\z/', $sha256) !== 1
        || !is_int($sizeBytes) || $sizeBytes <= 0 || $sizeBytes > 536870912
    ) {
        return null;
    }
    $url = parse_url($downloadUrl);
    if ($url === false || strtolower($url['scheme'] ?? '') !== 'https' || ($url['host'] ?? '') === ''
        || isset($url['user']) || isset($url['pass']) || isset($url['fragment'])) {
        return null;
    }

    return [
        'versionName' => $versionName,
        'versionCode' => $versionCode,
        'changelog' => $changelog,
        'downloadUrl' => $downloadUrl,
        'sha256' => $sha256,
        'sizeBytes' => $sizeBytes,
    ];
}

$requestPath = explode('?', $_SERVER['REQUEST_URI'] ?? '', 2)[0];
if ($requestPath !== ANTOUPDATE_PATH) {
    antoupdateRespond(404, ['code' => 404, 'message' => 'Not found.']);
    return;
}
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    header('Allow: GET');
    antoupdateRespond(405, ['code' => 405, 'message' => 'Method not allowed.']);
    return;
}

$stable = antoupdateLoadStable();
if ($stable === null) {
    antoupdateRespond(503, ['code' => 403, 'message' => 'Automatic update is unavailable.']);
    return;
}
antoupdateRespond(200, ['code' => 0, 'stable' => $stable]);
