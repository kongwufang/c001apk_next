package com.example.c001apk.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
 * ## 探测目标：**自己主页的第一个帖子**（2026-10-24 改）
 * 早先用一个写死的 feed id ✗ —— 那条动态哪天被删就不好说了 ✗
 * 现在：
 * 1. `GET /v6/user/feedList?uid=<自己>` ⇒ 取第一条的 id ✓
 *    —— ⚠️ 这个接口**不受 duid 影响** ✓（30 接口扫描验证过 ✓）
 *    ⇒ **duid 坏了也照样能拿到 id** ✓ 这点很关键 ✓
 * 2. `GET /v6/feed/detail?id=<那个 id>` ⇒ 判定 ✓
 * 3. 兜底：拿不到 id（没发过帖 / 请求失败）⇒ 用固定 id [FALLBACK_ID] ✓
 * 4. 解析出的 id **缓存 1 小时** ✓（省一次请求）
 *
 * ## 判据（2026-10-18 / 10-24 两次实测 + 边界测试）
 *
 * | 响应 | 判定 |
 * |---|---|
 * | `data` 是**对象** | 正常 ✓ |
 * | `你查看的内容不存在或已被删除` | **正常** ✓（见下）|
 * | `data` 是字符串 / `message` 含 4 条已知文案 | **过期** ✗ |
 * | HTTP **567 连续两次** | 过期（疑似风控）✗ |
 * | 其它（403 限流 / 网络错 / 代理页 / 只出现一次的 567）| **未知 ⇒ 不提示** ✓ |
 *
 * ### 为什么「内容不存在」也算正常
 * ```
 * 有效 duid + 不存在的 id → 你查看的内容不存在或已被删除
 * 随机 duid + 不存在的 id → 当前访问需要验证码          ← ★ duid 校验在 id 校验**之前**
 * ```
 * ⇒ 看到「内容不存在」就说明 **duid 校验已通过** ✓
 * ⇒ 探测**不会因为目标帖子失效而静默失效** ✓
 *
 * ## 请求头
 * 直接复用 [AddCookiesInterceptor] ✓ —— 它会把 App 真实使用的
 * `X-App-Device` / `X-App-Token`(v3) / `X-App-Code` / `Cookie(uid/username/token)` 全拼好 ✓
 * ⇒ 探测请求与业务请求**完全同源** ✓
 *
 * ## 约定
 * - 启动后 60 秒第一次，之后每 10 分钟一次 ✓
 * - 后台线程发请求 ✓，判定过期 ⇒ [SzlmHint.notifyExpired] 弹窗 + 一键去取号 ✓
 * - 任何异常都**静默** ✓（探测失败绝不打扰用户 ✗）
 * - duid 为空 / 未登录 ⇒ 跳过 ✓
 * - **宁可漏报，不可误报** ✓（漏报晚点发现 ✓ 误报让用户白跑一趟取号 ✗）
 */
object SzlmMonitor {

    private const val TAG = "SzlmMonitor"

    /** 兜底探测目标（拿不到自己帖子时用 ✓ 这条就算被删也能判 ✓ 见上面「内容不存在」） */
    private const val FALLBACK_ID = "74345950"

    private const val API = "https://api.coolapk.com"

    /** 已知的「duid 失效」文案（只认这几条 ✓ 别的当未知 ✗） */
    private val MARKERS = listOf(
        "评论接口异常",
        "当前访问需要验证码",
        "检测到当前版本存在异常",
        "(-419)",
    )

