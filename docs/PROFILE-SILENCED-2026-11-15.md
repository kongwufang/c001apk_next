# 个人主页禁言标识（2026-11-15）

## Python 实测

用户提供已被禁言 UID `32864090`。本机脚本 `_rev/win_ref/probe_user_silenced_20261115.py` 读取模拟器当前设备参数，生成与应用匹配的 v3 请求头，匿名请求以下两个端点，均返回 HTTP 200 和有效用户数据：

- `https://api.coolapk.com/v6/user/space?uid=32864090`（应用个人主页实际使用）
- `https://api.coolapk.com/v6/user/profile?uid=32864090`

请求没有携带账号 Cookie；证书由项目内置 CA 验证，没有无条件关闭 TLS 校验。请求头、设备串和完整响应只留在本机忽略目录，不入库。

| 字段 | 目标 UID 32864090 | 对照 UID 575232 |
| --- | --- | --- |
| `disallowPost` | **1** | **0** |
| `groupid` | 7 | 0 |
| `usergroupid` | 1003 | 100 |
| `block_status` | -1 | 0 |
| `status` | 1 | 1 |
| `isBlackList` / `isBeBlackList` | 0 / 0 | 0 / 0 |

两个端点在这些字段上结果一致。UID 1 和 10937621 返回「用户不存在」，不作为正常账号或禁言账号的判据。

## 官方实现与判定

- `_rev/coolapk_decompiled/jadx_out_c7/sources/com/coolapk/market/model/UserProfile.java:233`：`@SerializedName("disallowPost") Integer getDisallowPost()`。
- 同目录 `defpackage/vzh.java:331`：只有该字段非空且 `== 1` 时，才加入禁言标签。
- `vzh.java:166` 的 `m14003()`：警告图标及「该用户禁言中」文案，背景为 80% 不透明的 `#F44336`，白色文字。
- 同类随后把标签放入 `FLEX` 信息流，与认证、性别、地区和活跃信息并列。

因此采用 **`disallowPost == 1`**，不根据 UID 硬编码，不根据群组数字、个人拉黑关系、昵称/简介或 `block_status` 猜测，也不推断是否永久禁言。

## 应用变更

- `UserProfileResponse.Data` 增加可空 `disallowPost` 和计算属性 `isSilenced`；缺失/null/0/未知值均不显示。
- 主页信息标签中增加带警告图标的红色「该用户禁言中」。标签容器改为可换行 Flexbox，避免窄屏被后续地区或活跃信息挤掉。
- 数据绑定随新的用户资料对象重新计算显隐，恢复正常状态时可隐藏。保留现有认证、公共黑名单、关注、简介和装备展示。
- 4 项 JVM 测试覆盖观察到的标志、正常/缺失/null、拉黑/其他状态不得误报、更新后恢复。

## 验证

- 代码 `98ac239e` 已推 debug 测试线，CI [#660](https://github.com/kongwufang/c001apk_next/actions/runs/43662386733) 通过（4 项新增解析/判定测试、既有测试、混淆构建及签名校验）。未本地编译、未改版本号或正式发版开关。
- 已安装并核对 `_rev/apk/user-silenced-ci660.apk`，SHA-256：`353f6d68d5baab40401653a3bfee12e98830fd2c4aa88ca6716b93bdd52047ce`。
- 模拟器 720×1280、密度 240：目标用户可见标识，边界 `[24,450][182,485]`；对照用户无标识。两者主页均正常加载，认证、装备及其他信息保留。
- 临时密度 320（360dp 宽）下，目标标识边界 `[32,598][242,643]`，未裁切；对照用户仍不显示。测试后已恢复密度 240。
- 已实际查看截图 `_rev/win_ref/shots/user_silenced_240.png`、`user_silenced_360.png`、`user_normal_240_verified.png`、`user_normal_360.png`，前两张显示目标禁言标识，后两张无误报。
- ADB 重复打开同一顶层 Activity 时可能复用旧用户页面，测试先返回再打开对照，并将头部昵称与该 UID 的 API 样本比对，避免拿错用户作结论；不把一次 Intent 复用当成禁言判断问题。
- 响应、UI 结构和断言摘要位于本机忽略目录 `_rev/win_ref/user_silenced_20261115/`，不提交设备参数、请求头或完整个人资料。
