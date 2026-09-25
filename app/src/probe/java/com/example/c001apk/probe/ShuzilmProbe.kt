package com.example.c001apk.probe

import android.content.Context
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
 * 数盟（`cn.shuzilm.core`）SDK 探针 —— 只回答一个问题：
 *
 * > 纯靠 SDK 向官方（`auni.telecome.cn`）要签发，能不能拿到一个被酷安服务端认可的 DUID？
 * > 而不是像现在这样，把别人签好的那一份内置进包里。
 *
 * ## 为什么要探
 *
 * c001apk 的「数字联盟 ID」现在只能**外部输入**：它是数盟服务端在真机上签发的设备标识，
 * 客户端自己造不出来（随机值的实测结果是 `err_request_captcha_v2`，见 `_rev/probe_trust.py`）。
 * 于是只剩下内置别人那一份的路 —— 而一份值被多个安装共用，服务端就会算成同一台设备，
 * 最终落进 `-415 账号过多`（对照见 `_rev/SZLM_CANDIDATE_VERDICT.md`）。
 *
 * 如果 SDK 能在本应用里跑通，每台设备就各自签自己那一份：**一机一值，永不共用**。
 *
 * ## 怎么跑
 *
 * - 构建：`./gradlew :app:assembleRelease -PshuzilmProbe=true`
 *   （不开这个开关，本文件与 `src/probe/` 下的 dex/so/assets 都不参与构建）
 * - 触发：应用启动后自动执行，无需交互；结果只记一次，重跑要清应用数据
 * - 看结果：Toast + logcat（`adb logcat -s ShuzilmProbe`）+ `Android/data/com.example.c001apk/files/shuzilm_probe.txt`
 *
 * ## 判读
 *
 * | verdict | 含义 |
 * |---|---|
 * | `signed` | 返回值以 `D` 开头 = 官方签发成功，闭环成立 |
 * | `rejected` | 有返回但不是 `D` 开头 = SDK 跑起来了但服务端没给 |
 * | `empty` | 回调是空 = 同上，且更彻底 |
 * | `timeout` | 25 s 内没回调 = 请求没出网 / 端点不可达 |
 * | `sdk_crash` | SDK 自己的线程抛了未捕获异常，具体见 `detail` |
 * | `error` | 探针自身抛异常，看 logcat 堆栈 |
 *
 * ## 为什么是三张 dex
 *
 * `cn.shuzilm.**` 在官方 APK 里散在三张 dex 上，只带 classes01 会死在
 * `DUHelper.o.run()` 的 `NoClassDefFoundError: cn.shuzilm.core.AIClient`：
 *
 * | dex | 含有的 cn.shuzilm 类 |
 * |---|---|
 * | `classes01`（47 个） | `Main`、`DUHelper`、`Listener`、`dl`、`a`–`z`、`R`… |
 * | `classes09`（2 个） | `aa`、`BuildConfig` —— classes01 引用但没定义 |
 * | `classes10`（6 个） | `AIClient` 及内部类 —— classes01 引用但没定义 |
 *
 * 这三张是逐张解析 class_defs + type_ids 比对出来的最小集合。
 *
 * ## 加载方式与取舍
 *
 * SDK 的 class 不从源码编译进来，而是把这三张 dex 塞进 assets、运行时独立加载、反射调用：
 *
 * 1. 它们是酷安**整个** dex 的前几张（18.7 + 8.2 + 9.3 MB），里面还有 `com.coolapk.*`
 *    和一堆第三方库，直接合进本工程会大面积类名冲突；
 * 2. 只挑那 55 个类摘出来也不行 —— 它们对同 dex 内其它类的引用会一起断掉；
 * 3. 独立 loader 天然隔离，探针砍掉时主工程零残留。
 *
 * API 29+ 走 [InMemoryDexClassLoader]（不落盘）；Android 10 才加上带 `librarySearchPath`
 * 的三参构造，Android 9 只有二参版本，而 SDK 内部要 `System.loadLibrary("du")`，
 * 缺库搜索路径会 `UnsatisfiedLinkError` —— 所以那边走落盘 [DexClassLoader]。
 *
 * `Listener` 是 SDK 的接口，本工程编译期不认识它，用 [Proxy] 动态代理实现。
 */
object ShuzilmProbe {

    private const val TAG = "ShuzilmProbe"

    /** 酷安官方接入用的数盟 apiKey（42 字符、`11` 前缀，SDK 侧只自校验这个格式） */
    private const val API_KEY = "11e7b222083a4b732b4b14811f6fc05995a01415eb"

    /** 必须按 classes01 → 09 → 10 的顺序，且是 classes01 引用缺失类的最小闭包（见类注释） */
    private val DEX_ASSETS = listOf("shuzilm01.dex", "shuzilm09.dex", "shuzilm10.dex")

    private const val PREFS = "shuzilm_probe"
    private const val CALLBACK_TIMEOUT_SEC = 25L

    private const val KEY_VERDICT = "verdict"
    private const val KEY_DID = "did"
    private const val KEY_DEX = "dex"
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
     * 入口。幂等；已有结果就不重复跑（想重跑清应用数据）。
     * 由 `MyApplication` 反射调用，所以是 `@JvmStatic`。
     */
    @JvmStatic
    fun maybeRun(ctx: Context) {
        val app = ctx.applicationContext
        if (app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_VERDICT)) {
            Log.i(TAG, "已有结果，跳过（清应用数据可重跑）")
            return
        }
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

    /**
     * 子线程：把 dex 准备好并建出 ClassLoader（首次要 dexopt 34 MB，几秒到二十几秒）。
     *
     * 两条路都试，谁先成用谁。
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

        try {
            val mainCls = Class.forName("cn.shuzilm.core.Main", true, loader)
            val listenerCls = Class.forName("cn.shuzilm.core.Listener", true, loader)

            mainCls.getMethod(
                "init", Context::class.java, String::class.java, java.lang.Boolean.TYPE
            ).invoke(null, ctx, API_KEY, false)
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
            ).invoke(null, ctx, "coolapk", "", true, listener)
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
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, "数盟探针：$verdict\n${value ?: crash[0].orEmpty()}", Toast.LENGTH_LONG)
                    .show()
            }
        }, "shuzilm-await").start()
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
     * API 28 及以下才走这条路：把 dex 拷到 `filesDir/probe/` 并设只读。
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
}
