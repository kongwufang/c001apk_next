# 酷安表情包：内置资源、云端覆盖与 APK 瘦身评估

- 日期：2026-11-22
- 范围：Android；官方酷安 16.7.3（2611121）、c001apk V1.0.11-debug（CI #698，10011），另参考官方 16.5.1 和 15.1.1。
- 状态：已完成拆包和探测。**尚未决定方案；没有修改 App、构建新包或部署服务。**

## 1. 内置表情对比

| 图片表情分组 | 官方 16.7.3 | c001apk CI #698 | 对比结果 |
| --- | ---: | ---: | --- |
| 默认 | 108 | 108 | 表情码、分组内顺序、解码后 RGBA 像素完全一致 |
| 酷币 | 43 | 43 | 表情码、分组内顺序、解码后 RGBA 像素完全一致 |
| 牛牛 | 32 | 0 | c001apk 没有该组图片和表情码映射，遇到 `[牛牛点赞]` 等会显示原文 |
| 合计 | 183 | 151 | 不计分组图标、正文特殊标记及 Unicode Emoji |

官方 15.1.1 没有「牛牛」分组；16.5.1 内置 30 张，16.7.3 新增「牛牛真香」「牛牛爆赞」两张。新版在初始化代码中将 `niuniu_emotion_array` 和 APK 内的 32 张 PNG 配对，另有 1 张分组图标。这不是只有联网才能取得的表情组。「最近」是使用历史；官方还单列 Unicode Emoji，不是额外的 PNG 表情包。我们 Android 的表情映射见 [EmojiUtils.kt](../app/src/main/java/com/example/c001apk/util/EmojiUtils.kt#L8-L165)。

## 2. 官方在线表情目录

反编译确认：官方回复、私聊面板会将远程图片分组**追加**在本地组后面，未看到该链路覆盖本地表情码映射。

- `GET emojiPicture/groupList`：返回组 ID、标题、图标、数量、显示顺序等。
- `GET emojiPicture/groupEmojiList?group=<组 ID>`：返回该组图片清单，模型包含原图、缩略图、GIF URL 等字段。
- 目录刷新间隔为 **5 分钟**，目录与清单的磁盘缓存有效期为 **7 天**。5 分钟是刷新判断的阈值，不是后台定时推送；代码在面板显示等时机触发判断。
- 模拟器里的官方 App 从帖子点击「写评论」进入登录页，未登录测试没有进入表情面板。

### 补测：c001apk 请求头与接口响应

c001apk 的 [Retrofit/OkHttp 配置](../app/src/main/java/com/example/c001apk/di/NetworkModule.kt#L62-L116)在发出请求前通过 [AddCookiesInterceptor](../app/src/main/java/com/example/c001apk/util/AddCookiesInterceptor.kt#L21-L54)补齐 UA、客户端版本等头，并以同一份设备串和版本号生成请求 Token；默认还会按当前会话附加 Cookie。当前 c001apk 未接入表情分组路由。用不带 Token、设备串和 Cookie 的脚本做请求头对照：

| `GET /v6/emojiPicture/groupList` | HTTP | 响应 |
| --- | ---: | --- |
| 普通脚本 UA | 403 | 空 HTML |
| 只换为客户端格式的 UA | 404 | JSON `status=1000`，请求方式错误 |
| 客户端格式 UA 加固定头（版本、SDK、`X-Requested-With` 等） | 200 | JSON `status=1004`，请求未验证 |

同样的 UA 和固定头访问 App 已用的 `/v6/feed/detail` 也返回 HTTP 200、业务状态 `1004`。这说明先前的 403 不能用来判断接口不存在；HTTP 200 也不代表取到了目录。

**现算 Token 复测（2026-11-22）：** 使用工作区的 [v3 Token 计算器](../_rev/v3_token_fixed.py#L63-L92)，用当前 Unix 秒、与请求头一致的 `X-App-Code` 和 App 默认设备串（数字联盟 ID 留空）生成 Token。不带 Cookie，不读取账号数据。相同设备串和其他请求头下，无 Token 返回 `status=1004`「请求未验证」；加现算 Token 后返回 HTTP 200、业务 `status=200`「你还没有登录，请先登录！」，无 `data`。用同样方式请求首页作正对照，实际返回 11 条数据。因而这次访客请求已到登录检查，动态 Token 不能替代登录。

补充测试同一套请求头下的其他表情路由：`emojiPicture/list`、`emojiPicture/list?group=`、`emojiPicture/list?group=0`、`emojiPicture/groupEmojiList?group=0`、`emojiPicture/groupEmojiList?group=` 全部返回 HTTP 200、业务 `status=200`「你还没有登录，请先登录！」，无 `data`。官方这几条表情路由是**登录后接口**，不代表游客或新安装可用。

### 登录态实测目录（2026-11-22）

在模拟器里登录后，把该 App 自己的 `settings.xml` 备份到被忽略的 `_rev/private_session/`（不打印、不提交、不写入文档），用其中的设备串、UA、版本号和会话 Cookie 生成请求。本次只做只读 GET，Token 仍按当前时间现算。

- `emojiPicture/groupList` 返回 **2 个组**：ID 1「酷小牛」31 张，ID 5「酷小牛2.0」24 张。
- 逐组取 `groupEmojiList` 成功，两组共 **55 张**，全部为 PNG、300×300，无 GIF。
- 表情码带组前缀，形如 `[1:头条通知书]`、`[5:+1]`，与内置的 `[头条通知书]` 不是同一个码。
- 与当前内置 151 个码精确比对：**交集为 0**。仅按去掉前缀后的名字看，有「头条通知书」「真正的音乐」两张同名，但下载图与内置图平均 RGB 差异约 57～103，是不同画作，不是同一张图。
- 目录里没有任何「默认」108 张或「酷币」43 张的内容；它只是两组独立的「酷小牛」表情。该账号只看到 2 组；官方代码允许最多 50 组，其他账号是否不同未测。

复核脚本 [emoji_remote_probe.py](../_rev/win_ref/emoji_remote_probe.py)；原始响应（不含凭据）在 `_rev/win_ref/emoji-remote-groups.json`。

## 2.1 对「云端替代内置」的结论

- 官方表情目录与组内清单都需要登录，且没有可匿名读取的等价路由；因此它无法在网络不可用、未登录或首次启动时提供表情。
- 登录后能取到的只有 2 组共 55 张「酷小牛」图，与内置的默认、酷币表情零交集，也不含内置「牛牛」的 32 张。
- 公开 CDN 只覆盖 183 张官方内置中的 141 张（默认组缺 10 张、牛牛组 0 张），也不足以完整替换。
- 只有在用户已登录且在线时，远程分组才可用；这是**附加内容**，不能成为移除内置表情的来源。

## 3. 公开 CDN 逐项核对

用脚本从 [公开 CDN 的 `v9` 路径](https://static.coolapk.com/emoticons/v9/) 按已知文件名请求，并用 Pillow 比较下载图与 APK 内 PNG 的尺寸、RGBA 像素。仅完全透明像素的 RGB 值不同，仍视为显示效果一致。此项测试独立于在线分组 API，不依赖账号。

| 分组 | 测试数 | 取得可解码 PNG | 与内置版显示效果一致 |
| --- | ---: | ---: | ---: |
| 默认 | 108 | 98 | 97 |
| 酷币 | 43 | 43 | 43 |
| 牛牛 | 32 | 0 | 0 |
| 合计 | 183 | 141 | 140 |

- 141 张中，21 张 RGBA 完全一致，119 张只差全透明像素的 RGB；`[牛啤]` 的可见像素有差异。
- 默认组缺图 10 张：`[针不戳]`、`[列文虎克]`、`[真正的音乐]`、`[感知不强]`、`[给我整一个]`、`[yyds]`、`[夏阁艾迪剑]`、`[下次一定]`、`[酷安土豆]`、`[头条通知书]`。
- 以上结果仅覆盖已知 `v9` 路径和测试过的文件名，不能断言其他路径没有这些表情。**目前未验证任何可完整替代包内表情的官方云端来源。**

## 4. 体积与待选方案

以 CI #698 APK 的 ZIP 条目压缩大小为基线：整包 **7,265,321 B**；现有 151 张图片共 **1,214,720 B**，约 **1.21 MB（16.72%）**。官方 16.7.3「牛牛」32 张 PNG 加 1 张分组图标约 **502 KB**（压缩后），当前我们的 APK 不包含这部分。

| 方案 | 可节省体积 / 验证结果 | 取舍 |
| --- | --- | --- |
| 保留内置，转无损 WebP | 151 张试转合计从 1,214,720 B 到 916,822 B，估算省 **297,898 B（约 298 KB）** | 尺寸、透明度和可见像素均不变，离线可用。还未修改 App 或重新打包验证实际净节省 |
| 直接依赖官方公开 CDN 或登录目录 | 登录后目录仅 2 组 55 张「酷小牛」，与内置 151 张零交集；公开 CDN 只覆盖 141/183 | 无法替代默认、酷币和内置「牛牛」；未登录或离线时完全不可用 |
| 独立版本化资源包 | 可从主 APK 移出接近 **1.21 MB** 的现有图片；真实净节省需扣除下载、校验等代码成本 | 需先确认分发授权。首次按需下载，校验通过后再启用；缓存保留供离线使用；下载失败时可显示表情码或使用小型内置兜底包 |

**建议作为决策参考：** 官方在线表情目录已被证明登录后才能读取，不适合做瘦身来源。若优先保持稳定、离线无感，先选无损 WebP；若优先追求 APK 瘦身，再评估独立资源包。接入官方在线表情目录可作为附加功能，但必须保留内置表情或兜底资源。

## 5. 本地复核材料

以下材料位于本地 `_rev`，不要求随文档提交：

- [云端核查脚本](../_rev/win_ref/emoji_cloud_audit.py)：从仓库根目录执行 `python _rev/win_ref/emoji_cloud_audit.py`，依赖 Pillow、两份 APK 和 AAPT2 资源表转储。脚本不传账号凭据。
- [逐项覆盖率 CSV](../_rev/win_ref/emoji-cloud-coverage.csv)、[官方牛牛预览](../_rev/win_ref/coolapk-16.7.3-niuniu-preview.png)。
- [官方 APK](../_rev/coolapk_versions/20261119_official_download/official.apk)、[c001apk APK](../_rev/apk/no-szlm-monitor-ci698.apk)、[官方资源表](../_rev/win_ref/emoji-table-16.7.3.txt)、[c001apk 资源表](../_rev/win_ref/emoji-table-ours.txt)。
- [牛牛初始化](../_rev/coolapk_versions/20261119_official_download/jadx_fallback/sources/defpackage/zq3.java#L755-L874)、[分组 API](../_rev/coolapk_versions/20261119_official_download/retrofit_contracts_16.7.3.json#L4685-L4713)、[组内清单 API](../_rev/coolapk_versions/20261119_official_download/retrofit_contracts_16.7.3.json#L15148-L15187)、[远程刷新和缓存策略](../_rev/coolapk_versions/20261119_official_download/jadx_fallback/sources/com/coolapk/market/model/EmojiPicturePolicy.java#L7-L11)。
