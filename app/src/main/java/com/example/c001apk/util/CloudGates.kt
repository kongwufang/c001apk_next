package com.example.c001apk.util

import android.util.Log
import com.example.c001apk.constant.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云控开关（2026-11-01）
 *
 * 用户需求：
 *   「现在做云控开关机制，黑名单联盟云控（app上面的开关被用户打开后生效）总开关，
 *     其他卡片黑名单上报总开关，卡片关联开关（包括帖子关联话题这种），
 *     开关如果被云控关闭就不执行对应功能」
 *
 * ## 三级门闸
 *
 * ```
 * alliance      联盟总开关   —— 「用户打开」AND「云控打开」= 生效 ✓
 *                              ⚠️ 两者**独立** ✓ 云控可以随时开/关 ✓
 *                              （云控设 1 但用户没开 ⇒ 不生效 ✓
 *                                用户开了但云控设 0 ⇒ 也不生效 ✓）
 * reportOther   其他卡片上报 —— 话题 / 用户 / 优质图文 等非动态卡
 * cardAssoc     卡片关联     —— 帖子关联话题这种自动关联上报
 * ```
 *
 * ## 参数来源
 *
 * 用户要求：「开关状态可以从任意一个接口返回参数控制」
 * ⇒ [applyFrom] 可以从**任何**后台响应里读这三个字段 ✓
 *    · `blocklist/check.php`  ✓
 *    · `report/create.php`    ✓
 *    · `config/gates.php`     ✓（专用接口 ✓ 兜底）
 *   ⇒ 客户端日常已经在打这些接口 ⇒ 顺手就把开关带回来 ✓ 不用额外请求 ✓
 *
 * ## 失败策略
 *
 * 拉不到 ⇒ 沿用上次（[PrefManager] 里缓存 ✓）；从没拉到过 ⇒ 默认全开 ✓
 * ⇒ 网络抖动不会误关 ✗ 也不会因为拉不到就乱开 ✗
 */
object CloudGates {

    private const val TAG = "CloudGates"

    /** 多久拉一次（6 小时 ✓ 别太频繁）*/
    private const val TTL_MS = 6 * 60 * 60 * 1000L

    /** hash 变了就立刻重拉（运维改开关后能快速生效 ✓）*/
    private const val ENDPOINT = "https://service.houlangs.cn/c001apk/config/gates.php"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(ServerCookieStore.interceptor())
            .let { SslVerify.apply(it) }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // ---------------------------------------------------------------- 三个开关

    /**
     * 联盟总开关（云控侧）。
     *
     * 生效 = `joined && PrefManager.reportAllianceEnabled && alliance` ✓
     * ⇒ 云控侧和用户侧**独立** ✓ 但必须**同时为 true** 才生效 ✓
     */
    val alliance: Boolean
        get() = PrefManager.cloudAlliance

    /** 其他卡片（话题 / 用户 / 优质图文…）的上报总开关 */
    val reportOther: Boolean
        get() = PrefManager.cloudReportOther

    /** 卡片关联（帖子 ↔ 话题）自动上报开关 */
    val cardAssoc: Boolean
        get() = PrefManager.cloudCardAssoc

    // ---------------------------------------------------------------- 任意接口带回

    /**
     * ★ 2026-11-01 从**任意**后台响应里读云控开关
     *
     * 用户要求：「开关状态可以从任意一个接口返回参数控制」
     *
     * 用法：任何拿到后台 JSON 的地方都调一下 ✓ 几乎零成本 ✓
     * ```kotlin
     * CloudGates.applyFrom(json)
     * ```
     *
     * ⚠️ 只认**存在**的字段 ✓ 没带的字段保持原值 ✓
     *    （不是所有接口都会返回三个开关 ✓ 别把它们清零 ✗）
     */
    fun applyFrom(json: JSONObject?) {
        if (json == null) return
        var changed = false
        if (json.has("alliance")) {
            val v = json.optInt("alliance", 1) == 1
            if (PrefManager.cloudAlliance != v) {
                PrefManager.cloudAlliance = v
                changed = true
            }
        }
        if (json.has("report_other")) {
            val v = json.optInt("report_other", 1) == 1
            if (PrefManager.cloudReportOther != v) {
                PrefManager.cloudReportOther = v
                changed = true
            }
        }
        if (json.has("card_assoc")) {
            val v = json.optInt("card_assoc", 1) == 1
            if (PrefManager.cloudCardAssoc != v) {
                PrefManager.cloudCardAssoc = v
                changed = true
            }
        }
        if (json.has("alliance") || json.has("report_other") || json.has("card_assoc")) {
            PrefManager.cloudGatesTs = System.currentTimeMillis()
        }
        if (changed) {
            Log.i(
                TAG,
                "云控开关已更新 alliance=$alliance reportOther=$reportOther cardAssoc=$cardAssoc"
            )
        }
    }

    // ---------------------------------------------------------------- 拉取

    private val fetching = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 让下次读开关时**立刻**重新拉一次（心跳下发 `gates` 事件时用 ✓）
     *
     * 实现：把 CloudGates 的「上次拉取时间」清零 ⇒ [needFetch] 立刻 true ✓
     * 不动当前值 ✗（拉失败时还能退回旧值 ✓ 不会因为一次网络抖动把功能关掉 ✗）
     */
    fun invalidate() {
        runCatching { PrefManager.cloudGatesTs = 0L }
    }

    /** 该拉了吗（超过 TTL ✓）*/
    private fun needFetch(): Boolean {
        val last = PrefManager.cloudGatesTs
        return last <= 0L || System.currentTimeMillis() - last > TTL_MS
    }

    /**
     * 启动时 / 打开联盟开关后调一次 ✓ 后台线程 ✓ 失败静默 ✓
     *
     * @param force true = 无视 TTL 立刻拉（比如用户在设置里刚打开开关 ✓）
     */
    fun refresh(force: Boolean = false) {
        if (!force && !needFetch()) return
        if (!fetching.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val req = Request.Builder()
                    .url(ENDPOINT)
                    .get()
                    .header("X-Client-SSaid", PrefManager.ssaid)
                    .header("X-App-userrandomid", PrefManager.userRandomId)
                    .header("X-Client-Version", com.example.c001apk.BuildConfig.VERSION_NAME)
                .header(ServerIdentity.HEADER_API, ServerIdentity.API_VERSION.toString())
                    .header("X-Client-Code", com.example.c001apk.BuildConfig.VERSION_CODE.toString())
                    .header("X-Client-Platform", "android")
                    .header("User-Agent", Constants.USER_AGENT)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || text.isEmpty()) {
                        Log.d(TAG, "云控拉取失败 HTTP ${resp.code}，沿用上次")
                        return@use
                    }
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    if (j?.optInt("status") != 1) {
                        Log.d(TAG, "云控返回异常，沿用上次: ${text.take(80)}")
                        return@use
                    }
                    applyFrom(j)
                    PrefManager.cloudGatesTs = System.currentTimeMillis()
                    // ★ 2026-11-01 加一条「拉到了」的日志 ✓
                    //   原来只在「变化 / 失败」时打 ✗ ⇒ logcat 干净得像没跑 ✓（踩过）
                    Log.i(
                        TAG,
                        "云控已拉取 alliance=$alliance reportOther=$reportOther " +
                            "cardAssoc=$cardAssoc（下次 ${TTL_MS / 3600000}h 后）"
                    )
                }
            }.onFailure {
                Log.d(TAG, "云控拉取异常（沿用上次）：${it.javaClass.simpleName}")
            }
            fetching.set(false)
        }
    }
}
