#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成桌面端「完整性清单」并签名（CI 打包后执行）。

流程：
    1. 递归遍历 jpackage 应用目录，对每个文件算 SHA-256（路径用 '/' 分隔、相对目录根）
    2. 生成规范化载荷 canonicalPayload（必须与 Kotlin `Integrity.canonicalPayload` 完全一致）
    3. 用 Ed25519 私钥对载荷签名
    4. 产出：
         <appDir>/integrity.sig              清单 + 签名（放进应用目录）
         <outKt>                             Kotlin 源码：内嵌公钥（编译进程序）

canonicalPayload 约定（Kotlin 侧同此）：
    version=<n>\n
    algorithm=Ed25519\n
    <path>\t<sha256>\t<size>\n     ← 按 path 字典序
    末尾无多余字符

用法：
    python make_integrity.py --app-dir <应用目录> --key ed25519_private.pem \
                             --out-kt <IntegrityPublicKey.kt> [--verify-only]
"""
import argparse
import base64
import hashlib
import io
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="backslashreplace")

try:
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ed25519
except ImportError:
    sys.exit("缺少依赖：pip install cryptography")

MANIFEST_NAME = "integrity.sig"
VERSION = 1
ALGORITHM = "Ed25519"

KT_TEMPLATE = '''package com.example.c001apk.desktop.core.security

/**
 * 完整性校验用的内嵌公钥（Ed25519, X.509 SubjectPublicKeyInfo 的 base64）。
 *
 * ★ 本文件由 CI 在打包后自动生成（_rev/win_ref/make_integrity.py），**请勿手改**。
 *   本地开发构建下为空字符串 —— 此时 `Integrity.verify` 会自动跳过校验，不阻塞调试。
 */
object IntegrityPublicKey {
    const val BASE64: String = "%s"
}
'''


def walk_files(root):
    out = []
    for dp, _, fns in os.walk(root):
        for fn in fns:
            p = os.path.join(dp, fn)
            if not os.path.isfile(p):
                continue
            rel = os.path.relpath(p, root).replace("\\", "/")
            if os.path.basename(rel) == MANIFEST_NAME:
                continue
            out.append((rel, p))
    out.sort(key=lambda x: x[0])
    return out


def sha256_of(path, buf_size=1 << 16):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            b = f.read(buf_size)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def canonical_payload(entries):
    """与 Kotlin Integrity.canonicalPayload 严格一致"""
    sb = []
    sb.append("version=%d\n" % VERSION)
    sb.append("algorithm=%s\n" % ALGORITHM)
    for path, digest, size in sorted(entries, key=lambda e: e[0]):
        sb.append("%s\t%s\t%d\n" % (path, digest, size))
    return "".join(sb).encode("utf-8")


def json_escape(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


def build_manifest_json(entries, signature_b64):
    lines = []
    lines.append("{")
    lines.append('  "version": %d,' % VERSION)
    lines.append('  "algorithm": "%s",' % ALGORITHM)
    lines.append('  "signature": "%s",' % signature_b64)
    lines.append('  "entries": [')
    for i, (path, digest, size) in enumerate(entries):
        comma = "," if i < len(entries) - 1 else ""
        lines.append(
            '    {"path": "%s", "sha256": "%s", "size": %d}%s'
            % (json_escape(path), digest, size, comma)
        )
    lines.append("  ]")
    lines.append("}")
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--app-dir", required=True, help="jpackage 应用镜像目录（含 app/ 与 runtime/）")
    ap.add_argument("--key", required=True, help="Ed25519 私钥 PEM（PKCS#8）")
    ap.add_argument("--out-kt", required=True, help="输出的 IntegrityPublicKey.kt 路径")
    ap.add_argument("--verify-only", action="store_true", help="只校验已有清单，不重新生成")
    a = ap.parse_args()

    if not os.path.isdir(a.app_dir):
        sys.exit("应用目录不存在：%s" % a.app_dir)

    with open(a.key, "rb") as f:
        priv = serialization.load_pem_private_key(f.read(), password=None,
                                                  backend=default_backend())
    if not isinstance(priv, ed25519.Ed25519PrivateKey):
        sys.exit("私钥不是 Ed25519（当前为 %s）" % type(priv).__name__)

    pub_der = priv.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    pub_b64 = base64.b64encode(pub_der).decode("ascii")

    files = walk_files(a.app_dir)
    print("应用目录：%s" % a.app_dir)
    print("待哈希文件：%d 个" % len(files))

    entries = []
    total = 0
    for rel, p in files:
        size = os.path.getsize(p)
        total += size
        entries.append((rel, sha256_of(p), size))
    print("总大小：%.1f MB" % (total / 1024 / 1024))

    payload = canonical_payload(entries)
    sig_b64 = base64.b64encode(priv.sign(payload)).decode("ascii")
    print("载荷 %.1f KB，签名 %d 字节（base64）" % (len(payload) / 1024, len(sig_b64)))

    manifest_path = os.path.join(a.app_dir, MANIFEST_NAME)
    with open(manifest_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(build_manifest_json(entries, sig_b64))
    print("已写出清单：%s（%.1f KB）" % (manifest_path, os.path.getsize(manifest_path) / 1024))

    os.makedirs(os.path.dirname(os.path.abspath(a.out_kt)), exist_ok=True)
    with open(a.out_kt, "w", encoding="utf-8", newline="\n") as f:
        f.write(KT_TEMPLATE % pub_b64)
    print("已写出公钥源码：%s" % a.out_kt)

    # 自校验：重新验一遍签名与哈希，确保 Kotlin 侧能通过
    print()
    print("── 自校验 ──")
    ok_sig = True
    try:
        priv.public_key().verify(base64.b64decode(sig_b64), payload)
    except Exception as e:
        ok_sig = False
        print("  ✗ 签名校验失败：%s" % e)
    print("  签名校验：%s" % ("✓ 通过" if ok_sig else "✗ 失败"))

    # 抽样重算，确认哈希稳定
    sample = entries[:3] + entries[-3:]
    ok_hash = all(sha256_of(os.path.join(a.app_dir, rel.replace("/", os.sep))) == d
                  for rel, d, _ in sample)
    print("  哈希抽样复核（%d 个）：%s" % (len(sample), "✓ 通过" if ok_hash else "✗ 失败"))
    print("  文件数：%d" % len(entries))

    if not (ok_sig and ok_hash):
        sys.exit(1)


if __name__ == "__main__":
    main()
