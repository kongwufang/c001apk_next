# Android 开发线改动记录（2026-11-20 至 11-22）

记录范围：正式版 V1.0.11 之后的 `7a639f07..b0aa6314`（27 个提交），以及与之相关的服务端更新接口和只读表情调查。2026-11-22 本地 `main` 和远程跟踪 `next/debug` 指向 `b0aa6314`；`next/main` 仍是 `7a639f07`。**这些客户端变更没有合入正式线，也没有涨版本号。** CI 测试包仍为 `10011`，`debug` 是构建渠道标记，不代表可调试或无签名。最近验收使用 Android 9（API 28）模拟器；同签名覆盖安装，不卸载、不清除数据。

## 已有专项记录

| 改动 | 最终行为 | 提交与证据 |
| --- | --- | --- |
| 回复原图、长按复制与引导 | 回复里的图片链接仍可短按打开原图；长按交给原生选字。自动刷新数字联盟 ID 开关移到引导第 3 页；下文移除的是主动验号，不是自动刷新。 | `1d06aa71`、`4ec33685`、`9ca11fd5`；[回复与引导验收](REPLY-IMAGE-ONBOARDING-2026-11-20.md) |
| 消息中心缓存 | 按账号隔离；成功刷新以服务端快照同步增删改，失败保留原分组，旧请求不能回写新会话；修正 V18 游标与分页排序。 | `74b0acbd`，CI #675；[测试及设备验证边界](MESSAGE-CACHE-2026-11-21.md) |
| 通用 WebView | 左上返回与系统返回先退网页历史，无历史则退出；刷新不新增历史，独立关闭按钮直接退出。登录 WebView 不使用这套通用导航。 | `4bd276e0`、`ee49a0f0`，CI #677 / #678；[首版设备验证](WEBVIEW-NAVIGATION-2026-11-21.md)中的「第一页不退出」只适用于 #677，已被 #678 改正。 |

## 其余客户端功能与修复

| 改动 | 最终行为 / 边界 | 提交与复核入口 |
| --- | --- | --- |
| 侧栏「酷回收」 | 九宫格原「我的关注」位置改为「酷回收」；在应用内打开，不因 App 未登录而隐藏入口。 | `b0105ac1`，测试包 CI #676；[侧栏菜单实现](../app/src/main/java/com/example/c001apk/ui/message/MineMenuAdapter.kt) |
| 全屏 GIF 与 APK 去原生库 | Mojito 全屏 GIF 改用 Glide 动画播放，移除旧原生 GIF 解码依赖。APK 检查不含 ZIP `lib/` 文件、`.so` 或直接 ELF 条目；这不是对系统组件或嵌套归档的结论。`minSdk=24` 不等于已在 API 24 设备运行验证。 | `7686b8cd`，CI #679；[签名/APK/GIF 复核方法](../_rev/win_ref/verify_mojito_gif.md) |
| 酷安登录 | 默认由酷安 HTTPS 网页完成授权，通过授权码换取并校验会话；兼容网页登录保留。旧 WebView 会话先清理，过期回调不能覆盖当前会话；SSL 错误取消而非继续。 | `8a4b7499`，测试包 CI #680；[登录实现](../app/src/main/java/com/example/c001apk/ui/login/WebLoginActivity.kt)、[会话回归测试](../app/src/test/java/com/example/c001apk/util/AccountLoginTest.kt)。不把构建或模型测试视作真实账号端到端登录通过。 |
| 页面转场 | 默认「跟随系统」；切换「自定义」才显示曲线与速度配置。保留评论浮层动效。仅在同一任务中前一页关闭后、立即恢复的页面再次快速关闭时，合并第二次关闭动效；窗口 350 ms，不拦截返回事件或接管系统手势。 | `fcb4ac5b`、`94db563e`、`9db36683`，CI #682 / #683；[设置复核脚本](../_rev/win_ref/transition_settings_smoke.py)、[快速返回判定](../app/src/main/java/com/example/c001apk/util/RapidReturn.kt)。未将其他试验性返回方案纳入。 |
| 文章正文短按链接与长按选字 | 内联链接由可选文本手势处理，避免额外的 XML 点击绑定重复分发。按实际按下时长区分短按和长按；长按保留原生选字，独立链接卡片不走这条文本路径。 | `1b5bbd43`、`aef48a4a`、`a425d0c8`、`fe6f2a96`；[正文绑定测试](../app/src/test/java/com/example/c001apk/ui/feed/ArticleTextBindingTest.kt)、[原帖 75086301 复核脚本](../_rev/win_ref/article_link_smoke.py)。 |

## Wi-Fi 静默下载更新

