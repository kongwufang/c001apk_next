package com.example.c001apk.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 数字联盟 ID（duid / szlmId）**主动过期监测** —— 客户端侧，**本地直连酷安判断**。
 *
 * ## 为什么要它
 * duid 失效时酷安**仍回 HTTP 200** ✗，错误藏在 JSON 的 `data` / `message` 里：
 * `评论接口异常` / `当前访问需要验证码` / `检测到当前版本存在异常…(-419)`
 * ⇒ 用户要等到点开某条动态才发现打不开，而且文案完全不提示「该去重新取号」✗
 *
 * ## 为什么不走自建服务器（2026-10-24 改）
 * 早先版本是「每 10 分钟把 duid 报给 `szlmid/check.php`，让服务器替客户端打一次」。
 * 实测放弃这条路 ✗：
 *  - 服务器出口 IP 打多了会被酷安**风控成 403** ✗（当天就遇到 ✓ 客户端就永远收不到「过期」✗）
 *  - 多一跳网络、多一个可能挂的环节 ✗
 *  - 其实**客户端自己就能判** ✓（判据稳定 ✓ 见下表）
 *
 * ## 判据（2026-10-18 / 10-24 两次实测）
 * 只打一个**固定接口**：`GET https://api.coolapk.com/v6/feed/detail?id=74345950`
 *
 * | 情况 | 响应 |
 * |---|---|
 * | duid 正常 | `data` 是**对象** ✓ |
 * | duid 过期（换号前的旧值）| `message` / `data` = `评论接口异常` 或 `…(-419)` ✗ |
 * | duid 是随机串 / 已失效 | `data` 是**字符串** `当前访问需要验证码` ✗ |
 * | 被风控 | HTTP **567** ✗ |
 * | 其它（动态被删、网络错、限流 **403**）| 判**未知** ✓ **不提示**（绝不误报 ✗）|
 *
 * ⚠️ 只认上面 4 条已知文案 ⇒ 别的错误一律当「未知」✓ 免得误弹 ✗
 *
 * ## 请求头
 * 直接复用 [AddCookiesInterceptor] ✓ —— 它会把 App 真实使用的
 * `X-App-Device` / `X-App-Token`(v3) / `X-App-Code` / `Cookie(uid/username/token)` 全拼好 ✓
 * ⇒ 这条探测请求与业务请求**完全同源** ✓ 服务端看到的身份一致 ✓
 *
 * ## 约定
 * - 启动后 60 秒第一次，之后每 10 分钟一次 ✓
 * - 后台线程发请求 ✓，判定过期 ⇒ [SzlmHint.notifyExpired] 弹窗 + 一键去取号 ✓
 * - 任何异常都**静默** ✓（探测失败绝不打扰用户 ✗）
 * - duid 为空 / 未登录 ⇒ 跳过 ✓
 */
object SzlmMonitor {

    private const val TAG = "SzlmMonitor"

    /** 固定探测接口（`feed/detail` 最敏感：duid 一坏就拒 ✗） */
    private const val PROBE_URL = "https://api.coolapk.com/v6/feed/detail?id=74345950"

    /** 已知的「duid 失效」文案（只认这几条 ✓ 别的当未知 ✗） */
    private val MARKERS = listOf(
        "评论接口异常",
        "当前访问需要验证码",
        "检测到当前版本存在异常",
        "(-419)",
    )

    /** HTTP 567 = 酷安的风控页（整页 HTML ✗） */
    private const val BLOCKED_STATUS = 567

    private const val INTERVAL_MS = 10 * 60 * 1000L
    private const val FIRST_DELAY_MS = 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())

    /**
     * ★ 复用 App 自己的头 ✓
     * [AddCookiesInterceptor] 会按业务请求的方式把 device / token / cookie 全拼上 ✓
     */
    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AddCookiesInterceptor)
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
        Log.i(TAG, "已启动：每 ${INTERVAL_MS / 60000} 分钟本地探测一次 duid（直连酷安）")
    }

    /** 手动触发一次（调试用 ✓） */
    fun checkNow() {
        handler.removeCallbacks(tick)
        checkOnce()
        if (started) handler.postDelayed(tick, INTERVAL_MS)
    }

    /** 判定结果 */
    private data class Verdict(val ok: Boolean, val expired: Boolean, val reason: String)

    private fun checkOnce() {
        if (PrefManager.SZLMID.isEmpty()) {
            Log.d(TAG, "跳过：duid 为空")
            return
        }
        if (!PrefManager.isLogin) {
            Log.d(TAG, "跳过：未登录（没凭据 ⇒ 探测一定被拒 ⇒ 没意义 ✗）")
            return
        }
        Thread {
            runCatching {
                val req = Request.Builder().url(PROBE_URL).get().build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val v = judge(resp.code, body)
                    Log.i(TAG, "探测 code=${resp.code} ok=${v.ok} expired=${v.expired} reason=${v.reason}")
                    if (v.expired) {
                        // ★ 复用已有的弹窗（60 秒节流 ✓ + [去重新取号] 按钮 ✓）
                        handler.post { SzlmHint.notifyExpired(AppContextHolder.context, v.reason) }
                    }
                }
            }.onFailure {
                // 探测失败绝不打扰用户 ✗（网络抖动很常见 ✓）
                Log.w(TAG, "探测异常：${it.javaClass.simpleName}: ${it.message}")
            }
        }.start()
    }

    /** 判据（见类注释的表 ✓） */
    private fun judge(code: Int, body: String): Verdict {
        if (code == BLOCKED_STATUS) {
            return Verdict(ok = false, expired = true, reason = "HTTP 567（风控页）")
        }
        if (code != 200) {
            // 403 限流 / 5xx / 代理错误页… 一律「未知」⇒ 不提示 ✓
            return Verdict(ok = false, expired = false, reason = "HTTP $code（未知，不提示）")
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return Verdict(false, false, "响应不是 JSON（未知，不提示）")
        val data = json.opt("data")
        val msg = json.optString("message", "")

        // data 是对象 ⇒ 正常 ✓
        if (data is JSONObject) {
            return Verdict(ok = true, expired = false, reason = "")
        }
        val hay = (if (data is String) data else "") + "\n" + msg
        for (m in MARKERS) {
            if (hay.contains(m)) {
                return Verdict(ok = false, expired = true, reason = hay.trim().take(120))
            }
        }
        return Verdict(
            ok = false, expired = false,
            reason = "未知响应（data=${data?.javaClass?.simpleName ?: "null"}）⇒ 不提示"
        )
    }
}
