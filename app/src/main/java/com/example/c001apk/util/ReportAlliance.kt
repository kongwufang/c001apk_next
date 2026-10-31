package com.example.c001apk.util

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「匿名举报联盟」—— 不使用酷安官方举报，走自建后端。
 *
 * 用户 2026-10-31 需求：
 * > 「我打算做一个匿名举报功能，即不使用酷安的举报功能，在点赞、评论、收藏、转发那里
 * >   增加一个举报选项，首次点击会跳出弹窗『如果你认为官方举报是滚木，可以加入我们的
 * >   匿名举报联盟』用户同意后先保存他的偏好，并且在设置里增加一个开关 ... 可以随时控制」
 *
 * ## 三条底线
 *
 * ① **不告诉用户举报结果** ✗ —— 上传成功只提示「已提交」，不透露任何处置 ✓
 * ② **不与被举报内容发生任何交互** ✗ —— 完全不调酷安的任何接口 ✓
 *    （所以被举报人、酷安、以及任何人都不可能从官方渠道知道是谁举报的 ✓）
 * ③ **只上报最小必要信息** ✓ —— 帖子/话题 ID + 原因 + 设备标识（用于反刷）
 *
 * ## 上报内容
 *
 * ```
 * type      feed | topic
 * id        帖子 ID / 话题 ID
 * topic_id  所属话题（在话题页举报时带）
 * reasons   原因编码，逗号分隔
 * note      可选补充说明
 * ```
 *
 * 设备标识由请求头带（`X-Client-SSaid` / `X-App-userrandomid` / …）✓
 * cookie（`ck1`）由 [ServerCookieStore] 的拦截器自动附上 ✓
 * —— 这样服务端能把「举报行为」和「风控记录」关联起来 ✓
 */
object ReportAlliance {

    private const val TAG = "ReportAlliance"

    /** 上报地址（与统计、分享同一个域 ✓ cookie 拦截器只对自家域生效 ✓） */
    private const val ENDPOINT = "https://service.houlangs.cn/c001apk/report/create.php"

    /**
     * 举报原因（编码 => 文案）。与服务端 `$ALLOWED` 一一对应 ✓
     * 顺序即用户给定的顺序 ✓
     */
    val REASONS: List<Pair<String, String>> = listOf(
        "politics" to "涉及政治隐喻，传播领导人不实信息",
        "violence" to "暴力、血腥、政治等敏感内容",
        "privacy" to "泄露他人个人隐私",
        "porn" to "色情、低俗内容",
        "ad" to "小广告",
        "flame" to "引战，带节奏",
        "abuse" to "辱骂他人，恶意攻击",
        "fake" to "发表虚假信息，制造传播谣言",
        "vpn" to "违禁翻墙工具",
        "fraud" to "发布商品信息与实际不符或存在欺诈行为",
        "simcard" to "流量卡推广",
        "netdisk" to "网盘推广",
    )

    /**
     * 专属 OkHttp：只打自家域名 ✓ 挂 [ServerCookieStore]（要把 ck1 带回去 ✓）
     *
     * ⚠️ 2026-10-31 云控策略（用户原话）：
     *   「客户端要带所有请求头，我说的是如果用脚本拼请求头的时候，缺了请求头
     *     直接标记机器人」
     *   ⇒ 正常客户端**必须带齐**全部头 ✓
     *     服务端把「缺任意一个」当脚本特征 ✓ 标记机器人 + 连坐 IP 段 ✓
     *   ⇒ 所以这里一个都不能少：ssaid / uid / 版本 / code / 平台 / cookie ✓
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(ServerCookieStore.interceptor())
            // ★ SSL 策略跟着「校验 SSL 证书」开关走 ✓（2026-10-31）
            .let { SslVerify.apply(it) }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * ★ 2026-11-01 **无 Cookie** 客户端（探测话题专用）
     *
     * 用户要求：「用无 cookie 的情况请求详情页避免污染用户的历史记录数据」
     *
     * 和 [client] 的区别：
     *   · **不挂** `ServerCookieStore.interceptor()` ✗ ⇒ 不带 ck1 ✓
     *   · `CookieJar.NO_COOKIES` ⇒ 一个 Cookie 都不存不送 ✓
     *   ⇒ 酷安收到的是"匿名"请求 ✓ 不会把它记成「你看过这条」✓
     */
    private val anonClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .let { SslVerify.apply(it) }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** 是否已同意加入联盟（首次弹窗点「加入」后为 true） */
    var joined: Boolean
        get() = PrefManager.reportAllianceJoined
        set(value) {
            PrefManager.reportAllianceJoined = value
        }

