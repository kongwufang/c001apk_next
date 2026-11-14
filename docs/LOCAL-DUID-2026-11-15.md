# Android 原生取号迁移（2026-11-15）

## 来源与范围

- 用户要求移除 Android 的服务器取号网页、JSBridge 和云端中转，直接请求上游；确认不要求与旧云端同号，使用应用实际 SSAID。
- 协议参考：https://github.com/kongwufang/szlm_res ，尤其 `tools/getduid.py`、`tools/README.md`、`docs/99-已推翻的结论.md`。参考脚本 SHA-256：`3f9d33c0f7af1dc03bf1a6d761ce45092e568b5d7b6368570fc917cf04c25b64`。
- MIT 许可随 APK 保存在 `assets/third_party/szlm_res-LICENSE.txt`。79 字段协议模板来自该公开脚本，服务端私有 HMAC 种子及管理密钥均未移入客户端。
- Android 客户端已移除旧链路；未修改线上服务器，旧版应用兼容服务保留。

## 新链路

`SzlmIdPrompt` → `SzlmIdActivity` / `SzlmIdViewModel` → `LocalDuidClient` → `auni.telecome.cn` → `SzlmIdPrompt.save`。

- 读取当前应用 `Settings.Secure.ANDROID_ID`，规范为小写 16 位 hex。缺失、全零或 Android 历史共享错误值时失败，不使用随机数/固定公共设备 ID 兜底。
- 本地公开域 `c001apk-local-duid-v1`，对 `ssaid|<ssaid>` 做 HMAC-SHA256，按标签和计数扩展出 `sfOo/foO/2cO/w91/gEd/6yY` 六项。机型伪装、应用版本、时间不参与派生。
- 这是新命名空间，不兼容旧云端的设备信息 JSON + 私有种子映射。升级时不主动换号，已有 DUID 保留；只有用户明确自动取号且上游返回有效 ID 时才保存。
- 稳定的是本地请求指纹，DUID 本身由上游签发。2026-11-15 独立 Python 直连探测中，相同请求两次都收到 36 位非零 DUID，但两次 ID 不同。因此不承诺重取/重装后同号，持久化现有 ID 才是日常复用来源。
- UTF-8 紧凑 JSON（转义 `/`）→ zlib level 6 → 循环 XOR；响应支持 XOR + zlib JSON 和 XOR + 原始 JSON。只接受 `DU` + 34 位 URL 安全字符，拒绝全零、错误类型、损坏及过大响应，限制压缩前后响应为 64 KiB。
- 独立 OkHttp 客户端，无酷安 Cookie、认证头或请求体日志；仅 HTTPS 上游、禁用重定向、无自动重试、30 秒总超时，沿用项目 `SslVerify` 配置（默认校验证书）。不照搬参考 Python/PHP 的无条件关闭 TLS 校验。
- ViewModel 保留旋转期间的请求；退出页面取消请求，失败可重试或手动填入。成功沿用原 `save`，仅替换设备串第一字段并触发本地有效性监测，不重置机型参数。

## 验证

- Python 标准库 + 上游公开脚本生成固定 SSAID 的六字段/完整请求 JSON/两种响应形式，作为 JVM 固定夹具；不包含实际设备 SSAID 或真实 DUID。
- 单元测试覆盖完整 JSON 字节、嵌套对象及数值类型、六字段稳定性/隔离、非法 SSAID、两种响应、零值/畸形/解压过大拒绝。
- CI 增加 `:app:testDebugUnitTest`，仍只通过 debug 测试线构建，不本地编译、不改版本号或正式发版。
- 最终代码 `d2862996`，CI [#656](https://github.com/kongwufang/c001apk_next/actions/runs/43630952125) 成功（单元测试 + assembleRelease + 签名校验），#655 被后续取消修正替代，已取消。
- 已安装并核对 APK SHA-256：`a5f28bae5e5c768179a0d87f6b0078f4d94f30d481a876d04c5185ff3151fa27`。本机文件 `_rev/apk/native-duid-ci656.apk`（与 `_rev/apk/debug-latest.apk` 相同）；新增 5 项 JUnit 协议测试通过，另有既有样例测试。
- 升级安装后旧 ID 保留；实际 SSAID 原生直连成功取得 36 位有效 ID，并写入设备串首字段。取号后 2026-11-15 03:28:46 的 `SzlmMonitor` 详情探测：`id=74345950 code=200 ok=true expired=false`，实际返回业务对象。
- 默认 SSL 校验开启，调试模式关闭。直连日志只记目标主机及响应有效性，不含实际 SSAID/DUID/报文。实际设备标识和偏好备份仅留本机忽略目录，不入库。
- 临时仅阻断上游 `39.97.174.46:443`，实测连接超时、取消、立即拒绝均保留原 ID；失败页有重试和手动入口，输入框可正常打开。退出、旋转均未额外自动重取（日志四次请求对应首次超时、取消、拒绝、正常取号四个显式操作）。阻断规则已全部移除。
- 成功取号前后对比：设备串第一字段与新 DUID 相符，其余分号字段、机型/UA/伪装设置均不变。冷启动后新 ID 仍保留，没有自动发起新的取号请求。
- 已实际查看截图：`_rev/win_ref/shots/offline_drop_native_duid.png`（进度）及 `native_duid_failure_review.png`（错误/重试/手动）。静态错误页 MP4 约 10 KB 也能抽出有效画面，已移除本机 `snap.py` 的 15 KB 硬门槛。屏幕密度 240、旋转配置（自动 1 / user_rotation 0）已恢复。
- 本机证据目录 `_rev/win_ref/local_duid_20261115/` 含脱敏请求元数据、CI 构建日志、界面结构与仅本机私有偏好快照；不提交该目录。
