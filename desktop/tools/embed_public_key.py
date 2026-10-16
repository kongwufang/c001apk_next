#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把发行公钥写入 IntegrityPublicKey.kt（CI 在打包【之前】执行）。

为什么必须在打包前：公钥要**编译进程序**，所以得先改源码再 build。

输入：
    --key-b64   Ed25519 私钥的 base64（来自 GitHub Secret DESKTOP_SIGN_KEY_BASE64）
                或
    --key-pem   私钥 PEM 文件路径（本地调试用）
    --out-kt    目标 Kotlin 文件（默认仓库里的 IntegrityPublicKey.kt）

用法：
    python embed_public_key.py --key-b64 <base64> [--out-kt <path>]
    python embed_public_key.py --key-pem ed25519_private.pem
"""
import argparse
import base64
import io
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="backslashreplace")

try:
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives import serialization
except ImportError:
    sys.exit("缺少依赖：pip install cryptography")

DEFAULT_KT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "core", "src", "main", "kotlin", "com", "example", "c001apk", "desktop", "core",
    "security", "IntegrityPublicKey.kt",
)

CONST_RE = re.compile(r'(BASE64:\s*String\s*=\s*)"[^"]*"')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key-b64", default=None, help="Ed25519 私钥的 base64（PKCS#8 PEM）")
    ap.add_argument("--key-pem", default=None, help="Ed25519 私钥 PEM 文件")
    ap.add_argument("--out-kt", default=DEFAULT_KT)
    a = ap.parse_args()

    if a.key_pem:
        with open(a.key_pem, "rb") as f:
            pem = f.read()
    elif a.key_b64:
        cleaned = re.sub(r"\s", "", a.key_b64)
        pem = base64.b64decode(cleaned)
    else:
        sys.exit("必须提供 --key-b64 或 --key-pem")

    priv = serialization.load_pem_private_key(pem, password=None, backend=default_backend())
    pub_b64 = base64.b64encode(
        priv.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    ).decode("ascii")

    if not os.path.isfile(a.out_kt):
        sys.exit("目标文件不存在：%s" % a.out_kt)
    src = io.open(a.out_kt, encoding="utf-8").read()
    new, n = CONST_RE.subn(lambda m: m.group(1) + '"' + pub_b64 + '"', src)
    if n != 1:
        sys.exit("未能在 %s 中唯一定位 BASE64 常量（匹配 %d 次）" % (a.out_kt, n))

    io.open(a.out_kt, "w", encoding="utf-8", newline="\n").write(new)
    print("已注入公钥：%s" % a.out_kt)
    print("  公钥长度（base64）: %d" % len(pub_b64))
    print("  公钥前 32 字符     : %s" % pub_b64[:32])


if __name__ == "__main__":
    main()
