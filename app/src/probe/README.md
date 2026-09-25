# 数盟 SDK 探针（`-PshuzilmProbe=true`）

只回答一个问题：**纯靠 SDK 向官方要签发，能不能拿到一个被酷安服务端认可的 DUID？**
而不是像现在这样，把别人签好的那一份内置进包里。

## 为什么值得试

c001apk 的「数字联盟 ID」是数盟（`cn.shuzilm.core`）服务端在真机上签发的设备标识，
客户端造不出来——随机值的实测结果是 `err_request_captcha_v2`（见 `_rev/probe_trust.py`）。
于是只剩内置别人那一份的路，而**一份值被多个安装共用，服务端就把它算成同一台设备**，
最终落进 `-415 账号过多`（完整对照见 `_rev/SZLM_CANDIDATE_VERDICT.md`）。

SDK 如果在应用内跑得通，每台设备就各自签自己那一份：**一机一值，永不共用**。

## 跑一次

探针**不跟 push 走**，要手动触发：

1. Actions → CI → **Run workflow**
2. 勾上 **`shuzilmProbe`**
3. 跑完在本次 run 的 **Artifacts** 里取 `release-*.apk`

`workflow_dispatch` 触发时不会走 Release 发布步骤，所以探针包不会被当成正式版发出去。

本地同样可行：

```bash
./gradlew :app:assembleRelease -Pchannel=debug -PshuzilmProbe=true
```

## 看结果

装到真机，启动即自动执行（无需交互）。结果三处可看，取最方便的：

- **Toast**：启动几秒后弹出 `数盟探针：signed DUxxxx…`
- **文件**：`Android/data/com.example.c001apk/files/shuzilm_probe.txt` —— 免 root，文件管理器直接点开
- **logcat**：`adb logcat -s ShuzilmProbe`

| verdict | 含义 |
|---|---|
| `signed` | 返回值以 `D` 开头 = **官方签发成功，闭环成立** |
| `rejected` | 有返回但不是 `D` 开头 = SDK 跑起来了但服务端没给 |
| `empty` | 回调是空 |
| `timeout` | 25 s 内没回调 = 请求没出网 / 端点不可达 |
| `error` | 抛异常，看 logcat 堆栈 |

判据来自酷安自己的接入层：`ShuzilmSDKManager.initID` 的成功条件就是**返回值以 `"D"` 开头**。

结果只记一次（写进 SharedPreferences `shuzilm_probe`），想重跑先清应用数据。

## 如果拿到 `signed`，下一步验证

把那个值填进「设置 → 高级 → 数字联盟ID」，然后打开任意帖子详情页：

- 能正常加载 = 服务端认可，闭环真正走完
- 仍被要求验证码 = 签发出的 ID 与酷安认可的那套不是一回事，说明 `pkg`（上报包名
  `com.example.c001apk`）与 apiKey（酷安的）对不上，服务端做了绑定校验

这一步是这次探针唯一真正的未知数：`DUHelper.n()` 组的上报体里带 `pkg`，
而 apiKey `11e7b222…` 属于酷安。静态分析到此为止（native 侧的 RSA 加密挡住了字段级确认）。

## 资产来源与校验

| 文件 | 来源 | SHA-256（前 16 位） |
|---|---|---|
| `assets/shuzilm.dex` | 官方 APK 还原后的 `classes01.dex`（与 `dex_rebuilt/classes1.dex` 同哈希） | `068F6BA5F15E1D83` |
| `jniLibs/arm64-v8a/libdu.so` | 官方 APK `lib/arm64-v8a/libdu.so` | `9E08C9323DD1BA43` |
| `assets/cn.shuzilm.config.json` | 官方 APK `assets/cn.shuzilm.config.json` | — |

dex 结构已核：`magic=dex\n035`、file_size 与实长一致、16691 个 class_def，
`cn.shuzilm.core.Main`（PUBLIC, super=Object）、`Listener`（INTERFACE+ABSTRACT+PUBLIC,
super=Object）、`DUHelper`（super=`android.telephony.PhoneStateListener`）的 class_data 都在。

`libplt-base.so`（快手 xhook/bytehook）**不需要**——那是穿山甲/快手广告 SDK 带的，
`libdu.so` 的静态依赖只有 `libc/libdl/liblog/libm/libz`。

## 设计取舍

- **不把 SDK 编进源码**：那张 18.7 MB 的 dex 是酷安**整个**第一个 dex，里面还有
  `com.coolapk.*` 和大量第三方库，直接合进来会大面积类名冲突。走独立 ClassLoader 隔离。
- **不能只挑那 34 个类**：`cn.shuzilm.core.*` 用到的 `defpackage.bmj` 等依赖也在同一张 dex 里，
  单独摘出来会缺依赖（已核，dex 内自包含）。
- **优先内存加载**：Android 14 起 `DexClassLoader` 拒绝加载可写目录下的 dex，
  `InMemoryDexClassLoader` 天然没这个约束。真机 ROM 差异大，落盘方案留作回退。
- **`Listener` 用动态代理**：本工程编译期不认识这个接口，没必要为探针引入编译期依赖。
- **默认关闭**：不传 `-PshuzilmProbe=true` 时，`src/probe/` 下的源码 / assets / jniLibs
  一律不参与构建，产物零差异。

## 代价

探针包比正常包大约 **+20 MB**（dex 压缩后约 7 MB 进 APK）。这是刻意隔离的代价，
真要长期用应该做 dex 裁剪，而不是把整张 dex 背着走。
