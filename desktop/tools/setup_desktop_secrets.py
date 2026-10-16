#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成桌面端发行密钥，并直接上传到 GitHub 仓库 Secrets。

用法：
    python setup_desktop_secrets.py                 # 生成到默认目录并上传
    python setup_desktop_secrets.py --dry-run       # 只生成，不上传
    python setup_desktop_secrets.py --repo owner/name

需要：
    * PyNaCl（GitHub Secrets 用 libsodium sealed box 加密）
    * 仓库 token（默认从 git remote `next` 的 URL 里提取）
"""
import argparse
import base64
import io
import json
import os
import re
import subprocess
import sys

import requests

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="backslashreplace")

GIT = r"D:\works\tools\PortableGit\cmd\git.exe"
WORKDIR = r"D:\works\c001apk"
DEFAULT_OUT = r"D:\works\c001apk\_rev\desktop_keys"


def get_token_and_repo():
    out = subprocess.run([GIT, "remote", "get-url", "next"], capture_output=True,
                         text=True, cwd=WORKDIR).stdout.strip()
    m = re.search(r"https://x-access-token:([^@]+)@github\.com/(.+?)(?:\.git)?$", out)
    if not m:
        sys.exit("无法从 remote 提取 token/repo：%s" % out)
    return m.group(1), m.group(2)


def run_keygen(out_dir):
    """调用 make_desktop_keys.py 生成密钥"""
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "make_desktop_keys.py")
    args = [sys.executable, script, "--out", out_dir]
    if os.path.exists(out_dir) and os.listdir(out_dir):
        args.append("--force")
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print(r.stdout[-2000:])
        print(r.stderr[-2000:])
        sys.exit("密钥生成失败")
    return r.stdout


def encrypt_secret(public_key_b64: str, value: str) -> str:
    """GitHub 要求的 libsodium sealed box 加密"""
    from nacl import encoding, public
    pk = public.PublicKey(public_key_b64.encode("utf-8"), encoding.Base64Encoder())
    sealed = public.SealedBox(pk).encrypt(value.encode("utf-8"))
    return base64.b64encode(sealed).decode("utf-8")


def b64_of(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("ascii")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--repo", default=None, help="owner/name（默认从 remote 推断）")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--reuse", action="store_true",
                    help="复用已存在的密钥文件（不重新生成）")
    a = ap.parse_args()

    token, repo = get_token_and_repo()
    if a.repo:
        repo = a.repo
    print("仓库：%s" % repo)
    print("token：%s…（已从 remote 提取）" % token[:12])
    print()

    # ── 1. 密钥 ──
    need_gen = not (a.reuse and os.path.isfile(os.path.join(a.out, "ed25519_private.pem")))
    if need_gen:
        print("=== 生成发行密钥 ===")
        log = run_keygen(a.out)
        for line in log.splitlines():
            if line.startswith("  ") or "指纹" in line or "已生成" in line:
                print(line)
    else:
        print("=== 复用已有密钥：%s ===" % a.out)

    p_priv = os.path.join(a.out, "ed25519_private.pem")
    p_pfx = os.path.join(a.out, "codesign.pfx")
    p_pwd = os.path.join(a.out, "codesign_pwd.txt")
    for p in (p_priv, p_pfx, p_pwd):
        if not os.path.isfile(p):
            sys.exit("缺少文件：%s" % p)

    secrets = {
        "DESKTOP_SIGN_KEY_BASE64": b64_of(p_priv),
        "DESKTOP_CODESIGN_PFX_B64": b64_of(p_pfx),
        "DESKTOP_CODESIGN_PFX_PWD": io.open(p_pwd, encoding="ascii").read().strip(),
    }
    print()
    print("准备上传的 Secrets：")
    for k, v in secrets.items():
        print("  %-28s %d 字符" % (k, len(v)))

    if a.dry_run:
        print()
        print("--dry-run：已跳过上传。")
        return

    # ── 2. 取仓库公钥 ──
    S = requests.Session()
    S.trust_env = False
    H = {
        "Authorization": "Bearer " + token,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    r = S.get("https://api.github.com/repos/%s/actions/secrets/public-key" % repo,
              headers=H, timeout=30)
    if r.status_code != 200:
        sys.exit("取仓库公钥失败 HTTP %s：%s" % (r.status_code, r.text[:300]))
    pk = r.json()
    key_id, key_b64 = pk["key_id"], pk["key"]
    print()
    print("仓库公钥 key_id：%s" % key_id)

    # ── 3. 逐个上传 ──
    print()
    print("=== 上传 Secrets ===")
    ok = 0
    for name, value in secrets.items():
        enc = encrypt_secret(key_b64, value)
        resp = S.put(
            "https://api.github.com/repos/%s/actions/secrets/%s" % (repo, name),
            headers=H, json={"encrypted_value": enc, "key_id": key_id}, timeout=30,
        )
        status = "✓ 已创建/更新" if resp.status_code in (201, 204) else "✗ HTTP %s %s" % (
            resp.status_code, resp.text[:160])
        if resp.status_code in (201, 204):
            ok += 1
        print("  %-28s %s" % (name, status))

    print()
    print("成功 %d / %d" % (ok, len(secrets)))

    # ── 4. 复核 ──
    r = S.get("https://api.github.com/repos/%s/actions/secrets" % repo, headers=H, timeout=30)
    if r.status_code == 200:
        names = {s["name"] for s in r.json().get("secrets", [])}
        print()
        print("仓库当前 Secrets（复核）：")
        for n in sorted(names):
            mark = "  ← 本次设置" if n in secrets else ""
            print("  %-30s%s" % (n, mark))

    print()
    print("=" * 92)
    print("⚠️  请立刻做两件事：")
    print("   1. 备份私钥并从本机删除明文：%s" % a.out)
    print("   2. 轮换那个写在 git remote URL 里的 PAT（它已出现在我的上下文里）")
    print("=" * 92)


if __name__ == "__main__":
    main()