    /**
     * 联盟开关（设置 → 推荐流相关 ✓）。
     *
     * 默认**关闭** ✗ —— 必须用户先同意加入 ✓ 再手动开启 ✓
     * 「同意后先保存他的偏好，并且在设置里增加一个开关」⇒ 加入时自动置为 true ✓
     */
    var enabled: Boolean
        get() = joined && PrefManager.reportAllianceEnabled
        set(value) {
            PrefManager.reportAllianceEnabled = value
        }

    /**
     * ★ 2026-11-01 探测这条动态携带的话题（**无 Cookie** ✓ 不污染浏览记录 ✓）
     *
     * @return 话题名列表（可能为空）；任何异常都返回空 ✓ 绝不抛
     */
    fun discoverTopics(feedId: String): List<String> = runCatching {
        if (feedId.isBlank()) return@runCatching emptyList<String>()
        val url = "https://api.coolapk.com/v6/feed/detail?id=" +
                java.net.URLEncoder.encode(feedId, "UTF-8")
        val req = Request.Builder()
            .url(url)
            .get()
            .header("X-Client-SSaid", PrefManager.ssaid)
            .header("X-App-userrandomid", PrefManager.userRandomId)
            .header("X-Client-Version", com.example.c001apk.BuildConfig.VERSION_NAME)
            .header("X-Client-Code", com.example.c001apk.BuildConfig.VERSION_CODE.toString())
            .header("X-Client-Platform", "android")
            .header("User-Agent", com.example.c001apk.constant.Constants.USER_AGENT)
            .build()

        anonClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || text.isEmpty()) {
                Log.d(TAG, "探测话题：HTTP ${resp.code}（忽略）")
                return@use emptyList<String>()
            }
            val j = runCatching { JSONObject(text) }.getOrNull()
                ?: return@use emptyList<String>()
            val data = j.optJSONObject("data") ?: return@use emptyList<String>()