- 客户端启用自动检查及 Wi-Fi 自动下载后，使用独立正式更新接口；下载只在 Wi-Fi 上进行，DNS 也绑定相同网络。无可用 Wi-Fi 时等待，不回退到蜂窝网络下载。初次尝试之外最多静默重试五次；自动路径失败仍可使用原手动更新入口。
- 包在应用私有目录下载；校验文件大小、SHA-256、包名、版本和签名。Android 9 的签名读取另做兼容。就绪后由用户点击交给系统安装器，**不自动安装**；跳过按版本记忆，清理当前跳过或旧版本的缓存。
- 服务端独立路由 [正式自动更新接口](https://service.houlangs.cn/c001apk/antoupdate/) 已部署，验收结束时仍公布正式 `10011`；保留旧 `/update/` 行为。更新接口和正式包同步、原子替换规则见[服务端发布约定](../server/antoupdate/README.md)。
- `21d4d786`、`e5371c27`、`e7ef9dbc`、`d6fe652c`；签名测试包 CI #694。隔离 QA 使用正式同签名 `10012`，成功下载并打开 Android 系统安装器，随后**取消安装**；没有把 QA 活动推到正式接口，没有正式发布 `10012`，隔离服务与临时产物已清理。此处不存放任何签名密钥或凭据。
- [客户端状态机](../app/src/main/java/com/example/c001apk/util/SelfUpdates.kt)、[下载和校验](../app/src/main/java/com/example/c001apk/util/WifiUpdateDownload.kt)、[回归测试](../app/src/test/java/com/example/c001apk/util/AutoUpdateTest.kt)。

## 实况图全屏首帧闪动

- 原帖：[酷安 75194739](https://www.coolapk1s.com/feed/75194739)，第一张实况。旧版先按全屏边界显示一帧 MP4，下一帧才按视频比例缩回。
- 保留封面，等视频有效尺寸完成居中适配且首帧已渲染，两条件都满足才显示视频层。处理尺寸/首帧回调任意先后顺序；播放结束恢复封面。Mojito 同一个内容 View 的重复完成回调不再重绑播放器。
- `25fbe3d3`、`576a74f7`、`c75feb01`；[CI #697](https://github.com/kongwufang/c001apk_next/actions/runs/44927877904) 成功。在同一 Android 9 模拟器逐帧对比：旧版有一帧占满整个屏幕；修复后从封面、首帧、正常播放到恢复封面共检查 71 帧，没有满屏拉伸。[对比图](../_rev/live_flash_comparison.png) · [验证脚本](../_rev/win_ref/live_photo_flash_smoke.py) · [布局回归测试](../app/src/test/java/com/example/c001apk/util/LivePhotoVideoLayerTest.kt)。

## 移除数字联盟 ID 主动有效性探测

- 删除旧的冷启动后约 8 秒首次探测、约每 10 分钟探测，以及保存 ID 后立即验号。现在不再额外发帖详情探测请求；正常业务请求命中已知风控/疑似失效响应仍会提示。独立的 24 小时[自动刷新规则](AUTO-DUID-REFRESH-2026-11-15.md)不变。
- `b0aa6314`；[CI #698](https://github.com/kongwufang/c001apk_next/actions/runs/44930762090) 成功。[签名测试包](../_rev/apk/no-szlm-monitor-ci698.apk)通过签名校验并同签名覆盖安装；APK DEX 无旧探测器，冷启动超过旧 8 秒门槛未出现探测日志。未刻意让真实账号触发线上风控。当前[业务请求提示](../app/src/main/java/com/example/c001apk/util/SzlmHint.kt)仍接入网络拦截器。

## 内置表情迁移至 S3 资源包

[表情库云端覆盖与 APK 瘦身评估](EMOJI-CLOUD-AUDIT-2026-11-22.md)确认：官方登录目录的 55 张「酷小牛」与原内置的 151 个表情码零交集，公开 CDN 也只覆盖 141/183，不能直接替代。采用自有 S3 的 183 张无损 WebP 包（151 张原图 + 32 张牛牛），从 APK 移出原图。启动时检查版本清单，落盘前校验大小和 SHA-256；已有资源可离线使用，无包时正文显示表情码。第四页引导默认仅 Wi-Fi 自动下载，修改后点确认才保存。具体发布流程见[包体发布说明](../server/emojipack/README.md)。

- `12949c15`，[CI #702](https://github.com/kongwufang/c001apk_next/actions/runs/44992965642) 通过；签名测试 APK 比 #698 减少 **1,243,733 B（17.12%）**。已用 Android 9 模拟器验证自动下载、缓存离线冷启动、较旧版本更新、计量网络等待及引导保存；未推送正式分支。另以同一签名包实际发布评论、图文和动态，冷启动回读后，三组各抽一张均显示为行内图片；测试内容已清理。完整证据见[表情专项记录](EMOJI-CLOUD-AUDIT-2026-11-22.md)。

## V1.0.12 更新后重新引导一次

- 正式版 `versionCode=10012` 起，已走完旧引导的用户会从第一页重新走四步。只有点第四页「完成」才记录修订 3；之后冷启动及未变动引导的后续版本不再重复出现。旧 `10011` 测试包仍遵循修订 2，不能提前消耗这次确认。
- 保留已有账号、机型参数、数字联盟 ID 和已保存偏好；第三页已有 ID 可直接下一步，第四页表情包若未设置过则默认仅 Wi-Fi，点「完成」才保存。验证入口：[引导修订测试](../app/src/test/java/com/example/c001apk/util/OnboardingPolicyTest.kt)。
- GitHub CI 可在 `debug` 手动选择 `onboarding_compat` 生成同签名 `10012` 兼容验证包，单独上传为 Actions artifact；不修改正式版本号基线，也不打开正式发版锁。

> 文档中的 `_rev` 链接指向本机证据与验收包，不是正式下载地址，也不要求随代码提交。从此记录继续时，不要覆盖无关工作区修改或把测试渠道当成正式发布。
