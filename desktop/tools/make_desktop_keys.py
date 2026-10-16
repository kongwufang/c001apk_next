#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""桌面端发行密钥生成（一次性执行，密钥自行保存到 GitHub Secrets）。

产出（放在 --out 目录，默认 _rev/desktop_keys/，已被 .gitignore 忽略）：

  ed25519_private.pem    ★ 私钥（PKCS#8）—— 给「完整性清单」签名
  ed25519_public.txt       公钥（base64 X.509 SPKI）—— CI 写进程序源码
  codesign.pfx           ★ 代码签名证书（含 RSA 私钥）—— 签安装包 exe
  codesign_pwd.txt         上述 pfx 的密码
  codesign.crt             证书（PEM，便于查看/备用）

对应的 GitHub Secrets（照 Android 端 SIGN_KEYSTORE_BASE64 那套做法）：

  DESKTOP_SIGN_KEY_BASE64   = base64(ed25519_private.pem)     # 签完整性清单
  DESKTOP_CODESIGN_PFX_B64  = base64(codesign.pfx)            # 签安装包
  DESKTOP_CODESIGN_PFX_PWD  = codesign_pwd.txt 内容

用法：
    python make_desktop_keys.py                 # 默认目录
    python make_desktop_keys.py --out D:\\keys   # 指定目录
    python make_desktop_keys.py --force         # 覆盖已有密钥

说明：自签证书**不会**消除 Windows SmartScreen 提示（系统只信受信任根 CA 签发的证书），
      这点已与使用方确认 —— 只需要「我们自己的签名」+ 启动自校验即可。
"""
import argparse
import base64
import datetime as _dt
import io
import os
import secrets
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="backslashreplace")

try:
    from cryptography import x509
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ed25519, rsa
    from cryptography.hazmat.primitives.serialization import pkcs12
    from cryptography.x509.oid import NameOID
except ImportError:
    sys.exit("缺少依赖：pip install cryptography")

# 默认输出到 <repo>/_rev/desktop_keys（_rev 已被 .gitignore 忽略，避免密钥误入仓库）
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_OUT = os.path.join(_REPO_ROOT, "_rev", "desktop_keys")
SUBJECT_CN = "c001apk desktop"
SUBJECT_O = "c001apk_next"
SUBJECT_C = "CN"


def gen_ed25519(out_dir):
    """完整性清单签名用的 Ed25519 密钥对"""
    priv = ed25519.Ed25519PrivateKey.generate()
    priv_pem = priv.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    pub_der = priv.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    pub_b64 = base64.b64encode(pub_der).decode("ascii")

    p_priv = os.path.join(out_dir, "ed25519_private.pem")
    p_pub = os.path.join(out_dir, "ed25519_public.txt")
    with open(p_priv, "wb") as f:
        f.write(priv_pem)
    with open(p_pub, "w", encoding="ascii") as f:
        f.write(pub_b64 + "\n")
    return p_priv, p_pub, pub_b64


def gen_codesign_pfx(out_dir, pwd):
    """生成自签代码签名证书并导出 PFX（纯 Python，不依赖证书存储权限）"""
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048,
                                   backend=default_backend())
    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, SUBJECT_CN),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, SUBJECT_O),
        x509.NameAttribute(NameOID.COUNTRY_NAME, SUBJECT_C),
    ])
    now = _dt.datetime.utcnow()
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - _dt.timedelta(days=1))
        .not_valid_after(now + _dt.timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(
            x509.KeyUsage(
                digital_signature=True, content_commitment=False,
                key_encipherment=False, data_encipherment=False,
                key_agreement=False, key_cert_sign=False, crl_sign=False,
                encipher_only=False, decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(
            x509.ExtendedKeyUsage([x509.oid.ExtendedKeyUsageOID.CODE_SIGNING]),
            critical=False,
        )
        .sign(key, hashes.SHA256(), default_backend())
    )

    p_pfx = os.path.join(out_dir, "codesign.pfx")
    with open(p_pfx, "wb") as f:
        f.write(
            pkcs12.serialize_key_and_certificates(
                name=SUBJECT_CN.encode(),
                key=key,
                cert=cert,
                cas=None,
                encryption_algorithm=serialization.BestAvailableEncryption(pwd.encode()),
            )
        )
    p_crt = os.path.join(out_dir, "codesign.crt")
    with open(p_crt, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))

    thumb = cert.fingerprint(hashes.SHA1()).hex()
    return p_pfx, p_crt, thumb


def b64_of(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("ascii")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--force", action="store_true", help="覆盖已存在的密钥")
    ap.add_argument("--pfx-password", default=None, help="指定 PFX 密码（默认随机）")
    a = ap.parse_args()

    os.makedirs(a.out, exist_ok=True)
    existing = [f for f in os.listdir(a.out) if f.endswith((".pem", ".pfx", ".txt", ".crt"))]
    if existing and not a.force:
        sys.exit("目录 %s 已有密钥文件：%s\n如确认重新生成（会作废旧密钥！），加 --force"
                 % (a.out, ", ".join(existing)))

    pwd = a.pfx_password or secrets.token_urlsafe(24)

    p_priv, p_pub, pub_b64 = gen_ed25519(a.out)
    p_pfx, p_crt, thumb = gen_codesign_pfx(a.out, pwd)
    p_pwd = os.path.join(a.out, "codesign_pwd.txt")
    with open(p_pwd, "w", encoding="ascii") as f:
        f.write(pwd + "\n")

    print("=" * 96)
    print("桌面端发行密钥已生成：", a.out)
    print("=" * 96)
    for f in sorted(os.listdir(a.out)):
        p = os.path.join(a.out, f)
        if os.path.isfile(p):
            print("  %-28s %7.1f KB" % (f, os.path.getsize(p) / 1024))
    print()
    print("代码签名证书指纹(SHA-1) :", thumb)
    print("证书主题                : CN=%s, O=%s, C=%s" % (SUBJECT_CN, SUBJECT_O, SUBJECT_C))
    print("Ed25519 公钥(base64)    :", pub_b64[:48] + "…")
    print()
    print("=" * 96)
    print("请到 GitHub 仓库 Settings → Secrets and variables → Actions 添加这 3 个 Secret：")
    print("=" * 96)
    print()
    print("【DESKTOP_SIGN_KEY_BASE64】（base64 的 Ed25519 私钥，用于签完整性清单）")
    print()
    print(b64_of(p_priv))
    print()
    print("【DESKTOP_CODESIGN_PFX_B64】（base64 的代码签名证书，用于签安装包）")
    print()
    print(b64_of(p_pfx))
    print()
    print("【DESKTOP_CODESIGN_PFX_PWD】（代码签名证书密码）")
    print()
    print(pwd)
    print()
    print("=" * 96)
    print("⚠️  请立刻备份下列私钥文件到安全位置，并从本机删除明文副本：")
    print("     %s" % p_priv)
    print("     %s" % p_pfx)
    print("    （_rev/ 已被 .gitignore 忽略，但仍建议移出仓库）")
    print("=" * 96)


if __name__ == "__main__":
    main()
