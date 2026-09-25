package com.example.c001apk.probe

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 数盟（`cn.shuzilm.core`）SDK 探针 —— 回答两个问题：
 *
 * 1. 纯靠 SDK 向官方要签发，能不能拿到一个被酷安服务端认可的 DUID？
 * 2. 如果不能，**服务端凭什么判定调用方不是官方 App**？
 *
 * ## 已有的结论（见 `_rev/SZLM_SDK_SIGNING_VERDICT.md`）
 *
 * 第一轮实测：SDK 全链路跑通（init / setConfig / getQueryID / 通信 / 回调全绿），
 * 但拿到的 `device_id` 是 **36 个 0**。同一台设备上官方酷安拿的是真值
 * `DUFPnec5dST2pTeImYGBClyhOnoUecHYyWg9`，两边云控都落 32 条键、一一对应。
 * 唯一变量是**包名**（+签名）。
 *
 * 静态侧只查到：`DUHelper` 那批 native 方法几乎全部接收 `Context`
 * （`query(Context,…)` / `onEvent(Context,…)` / `dGZvcmRQ(Context,…)` …），
 * 而 Java 层只调了 `getPackageInfo(pkg, 0)`，**不带签名** —— 采集在 native，
 * `libdu.so` 串表运行期解密，静态看不到 JNI 方法名。
 *
 * ## 这一轮在测什么
 *
 * 不再猜，直接改实验变量：把传给 SDK 的 [Context] 包一层 [SpoofContext]，
 * 让 `getPackageName()` 返回 [SPOOF_PKG]。设备上装着官方酷安，于是
 * `getPackageManager().getPackageInfo(SPOOF_PKG, …)` 会查到**官方酷安真实的
 * PackageInfo 和签名** —— 也就是说这一层伪装同时把包名和签名都对齐了官方。
 *
 * - 拿到真值 → 判据就是 (包名, 签名)，结论闭环，且机制明确
 * - 还是全零 → 判据在别处（native 直接读 `/proc/self/cmdline`、或设备指纹、
 *   或 apiKey 与服务端注册的 profile 绑定），继续收窄
 *
 * ## 怎么跑
 *
 * - 构建：`./gradlew :app:assembleRelease -PshuzilmProbe=true`
 *   （不开这个开关，本文件与 `src/probe/` 下的 dex/so/assets 都不参与构建）
 * - 触发：应用启动后自动执行，无需交互
 * - 看结果：Toast + logcat（`adb logcat -s ShuzilmProbe`）+ `Android/data/com.example.c001apk/files/shuzilm_probe.txt`
 * - 改实验：调 [PROBE_VERSION] 即可强制重跑（不用清应用数据）
 *
 * ## 判读
 *
 * | verdict | 含义 |
 * |---|---|
 * | `signed` | 返回值以 `D` 开头 = 官方签发成功，闭环成立 |
 * | `rejected` | 有返回但不是 `D` 开头（全零就是这个） |
 * | `empty` | 回调是空 |
 * | `timeout` | 25 s 内没回调 = 请求没出网 / 端点不可达 |
 * | `sdk_crash` | SDK 自己线程抛了未捕获异常，具体见 `detail` |
 * | `error` | 探针自身抛异常，看 logcat 堆栈 |
 *
 * ## 为什么是三张 dex
 *
 * `cn.shuzilm.**` 在官方 APK 里散在三张 dex 上，只带 classes01 会死在
 * `DUHelper.o.run()` 的 `NoClassDefFoundError: cn.shuzilm.core.AIClient`：
 *
 * | dex | 含有的 cn.shuzilm 类 |
 * |---|---|
 * | `shuzilm01.dex`（47 个） | `Main`、`DUHelper`、`Listener`、`dl`、`a`–`z`、`R`… |
 * | `shuzilm09.dex`（2 个） | `aa`、`BuildConfig` —— classes01 引用但没定义 |
 * | `shuzilm10.dex`（6 个） | `AIClient` 及内部类 —— classes01 引用但没定义 |
 *
 * 逐张解析 class_defs + type_ids 比对出来的最小闭包（脚本 `_rev/_dex_scan.py`）。
 *
 * ## 加载方式与取舍
 *
 * 独立 ClassLoader 加载，不把 SDK 编进源码：这三张 dex 里还有 `com.coolapk.*`
 * 和大量第三方库，直接合进来会大面积类名冲突；只挑那 55 个类摘出来也不行 ——
 * 它们对同 dex 内其它类的引用会一起断掉。
 *
 * API 29+ 走 [InMemoryDexClassLoader]（不落盘）；Android 9 只有二参版本，
 * 而 SDK 内部要 `System.loadLibrary("du")`，缺库搜索路径会 `UnsatisfiedLinkError`
 * —— 那边走落盘 [DexClassLoader]。
 *
 * `Listener` 是 SDK 的接口，本工程编译期不认识它，用 [Proxy] 动态代理实现。
 */
