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

`debug2` 分支 push 即自动带探针构建，产物在本次 run 的 **Artifacts** 里
（`release-c001apk_next-*.apk`）。其它分支要手动触发：Actions → CI → **Run workflow**
→ 勾 `shuzilmProbe`。

`debug2` 与 `workflow_dispatch` 都不会走到 Release 发布步骤，所以探针包不会被当正式版发出去。

本地：

```bash
./gradlew :app:assembleRelease -Pchannel=debug -PshuzilmProbe=true
```

## 看结果

装到真机，启动即自动执行（无需交互），结果三处可看：

- **Toast**：启动几秒后弹出 `数盟探针：signed DUxxxx…`
- **文件**：`Android/data/com.example.c001apk/files/shuzilm_probe.txt` —— 免 root
- **logcat**：`adb logcat -s ShuzilmProbe`

| verdict | 含义 |
|---|---|
| `signed` | 返回值以 `D` 开头 = **官方签发成功，闭环成立** |
| `rejected` | 有返回但不是 `D` 开头 = SDK 跑起来了但服务端没给 |
| `empty` | 回调是空 |
| `timeout` | 25 s 内没回调 = 请求没出网 / 端点不可达 |
| `sdk_crash` | SDK 自己线程抛了未捕获异常，具体见 `detail` |
| `error` | 探针自身抛异常，看 logcat 堆栈 |

判据来自酷安自己的接入层：`ShuzilmSDKManager.initID` 的成功条件就是**返回值以 `"D"` 开头**。
结果只记一次（SharedPreferences `shuzilm_probe`），想重跑先清应用数据。

## 如果拿到 `signed`，下一步验证

把那个值填进「设置 → 高级 → 数字联盟ID」，打开任意帖子详情页；或者直接命令行：

```bash
python3 _rev/probe_szlm_candidate.py DU<新值>
```

- `feed/detail` 返回 200 = 服务端认可，闭环真正走完
- 仍被要求验证码 = 签发出的 ID 与酷安认可的那套不是一回事，说明 `pkg`（上报包名
  `com.example.c001apk`）与 apiKey（酷安的）对不上，服务端做了绑定校验

这一步是探针唯一真正的未知数：`DUHelper.n()` 组的上报体里带 `pkg`，
而 apiKey `11e7b222…` 属于酷安。静态分析到此为止（native 侧的 RSA 加密挡住了字段级确认）。

## 为什么是三张 dex

`cn.shuzilm.**` 在官方 APK 里散在三张 dex 上。只带 `classes01` 会死在
`DUHelper.o.run()` 的 `NoClassDefFoundError: cn.shuzilm.core.AIClient`（第一轮实测踩到过）：

| dex | 含有的 cn.shuzilm 类 | 大小 |
|---|---|---|
| `shuzilm01.dex`（`classes01`，47 个） | `Main`、`DUHelper`、`Listener`、`dl`、`a`–`z`、`R` | 18.7 MB |
| `shuzilm09.dex`（`classes09`，2 个） | `aa`、`BuildConfig` —— classes01 引用但没定义 | 8.2 MB |
| `shuzilm10.dex`（`classes10`，6 个） | `AIClient` 及内部类 —— classes01 引用但没定义 | 9.3 MB |

这三张是逐张解析 `class_defs` + `type_ids` 比对出来的最小闭包（脚本 `_rev/_dex_scan.py`）。
挑出那 55 个类单独打包也不行——它们对同 dex 内其它类的引用会一起断掉。

| 其它资产 | 来源 |
|---|---|
| `jniLibs/arm64-v8a/libdu.so`（1.4 MB） | 官方 APK `lib/arm64-v8a/libdu.so` |
| `assets/cn.shuzilm.config.json`（165 B） | 官方 APK `assets/cn.shuzilm.config.json` |

`libplt-base.so`（快手 xhook/bytehook）**不需要**——那是穿山甲/快手广告 SDK 带的，
`libdu.so` 的静态依赖只有 `libc/libdl/liblog/libm/libz`。

## 设计取舍

- **不把 SDK 编进源码**：这三张 dex 里还有 `com.coolapk.*` 和大量第三方库，
  直接合进来会大面积类名冲突。走独立 ClassLoader 隔离。
- **API 29+ 走内存加载**：Android 10 才加上带 `librarySearchPath` 的三参
  `InMemoryDexClassLoader(ByteBuffer[], String, ClassLoader)`。Android 9 只有二参版本
  （实测 `NoSuchMethodError`），而 SDK 内部要 `System.loadLibrary("du")`，
  缺库搜索路径会 `UnsatisfiedLinkError`——所以那边走落盘 `DexClassLoader`（实测可用）。
- **临时接管全局未捕获异常处理器**：SDK 拿到 ID 之后还在自己的线程池里跑
  （`AIClient` 那条链），那里抛的异常探针的 `try/catch` 接不到。不接管的话应用会反复
  崩溃重启，logcat 被刷屏反而看不出原因。窗口结束即还原。
- **`Listener` 用动态代理**：本工程编译期不认识这个接口，没必要为探针引入编译期依赖。
- **默认关闭**：不传 `-PshuzilmProbe=true` 时，`src/probe/` 下的源码 / assets / jniLibs
  一律不参与构建，产物零差异。

## 代价

探针包比正常包大约 **+12 MB**（三张 dex 压缩后进 APK）。这是刻意隔离的代价，
真要长期用应该做 dex 裁剪，而不是把三张完整 dex 背着走。
