# desktop/tools — 桌面端构建与签名工具

这些脚本是**构建管线的一部分**，被 `.github/workflows/desktop-ci.yml` 调用。
放在这里而不是 `_rev/`，是因为 **`_rev/` 被 `.gitignore` 忽略**，CI 上取不到文件
（这个坑已经踩过一次：CI 报 `can't open file '_rev/win_ref/embed_public_key.py'`）。

> 脚本本身**不含任何密钥** —— 私钥只存在于 GitHub Secrets。

| 脚本 | 谁调用 | 作用 |
|---|---|---|
| `embed_public_key.py` | CI（打包**前**） | 由 `DESKTOP_SIGN_KEY_BASE64` 推导公钥，写入 `IntegrityPublicKey.kt` |
| `make_integrity.py` | CI（打包**后**） | 遍历应用镜像算 SHA-256 → 生成并签名 `integrity.sig` |
| `make_desktop_keys.py` | **人**（一次性） | 生成 Ed25519 密钥 + 自签代码签名证书，输出 3 个 Secret 内容 |
| `setup_desktop_secrets.py` | **人**（一次性，可选） | 生成密钥并通过 API 直接上传到仓库 Secrets（需 PyNaCl） |

## 顺序很重要

```
① embed_public_key.py     ← 公钥必须【编译进程序】，所以要在打包前改源码
② packageAppImage         ← jpackage 出应用镜像
③ make_integrity.py       ← 算哈希 + 签名，把 integrity.sig 放进镜像
④ packageExe + signtool   ← 单文件安装器 + 证书签名
```

`integrity.sig` 生成时会**排除自己**（否则哈希无法自洽）。

## 密钥与 Secrets

```powershell
python desktop/tools/make_desktop_keys.py
```

输出到 `<repo>/_rev/desktop_keys/`（gitignore 内），并打印 3 个 Secret 的完整内容：

| Secret | 用途 |
|---|---|
| `DESKTOP_SIGN_KEY_BASE64` | base64 的 Ed25519 私钥 → 签完整性清单 |
| `DESKTOP_CODESIGN_PFX_B64` | base64 的代码签名证书 → 签安装包 exe |
| `DESKTOP_CODESIGN_PFX_PWD` | 上面那个 pfx 的密码 |

**未配置这些 Secrets 时**：CI 仍然成功，但会打印 warning，程序跳过完整性校验、安装包不签名。
方便先跑通构建，再补安全能力。

## 规范载荷格式（两侧必须严格一致）

`make_integrity.py`（Python）与 `Integrity.canonicalPayload`（Kotlin）用同一套文本：

```
version=1\n
algorithm=Ed25519\n
<path>\t<sha256>\t<size>\n      ← 按 path 字典序
```

任一侧改了格式，验签就会失败。Kotlin 侧有单测
`IntegrityTest.canonicalPayload 格式与构建端一致` 锁死这个约定。

## 自校验的强度边界（务必知悉）

这是**客户端自校验**，能防住「顺手改文件 / 被第三方魔改后重新分发」，
但**防不住**有心人直接 patch 校验代码或替换内嵌公钥 —— 那是客户端校验的固有极限。
真正的边界需要服务端参与或 TPM。

`--skip-integrity-check` 是应急逃生口（构建流程出问题时不至于把自己锁死）。