object ShuzilmProbe {

    private const val TAG = "ShuzilmProbe"

    /** 酷安官方接入用的数盟 apiKey（42 字符、`11` 前缀，SDK 侧只自校验这个格式） */
    private const val API_KEY = "11e7b222083a4b732b4b14811f6fc05995a01415eb"

    /**
     * 传给 SDK 的包名伪装目标。`null` = 用真实包名（基线）。
     *
     * 设成 `com.coolapk.market` 时，设备上装着的官方酷安会让
     * `getPackageManager().getPackageInfo(该包名, …)` 返回**官方真实的签名与安装信息**，
     * 于是「包名 + 签名」这两件事一起对齐了官方。
     */
    private val SPOOF_PKG: String? = "com.coolapk.market"

    /**
     * 实验版本号。改它就强制重跑（结果只对同版本有效）。
     *
     * - `baseline-1`：真实包名 → `rejected` / 全零（已完成）
     * - `spoof-pkg-1`：伪装包名 → 本轮
     */
    private const val PROBE_VERSION = "spoof-pkg-1"

    /** 必须按 classes01 → 09 → 10 的顺序，且是 classes01 引用缺失类的最小闭包（见类注释） */
    private val DEX_ASSETS = listOf("shuzilm01.dex", "shuzilm09.dex", "shuzilm10.dex")

    private const val PREFS = "shuzilm_probe"
    private const val CALLBACK_TIMEOUT_SEC = 25L

    private const val KEY_VERSION = "probe_version"
    private const val KEY_VERDICT = "verdict"
    private const val KEY_DID = "did"
    private const val KEY_DETAIL = "detail"

    private val CONFIGS = listOf("pkglist" to "1", "operation" to "1", "cdlmt" to "1")

    /**
     * 持有内存 dex 的引用。
     *
     * [InMemoryDexClassLoader] **不拷贝** buffer，直接引用它指向的内存；一旦被 GC 回收，
     * 之后解析这些类里的任何东西都是野指针。所以必须挂在这里不让它死。
     */
    @Volatile
    private var dexBuffers: List<ByteBuffer>? = null

