package com.example.c001apk.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 数字联盟 ID（duid / szlmId）**主动过期监测** —— 客户端侧。
 *
 * ## 为什么要它
 * duid 失效时酷安**仍回 HTTP 200**，错误藏在 `data` / `message` 里（`评论接口异常` /
 * `当前访问需要验证码` / `…(-419)`）。用户要等到点开某条动态才发现打不开，
 * 而且文案完全不提示「该去重新取号」。
 *
 * ⇒ 每 10 分钟把 duid 报给自建端点，让它**替客户端**打一次内容接口：
 *   - `expired=true`  ⇒ 立刻弹「可能数字联盟 ID 已失效」+ 一键去取号（复用 [SzlmHint] ✓）
 *   - 网络失败 / 服务端异常 ⇒ 静默（不打扰用户 ✓，绝不能因为探测失败误报 ✗）
 *
 * ## 服务端
 * `POST https://service.houlangs.cn/c001apk/szlmid/check.php`
 * ```
 * duid=<SZLMID>&device=<X-App-Device>&code=<versionCode>
 * → {"code":0,"data":{"ok":bool,"expired":bool,"reason":"…","cached":bool}}
 * ```
 * 服务端有 10 分钟缓存 + 每 IP 每小时 40 次限流 ✓
 *
 * ## 约定
 * - **只在主线程之外**发请求 ✓（OkHttp 同步调用放在后台线程 ✓）
 * - 未登录 / 没有 duid / 没有设备串 ⇒ 直接跳过 ✓（不报错 ✗）
 * - 全程**不上传任何账号信息** ✓ 只有 duid + 设备串 + versionCode
 */
object SzlmMonitor {

    private const val TAG = "SzlmMonitor"
    private const val URL = "https://service.houlangs.cn/c001apk/szlmid/check.php"

    /** 10 分钟一轮 ✓（服务端缓存也是 10 分钟，正好错开 ✓） */
    private const val INTERVAL_MS = 10 * 60 * 1000L

    /** 启动后先延迟一会儿再打第一次 ✓（别和启动时的其它请求抢带宽）*/
    private const val FIRST_DELAY_MS = 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            checkOnce()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    /** 幂等 ✓ 重复调用不会叠加定时器 */
    fun start() {
        if (started) return
        started = true
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, FIRST_DELAY_MS)
        Log.i(TAG, "已启动：每 ${INTERVAL_MS / 60000} 分钟探测一次 duid")
    }

    /** 取当前安装包的 versionCode（不依赖 BuildConfig ✓） */
    private fun appVersionCode(ctx: Context?): Long {
        if (ctx == null) return 0L
        return runCatching {
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(ctx.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }

    /** 手动触发一次（调试 / 用户点「立即检查」时用 ✓） */
    fun checkNow() {
        handler.removeCallbacks(tick)
        checkOnce()
        if (started) handler.postDelayed(tick, INTERVAL_MS)
    }

    private fun checkOnce() {
        val duid = PrefManager.SZLMID
        val device = PrefManager.xAppDevice
        if (duid.isEmpty() || device.isEmpty()) {
            Log.d(TAG, "跳过：duid 或设备串为空")
            return
        }
        val code = appVersionCode(AppContextHolder.context)
        // 后台线程发请求 ✓ 主线程只负责弹窗
        Thread {
            runCatching {
                val body = FormBody.Builder()
                    .add("duid", duid)
                    .add("device", device)
                    .add("code", code.toString())
                    .build()
                val req = Request.Builder()
                    .url(URL)
                    .post(body)
                    .header("User-Agent", "c001apk-android/${appVersionCode(AppContextHolder.context)}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "探测失败 HTTP ${resp.code}")
                        return@use
                    }
                    val data = JSONObject(text).optJSONObject("data")
                    if (data == null) {
                        Log.w(TAG, "探测返回缺 data：${text.take(120)}")
                        return@use
                    }
                    val expired = data.optBoolean("expired", false)
                    val reason = data.optString("reason", "")
                    val cached = data.optBoolean("cached", false)
                    Log.i(TAG, "探测结果 expired=$expired cached=$cached reason=$reason")
                    if (expired) {
                        // ★ 复用已有的弹窗（含 60 秒节流 ✓ 和「去重新取号」按钮 ✓）
                        handler.post {
                            SzlmHint.notifyExpired(AppContextHolder.context, reason)
                        }
                    }
                }
            }.onFailure {
                // 探测失败绝不打扰用户 ✗（网络抖动很常见 ✓）
                Log.w(TAG, "探测异常：${it.javaClass.simpleName}: ${it.message}")
            }
        }.start()
    }
}
