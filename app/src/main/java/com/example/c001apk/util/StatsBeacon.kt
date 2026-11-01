package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.example.c001apk.BuildConfig
import com.example.c001apk.MyApplication
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 静默心跳上报（2026-10-25 ✓）
 *
 * ## 为什么要有它（用户原话）
 * > 「为了保证二次开发的人把那个地方的升级换成自己的实现而导致统计不到
 * >   或者用户关闭了自动更新，在其他地方我们再实现一遍，用新的接口，
 * >   服务器建新的文件夹，这个地方就静默上报，
 * >   只要 app 在前台且无论是否自动检查更新开启还是关闭都每一段时间上报一次」
 *
 * ⇒ 所以本类和 [com.example.c001apk.util.UpdateChecker] **零耦合** ✗：
 *   - 不读 `PrefManager.isCheckUpdateStable` ✗（自适应开关无关 ✓）
 *   - 不调 `UpdateChecker` 里任何函数 ✗
 *   - 请求头自己拼一份 ✓（哪怕别人把 `UpdateChecker` 整个换掉也不影响这里 ✓）
 *   - 签名也自己算一份 ✓（同理）
 *
 * ## 什么时候报
 * **只在前台** ✓
 *   `Application.ActivityLifecycleCallbacks` 数 started 的 Activity，
 *   0 → 1 开始循环 ✓ 1 → 0 停 ✓（息屏 / 切后台 / 杀掉都会停 ✓）
 *
 * ## 报多勤
 * **服务器说了算** ✓
 *   接口返回 `{"interval": 60}` ⇒ 下一次隔 60 秒 ✓
 *   服务器改 `broadcast/beat.json` 即可调 ✓ 客户端不写死 ✗
 *   拿到响应之前用 [DEFAULT_INTERVAL_SEC] 兜底 ✓
 *
 * ## 和升级检查的关系
 * 一次启动会报**两次** ✓（升级接口一次 `src=update`，这里一次 `src=beat`）
 *   用户明确接受 ✓「这样每次启动实际上会报两次」
 *   统计靠 `ssaid` 去重 ✓ 所以不会算成两个人 ✓
 *
 * ## 失败怎么办
 * **完全静默** ✓ 不弹任何东西 ✗ 不打 error 日志 ✗
 * 网络失败就按默认间隔重试 ✓
 */
@Singleton
class StatsBeacon @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {

    companion object {
        /** 服务端新接口（独立于升级接口 ✓） */
        private const val URL = "https://service.houlangs.cn/c001apk/heartbeat/"

        /** 拿不到服务器 interval 时的兜底（秒）—— 跟 beat.json 现在的 30 秒对齐 ✓ */
        private const val DEFAULT_INTERVAL_SEC = 30

        /**
         * **请求失败后的退避**（秒 = 30 分钟）✓ 2026-10-25 用户要求
         *
         * > 「心跳要是收不到服务器端的响应的时候就停止30分钟，
         * >   防止用户太多我服务器扛不住又没办法下发更长的时间间隔」
         *
         * 为什么需要：正常情况下「报多勤」是**服务器说**的（返回 interval ✓）
         * ⇒ 但服务器一旦被打挂 / 超时 / 500，它**没法下发**任何间隔 ✗
         * ⇒ 客户端如果还按 30 秒猛冲，只会把服务器压得更死 ✗
         * ⇒ 所以**收不到响应就自己退避 30 分钟** ✓ 让服务器缓过来 ✓
         *
         * ⚠️ 只对「失败」生效 ✗
         *   成功拿到 interval（哪怕 30 秒）照常按服务器说的来 ✓
         */
        private const val FAIL_BACKOFF_SEC = 30 * 60

        /** 兜底夹紧：别让服务器手滑写 0 把客户端打成 DDoS ✗ */
        private const val MIN_INTERVAL_SEC = 30
        private const val MAX_INTERVAL_SEC = 3600

        /** 前台 Activity 计数（跨 Activity 切换不重启循环 ✓） */
        private var started = 0

        /** 同一秒内别重复报（配置变更会走 stop→start ✓ 防抖） */
        private var lastBeatAt = 0L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 服务器 Set-Cookie => 记住 => 下次所有上报都带上 ✓（2026-10-31）
            .addInterceptor(ServerCookieStore.interceptor())
            // ★ SSL 策略跟着「校验 SSL 证书」开关走 ✓（2026-10-31 用户要求：
            //   「新加的所有接口得遵循他的校验开关，以便我可以网络尝试」）
            .let { SslVerify.apply(it) }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 在 `MyApplication.onCreate` 里调一次 ✓ */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                if (started == 1) startLoop()
            }

            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0) {
                    started = 0
                    stopLoop()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun startLoop() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                // ⚠️ 异常（beat() 自己抛了）也要退避 ✓ 不能用 DEFAULT_INTERVAL_SEC ✗
                //   那是"正常但没配置"的兜底 ✓ 30 秒猛冲正是用户要防的情况 ✗
                val wait = runCatching { beat() }.getOrDefault(FAIL_BACKOFF_SEC)
                delay(wait.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC) * 1000L)
            }
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
    }

    /**
     * 报一次，返回**服务器指定**的下次间隔（秒）
     *
     * 任何异常都不抛 ✓ 返回兜底值 ✓
     */
    private suspend fun beat(): Int {
        // 防抖：配置变更 / 快速切页会触发 stop→start，1 秒内不重复报
        val now = SystemClock.elapsedRealtime()
        if (now - lastBeatAt < 1000L) return DEFAULT_INTERVAL_SEC
        lastBeatAt = now

        // 下面任何一步失败（建请求 / 网络 / 非 2xx / 解析）都退避 30 分钟 ✓
        //   别用 DEFAULT_INTERVAL_SEC ✗ 那是"正常但没拿到配置"的兜底 ✗
        //   服务器都不回话了，还按 30 秒冲就是雪上加霜 ✗
        val request = runCatching { buildRequest() }.getOrNull()
            ?: return FAIL_BACKOFF_SEC
        val body = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return FAIL_BACKOFF_SEC

        val interval = runCatching {
            JSONObject(body).optInt("interval", DEFAULT_INTERVAL_SEC)
        }.getOrDefault(DEFAULT_INTERVAL_SEC)

        // 服务器给的 interval 如果明显不合理（<=0 / 过大），也别当真的用 ✗ 走退避 ✓
        if (interval < MIN_INTERVAL_SEC || interval > MAX_INTERVAL_SEC) return FAIL_BACKOFF_SEC
        return interval
    }

    private fun buildRequest(): Request {
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(pkg, 0)
        val (installer, initiator) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val src = pm.getInstallSourceInfo(pkg)
            src.installingPackageName.orEmpty() to src.initiatingPackageName.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            val legacy = pm.getInstallerPackageName(pkg).orEmpty()
            legacy to ""
        }

        val b = Request.Builder()
            .url(URL)
            .header("X-App-userrandomid", PrefManager.userRandomId)
            .header("X-Client-SSaid", PrefManager.ssaid)
            .header("User-Agent", userAgent())
            .header("X-Client-Version", BuildConfig.VERSION_NAME)
            .header("X-Client-Code", BuildConfig.VERSION_CODE.toString())
            .header("X-Install-Time", info.firstInstallTime.toString())
            .header("X-Update-Time", info.lastUpdateTime.toString())

        if (installer.isNotEmpty()) b.header("X-Install-Source", installer)
        if (initiator.isNotEmpty()) b.header("X-Install-Origin", initiator)
        packageSignature(pkg)?.let { b.header("X-App-Signature", it) }

        return b.build()
    }

    /**
     * 和 `UpdateChecker.userAgent` **完全一致** ✓
     *
     * 服务端 `validate_request` 会按 UA 模板识别「是不是本应用」
     * （`Dalvik/` 开头 + 含 `(#Build; ` + 含 `okhttp/`）
     * ⇒ 这里必须算出同样的形状 ✓ 但**代码上不引用 UpdateChecker** ✓
     *   （各自调 `TokenDeviceUtils` ✓ 互不依赖 ✓）
     */
    private fun userAgent(): String = runCatching {
        val d = com.example.c001apk.util.TokenDeviceUtils.detectRealDevice()
        "Dalvik/2.1.0 (Linux; U; Android ${d.androidVersion}; ${d.model} ${d.buildNumber}) " +
            "(#Build; ${d.brand}; ${d.model}; ${d.buildNumber}; ${d.androidVersion}) okhttp/4.12.0"
    }.getOrDefault(
        "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE}; unknown) " +
            "(#Build; unknown; unknown; unknown; ${Build.VERSION.RELEASE}) okhttp/4.12.0"
    )

    /**
     * 本包签名证书 SHA-256（小写 hex）
     *
     * ⚠️ 第三次实现这份逻辑（[SignatureGuard] / [UpdateChecker] 各有一份 ✓）
     *   这不是偷懒 ✗ 是**故意的** ✓
     *   用户的要求就是「哪怕别人把升级那边换成自己的实现，统计也不能丢」✓
     *   ⇒ 三份互不引用 ✓ 删掉任何一份其它两份照常工作 ✓
     */
    private fun packageSignature(pkg: String): String? = runCatching {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val sigs = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES)
            .signatures ?: return null
        val first = sigs.firstOrNull() ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        md.update(first.toByteArray())
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