            val out = LinkedHashSet<String>()
            data.optString("ttitle", "").trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
            for (key in listOf("message", "title", "intro")) {
                val body = data.optString(key, "")
                if (body.isBlank()) continue
                for (m in Regex("#([^#\n]{1,40})#").findAll(body)) {
                    val t = m.groupValues[1].trim()
                    if (t.isNotEmpty()) out.add(t)
                }
            }
            for (key in listOf("topics", "topicRows")) {
                val arr = data.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    o.optString("title", "").trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                    o.optString("ttitle", "").trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                }
            }
            out.take(10).toList()
        }
    }.onFailure {
        Log.d(TAG, "探测话题异常（忽略）：${it.javaClass.simpleName}")
    }.getOrDefault(emptyList())

    /**
     * ★ 2026-11-01 日常黑名单运作：自动探测话题并**一起上报**
     *
     * 调用方：[com.example.c001apk.util.Blocklist]（后台线程 + 每条只做一次）
     *
     * 上报：`type=feed` + `id` + `topics[]` + 固定 reason
     * ⚠️ 服务端只**记录** ✓ 仍然要 ≥3 个不同 IP 才进黑名单 ✓
     */
    fun autoReportWithTopics(feedId: String) {
        if (!enabled || feedId.isBlank()) return
        runCatching {
            val topics = discoverTopics(feedId)
            if (topics.isEmpty()) {
                Log.d(TAG, "自动上报：$feedId 没带话题，跳过")
                return@runCatching
            }
            val body = JSONObject().apply {
                put("type", "feed")
                put("id", feedId)
                put("topics", JSONArray(topics))
                put("reasons", "ad")
                put("note", "auto:topic-assoc")
            }.toString()
            val req = Request.Builder()
                .url(ENDPOINT)
                .post(body.toRequestBody(JSON_TYPE))
                .header("X-Client-SSaid", PrefManager.ssaid)
                .header("X-App-userrandomid", PrefManager.userRandomId)
                .header("X-Client-Version", com.example.c001apk.BuildConfig.VERSION_NAME)
                .header("X-Client-Code", com.example.c001apk.BuildConfig.VERSION_CODE.toString())
                .header("X-Client-Platform", "android")
                .header("User-Agent", com.example.c001apk.constant.Constants.USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                Log.d(TAG, "自动上报(含话题) $feedId topics=$topics => HTTP ${resp.code}")
            }
        }.onFailure {
            Log.d(TAG, "自动上报异常（忽略）：${it.javaClass.simpleName}")
        }
    }

    /**
     * 提交一条举报。
     *
     * @param type    `feed` / `topic`
     * @param id      帖子 ID 或话题 ID
     * @param topicId 所属话题 ID（话题页举报时带自己的 ID；帖子页可为空）
     * @param reasons 选中的原因编码
     * @param note    补充说明（可空）
     * @return 成功返回举报编号 `rid`；失败返回 null
     */
    fun submit(
        type: String,
        id: String,
        topicId: String?,
        reasons: List<String>,
        note: String?,
    ): String? {
        if (!enabled) {
            Log.w(TAG, "联盟开关未开启，忽略")
            return null
        }
        // 申诉（type=appeal）不需要原因 ✓ 举报才需要 ✓
        if (reasons.isEmpty() && type != "appeal") return null
        return runCatching {
            val body = JSONObject().apply {
                put("type", type)
                put("id", id)
                if (!topicId.isNullOrBlank()) put("topic_id", topicId)
                put("reasons", reasons.joinToString(","))
                if (!note.isNullOrBlank()) put("note", note.take(500))
            }.toString()

            // ★ 2026-10-31 云控策略：**必须带齐**全部头 ✓
            //   服务端把「缺任意一个」当脚本特征 ⇒ 标记机器人 + 连坐 IP 段 ✓
            //   （缺了也不报错 ✓ 只是内部记一笔 ✓ 见 report/create.php 的 REQUIRED_HEADERS）
            val req = Request.Builder()
                .url(ENDPOINT)
                .post(body.toRequestBody(JSON_TYPE))
                .header("X-Client-SSaid", PrefManager.ssaid)
                .header("X-App-userrandomid", PrefManager.userRandomId)
                .header("X-Client-Version", com.example.c001apk.BuildConfig.VERSION_NAME)
                .header("X-Client-Code", com.example.c001apk.BuildConfig.VERSION_CODE.toString())
                .header("X-Client-Platform", "android")
                .header("User-Agent", com.example.c001apk.constant.Constants.USER_AGENT)
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "上报失败 HTTP ${resp.code}: ${text.take(120)}")
                    return@use null
                }
                val j = runCatching { JSONObject(text) }.getOrNull()
                if (j?.optInt("status") == 1) {
                    val rid = j.optString("rid", "")
                    Log.i(TAG, "举报已提交 rid=$rid")
                    return@use rid
                }
                Log.w(TAG, "上报被拒: ${text.take(120)}")
                null
            }
        }.onFailure {
            // ⚠️ NetworkOnMainThreadException 的 message 是 null ✗ 必须打类名 ✓
            Log.w(TAG, "上报异常: ${it.javaClass.simpleName}: ${it.message}", it)
        }.getOrNull()
    }

    /** 调试用：把原因列表导出成 JSON 数组（客户端/服务端对齐检查） */
    fun reasonsJson(): String = JSONArray().apply {
        REASONS.forEach { (code, label) ->
            put(JSONObject().apply {
                put("code", code)
                put("label", label)
            })
        }
    }.toString()

    /** 首次点击时是否还要弹「加入联盟」的确认框 */
    fun needsJoinPrompt(): Boolean = !joined

    /** 用户点了「加入」 */
    fun join(ctx: Context) {
        joined = true
        enabled = true      // 加入即开启 ✓ 之后可在设置里随时关 ✓
        Log.i(TAG, "已加入匿名举报联盟（package=${ctx.packageName}）")
    }
}