    private const val BLOCKED_STATUS = 567
    private const val INTERVAL_MS = 10 * 60 * 1000L
    private const val FIRST_DELAY_MS = 60 * 1000L
    private const val ID_CACHE_MS = 60 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())

    /** ★ 复用 App 自己的头 ✓ */
    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AddCookiesInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 解析出来的探测目标 id（缓存 1 小时 ✓） */
    @Volatile
    private var cachedId = ""
    @Volatile
    private var cachedAt = 0L
    @Volatile
    private var cachedForUid = ""

    @Volatile
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            checkOnce()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, FIRST_DELAY_MS)
        Log.i(TAG, "已启动：每 ${INTERVAL_MS / 60000} 分钟本地探测一次 duid（直连酷安）")
    }

    fun checkNow() {
        handler.removeCallbacks(tick)
        checkOnce()
        if (started) handler.postDelayed(tick, INTERVAL_MS)
    }

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
                val feedId = resolveFeedId()
                val (code, body) = get("/v6/feed/detail?id=" + feedId)
                val v = judge(code, body)
                Log.i(TAG, "探测 id=$feedId code=$code ok=${v.ok} expired=${v.expired} reason=${v.reason}")
                if (v.expired) {
                    handler.post { SzlmHint.notifyExpired(AppContextHolder.context, v.reason) }
                }
            }.onFailure {
                Log.w(TAG, "探测异常：${it.javaClass.simpleName}: ${it.message}")
            }
        }.start()
    }

    /** 打一次 GET，返回 (状态码, body) */
    private fun get(pathAndQuery: String): Pair<Int, String> {
        val req = Request.Builder().url(API + pathAndQuery).get().build()
        return client.newCall(req).execute().use { resp ->
            Pair(resp.code, resp.body?.string().orEmpty())
        }
    }

    /**
     * 解析探测目标：**自己主页的第一个帖子** ✓
     * 拿不到就退到 [FALLBACK_ID] ✓
     *
     * ⚠️ `/v6/user/feedList` 不受 duid 影响 ✓ ⇒ duid 坏了也能拿到 id ✓
     */
    private fun resolveFeedId(): String {
        val uid = PrefManager.uid
        if (uid.isEmpty()) return FALLBACK_ID
        // 缓存命中（同 uid + 未过期）✓
        if (cachedId.isNotEmpty() && cachedForUid == uid &&
            System.currentTimeMillis() - cachedAt < ID_CACHE_MS
        ) {
            return cachedId
        }
        val path = "/v6/user/feedList?uid=$uid&page=1&showAnonymous=0&isIncludeTop=1"
        val (code, body) = runCatching { get(path) }.getOrNull() ?: return FALLBACK_ID
        if (code != 200) {
            Log.d(TAG, "取自己帖子列表失败 code=$code，用兜底 id")
            return if (cachedId.isNotEmpty()) cachedId else FALLBACK_ID
        }
        val id = runCatching {
            val arr: JSONArray = JSONObject(body).optJSONArray("data") ?: return@runCatching ""
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val fid = o.optString("id", "")
                if (fid.isNotEmpty()) return@runCatching fid
            }
            ""
        }.getOrDefault("")
        if (id.isEmpty()) {
            Log.d(TAG, "自己没发过帖子 / 解析不到 id，用兜底 id")
            return FALLBACK_ID
        }
        cachedId = id
        cachedAt = System.currentTimeMillis()
        cachedForUid = uid
        Log.i(TAG, "探测目标 = 自己主页第一条动态 id=$id")
        return id
    }

    /** 判据（见类注释的表 ✓） */
    private fun judge(code: Int, body: String): Verdict {
        if (code == BLOCKED_STATUS) {
            // 567 也可能是「IP 被风控」而不是 duid ✗ ⇒ 再打一次，两次都是 567 才认 ✗
            val again = runCatching { get("/v6/feed/detail?id=" + (cachedId.ifEmpty { FALLBACK_ID })) }
                .getOrNull()
            if (again != null && again.first == BLOCKED_STATUS) {
                return Verdict(ok = false, expired = true, reason = "HTTP 567（连续两次风控页）")
            }
            return Verdict(ok = false, expired = false, reason = "HTTP 567（只出现一次，判未知）")
        }
        if (code != 200) {
            return Verdict(ok = false, expired = false, reason = "HTTP $code（未知，不提示）")
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return Verdict(false, false, "响应不是 JSON（未知，不提示）")
        val data = json.opt("data")
        val msg = json.optString("message", "")

        if (data is JSONObject) {
            return Verdict(ok = true, expired = false, reason = "")
        }
        val hay = (if (data is String) data else "") + "\n" + msg
        for (m in MARKERS) {
            if (hay.contains(m)) {
                return Verdict(ok = false, expired = true, reason = hay.trim().take(120))
            }
        }
        // ★ 目标帖子被删 ⇒ duid 校验**已通过**（实测：duid 坏了会先报错 ✗ 轮不到 id 校验 ✓）
        if (msg.contains("内容不存在") || msg.contains("已被删除")) {
            return Verdict(ok = true, expired = false, reason = "目标动态不在了，但 duid 正常")
        }
        return Verdict(
            ok = false, expired = false,
            reason = "未知响应（data=${data?.javaClass?.simpleName ?: "null"}）⇒ 不提示"
        )
    }
}