    /**
     * 入口。同一 [PROBE_VERSION] 只跑一次；换版本号即重跑。
     * 由 `MyApplication` 反射调用，所以是 `@JvmStatic`。
     */
    @JvmStatic
    fun maybeRun(ctx: Context) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getString(KEY_VERSION, null) == PROBE_VERSION && sp.contains(KEY_VERDICT)) {
            Log.i(TAG, "版本 $PROBE_VERSION 已有结果，跳过")
            return
        }
        sp.edit().clear().putString(KEY_VERSION, PROBE_VERSION).apply()
        report(app, "run", "$PROBE_VERSION  spoofPkg=${SPOOF_PKG ?: "(真实包名)"}")

        // 清掉数盟 SDK 自己的落盘：它会把 device_id 缓存进 _dna.xml，
        // 不清的话这次实验读到的还是上一轮的旧值，白跑
        clearSdkCache(app)

        Thread({
            val prepared = runCatching { prepare(app) }
            val loader = prepared.getOrNull()
            if (loader == null) {
                val e = prepared.exceptionOrNull()
                Log.e(TAG, "prepare 失败", e)
                report(app, KEY_VERDICT, "error")
                report(app, KEY_DETAIL, "prepare: $e")
                return@Thread
            }
            // SDK 的 Main.init 里有 `if (Looper.myLooper() == null) Looper.prepare()`：
            // 在子线程调用会造出一个永不 loop 的 Looper，之后 post 进去的回调全成死信。
            // 所以 init / getQueryID 都放主线程。
            Handler(Looper.getMainLooper()).post { call(app, loader) }
        }, "shuzilm-probe").start()
    }

    /** 删掉数盟 SDK 的 SP，强制它重新向服务端要签发（否则读缓存，实验无意义） */
    private fun clearSdkCache(ctx: Context) {
        val dir = File(ctx.dataDir, "shared_prefs")
        val names = listOf("${ctx.packageName}_dna.xml", "${ctx.packageName}_prefs.xml")
        for (n in names) {
            val f = File(dir, n)
            if (f.exists()) {
                val ok = f.delete()
                Log.i(TAG, "清 SDK 落盘 ${f.name} -> $ok")
            }
        }
    }

    /**
     * 子线程：把 dex 准备好并建出 ClassLoader（首次要 dexopt 34 MB，几秒到二十几秒）。
     */
    private fun prepare(ctx: Context): ClassLoader {
        val t0 = System.currentTimeMillis()
        val nativeDir = ctx.applicationInfo.nativeLibraryDir
        val parent = ShuzilmProbe::class.java.classLoader

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val bufs = DEX_ASSETS.map { readAsset(ctx, it) }
                dexBuffers = bufs
                val l = InMemoryDexClassLoader(bufs.toTypedArray(), nativeDir, parent)
                Class.forName("cn.shuzilm.core.Main", true, l)
                l
            }.onSuccess {
                Log.i(TAG, "loader=InMemory ${DEX_ASSETS.size}dex 就绪 ${System.currentTimeMillis() - t0} ms")
                return it
            }.onFailure {
                Log.w(TAG, "InMemoryDexClassLoader 不可用，回退落盘方案", it)
            }
        }

        val paths = DEX_ASSETS.map { copyAsset(ctx, it).absolutePath }
        val oat = File(ctx.filesDir, "probe/oat").apply { mkdirs() }
        val fallback = DexClassLoader(
            paths.joinToString(File.pathSeparator), oat.absolutePath, nativeDir, parent
        )
        // 构造是懒加载，真正校验在这里；类缺失/结构坏会在这一行炸出来
        return runCatching {
            Class.forName("cn.shuzilm.core.Main", true, fallback)
            fallback
        }.onSuccess {
            Log.i(TAG, "loader=DexClassLoader ${DEX_ASSETS.size}dex 就绪 ${System.currentTimeMillis() - t0} ms")
        }.getOrElse { throw it }
    }

    /** 主线程：init + 取 ID（回调异步回来，交给 [awaitCallback] 收） */
    private fun call(ctx: Context, loader: ClassLoader) {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<String>(1)
        val crash = arrayOfNulls<String>(1)
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        // SDK 拿到 ID 之后还会在自己的线程池里继续跑（AIClient 那条链），那里抛的异常
        // 走全局 handler，探针的 try/catch 接不到。临时接管，把崩溃记成结果 ——
        // 否则应用会反复重启，logcat 被刷屏反而看不出真正原因（第一轮就是这样）。
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(TAG, "SDK 线程未捕获异常：${t.name}", e)
            crash[0] = "${t.name}: $e"
            latch.countDown()
        }

        // 交给 SDK 的就是这一层包装；探针自己的落盘/日志仍用真实 ctx
        val sdkCtx: Context = SPOOF_PKG?.let { SpoofContext(ctx, it) } ?: ctx
        Log.i(TAG, "传给 SDK 的包名 = ${sdkCtx.packageName}（真实 ${ctx.packageName}）")

        try {
            val mainCls = Class.forName("cn.shuzilm.core.Main", true, loader)
            val listenerCls = Class.forName("cn.shuzilm.core.Listener", true, loader)

            mainCls.getMethod(
                "init", Context::class.java, String::class.java, java.lang.Boolean.TYPE
            ).invoke(null, sdkCtx, API_KEY, false)
            Log.i(TAG, "Main.init(apiKey) ok")

            // 酷安接入时打的三条；失败不影响取 ID，只记日志
            val setConfig = mainCls.getMethod("setConfig", String::class.java, String::class.java)
            for ((k, v) in CONFIGS) {
                runCatching { setConfig.invoke(null, k, v) }
                    .onFailure { Log.w(TAG, "setConfig($k) 失败：${it.message}") }
            }

            val listener = Proxy.newProxyInstance(loader, arrayOf(listenerCls)) { _, method, args ->
                if (method.name == "handler") {
                    val value = args?.firstOrNull()?.toString()
                    Log.i(TAG, "Listener.handler -> $value")
                    holder[0] = value
                    latch.countDown()
                }
                null
            }
            // 走酷安那条重载：getQueryID(ctx, channel, "", z=true, listener)
            mainCls.getMethod(
                "getQueryID", Context::class.java, String::class.java,
                String::class.java, java.lang.Boolean.TYPE, listenerCls,
            ).invoke(null, sdkCtx, "coolapk", "", true, listener)
            Log.i(TAG, "getQueryID 已下发，等回调（≤${CALLBACK_TIMEOUT_SEC}s）")
        } catch (t: Throwable) {
            Log.e(TAG, "call 失败", t)
            Thread.setDefaultUncaughtExceptionHandler(previous)
            report(ctx, KEY_VERDICT, "error")
            report(ctx, KEY_DETAIL, "call: $t")
            return
        }
        awaitCallback(ctx, latch, holder, crash) {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    /** 子线程：等回调、判定、落地；窗口结束把全局异常处理器还回去 */
    private fun awaitCallback(
        ctx: Context,
        latch: CountDownLatch,
        holder: Array<String?>,
        crash: Array<String?>,
        restore: () -> Unit,
    ) {
        Thread({
            val got = latch.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS)
            restore()
            val value = holder[0]
            val verdict = when {
                crash[0] != null -> "sdk_crash"
                !got -> "timeout"
                value.isNullOrEmpty() -> "empty"
                value.startsWith("D") -> "signed"
                else -> "rejected"
            }
            Log.i(TAG, "verdict=$verdict  value=$value  crash=${crash[0]}")
            report(ctx, KEY_VERDICT, verdict)
            report(ctx, KEY_DID, value.orEmpty())
            crash[0]?.let { report(ctx, KEY_DETAIL, it) }
            // 顺带把 SDK 落盘的真实结果也捞出来对照（它才是 native 的最终输出）
            report(ctx, "dna", readDna(ctx))
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, "数盟探针：$verdict\n${value ?: crash[0].orEmpty()}", Toast.LENGTH_LONG)
                    .show()
            }
        }, "shuzilm-await").start()
    }

    /** 读数盟 SDK 落盘的 `_dna.xml`（它记的才是 native 认定的 device_id） */
    private fun readDna(ctx: Context): String {
        val f = File(File(ctx.dataDir, "shared_prefs"), "${ctx.packageName}_dna.xml")
        if (!f.exists()) return "(无 _dna.xml)"
        return runCatching {
            Regex("<string name=\"device_id\">([^<]*)</string>")
                .find(f.readText())?.groupValues?.get(1) ?: "(无 device_id 键)"
        }.getOrElse { "<读取失败 ${it.message}>" }
    }

    /** 读 assets 成 direct buffer（[InMemoryDexClassLoader] 直接引用这块内存，不能是临时对象） */
    private fun readAsset(ctx: Context, asset: String): ByteBuffer {
        val bytes = ctx.assets.open(asset).use { it.readBytes() }
        Log.i(TAG, "assets/$asset 读入 ${bytes.size} B")
        return ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }
    }

    /**
     * 落盘方案用：把 dex 拷到 `filesDir/probe/` 并设只读。
     *
     * 必须只读，否则 [DexClassLoader] 会拒绝加载（Android 14 起强制，低版本也认这个约定）。
     */
    private fun copyAsset(ctx: Context, asset: String): File {
        val dir = File(ctx.filesDir, "probe").apply { mkdirs() }
        val out = File(dir, asset)
        if (!out.exists() || out.length() == 0L) {
            ctx.assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
            Log.i(TAG, "assets/$asset -> ${out.absolutePath}（${out.length()} B）")
        }
        if (out.canWrite()) out.setReadOnly()
        return out
    }

    /**
     * 结果落 3 处，取最方便的那一处看：
     *  1. logcat（`adb logcat -s ShuzilmProbe`）
     *  2. SharedPreferences `shuzilm_probe`
     *  3. `Android/data/com.example.c001apk/files/shuzilm_probe.txt` —— 免 root，文件管理器直接点开
     */
    private fun report(ctx: Context, key: String, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
        runCatching {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            dir.mkdirs()
            File(dir, "shuzilm_probe.txt").appendText("$key=$value\n")
        }.onFailure { Log.w(TAG, "落盘失败：${it.message}") }
    }

    /**
     * 只包一层包名，其余全部走真身 —— 这样 SDK 通过
     * `getPackageManager().getPackageInfo(getPackageName(), …)` 拿到的会是
     * **官方酷安的 PackageInfo 与真实签名**（设备上装着官方版）。
     */
    private class SpoofContext(base: Context, private val spoofPkg: String) : ContextWrapper(base) {
        override fun getPackageName(): String = spoofPkg

        /** 部分 API 走这个而不是 getPackageName()，一起改掉才不漏 */
        override fun getOpPackageName(): String = spoofPkg

        override fun getApplicationInfo(): ApplicationInfo =
            runCatching { baseContext.packageManager.getApplicationInfo(spoofPkg, 0) }
                .getOrElse { super.getApplicationInfo() }
    }
}
