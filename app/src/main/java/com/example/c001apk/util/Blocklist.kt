package com.example.c001apk.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 公共黑名单 —— 客户端缓存 + 批量查询。
 *
 * 用户 2026-10-31 需求：
 * >「接下来创建公共黑名单，为了保证覆盖了全面，直接读取卡片的调用，帖子详情页，
 * >  搜索页私信页等等凡是有用户和帖子的地方，只要那个开关开启，进去的时候渲染
 * >  卡片的**同时**后台请求一下服务器 ID 或者用户 ID，服务器返回结果后，
 * >  如果是被屏蔽状态 UI 更新为卡片带个模糊，上面显示一个该用户/话题已被多人举报」
 *
 * ## 设计要点
 *
 * ① **渲染不等网络** ✓
 *    卡片先正常渲染 ✓ 后台异步查 ✓ 命中后再更新 UI ✓（绝不阻塞列表滚动 ✗）
 *
 * ② **批量 + 去重 + 缓存** ✓
 *    - 一次最多 100 个 id（服务端上限 ✓）
 *    - 攒 250ms 再发（避免每个 item 一次请求 ✗）
 *    - 命中/未命中都进内存缓存 ✓ TTL 5 分钟 ✓
 *    - 已在请求中的 id 不重复请求 ✓
 *
 * ③ **受开关控制** ✓
 *    跟着「匿名举报联盟」的开关走 ✓（也就是设置 → 推荐流相关里那个 ✓）
 *    关闭时 [enabled] 为 false ⇒ 不发任何请求 ✓ 也不加模糊 ✓
 *
 * ④ **失败静默** ✓
 *    网络失败就当「未屏蔽」处理 ✗ 不打扰用户 ✓
 */
object Blocklist {

    private const val TAG = "Blocklist"
    private const val ENDPOINT = "https://service.houlangs.cn/c001apk/blocklist/check.php"
    private const val BATCH_MAX = 100
    private const val DEBOUNCE_MS = 250L
    private const val TTL_MS = 5 * 60 * 1000L

    /** 对象类型（与服务端 `feeds` / `users` / `topics` 对应） */
    enum class Kind(val key: String) {
        FEED("feeds"),
        USER("users"),
        TOPIC("topics"),
    }

    /** id -> 是否被屏蔽 */
    private val verdict = ConcurrentHashMap<String, Boolean>()

    /**
     * id -> **服务器给的横幅文案**（2026-10-31 用户要求）
     *
     * >「把全局红条的显示文本让服务器返回的 msg 决定」
     *
     * 服务器没给就回落到本地字符串 ✓
     */
    private val msgMap = ConcurrentHashMap<String, String>()

    /** id -> 过期时间 */
    private val expireAt = ConcurrentHashMap<String, Long>()

    /**
     * ★ 本会话内**已确认屏蔽**的 id。
     *
     * 为什么需要（2026-10-31 真机反馈：「被折叠之后滑下去再滑上来，它又展开了」）：
     *   RecyclerView 滑出屏幕后 ViewHolder 会复用/重建 ✓ bind 里先 `restore()` 展开 ✓
     *   然后查缓存 —— 如果之前那次请求失败过，我缓存了「未屏蔽 30 秒」✗
     *   => 于是显示成展开 ✗ 要等 30 秒后才重新查再折叠 ✓
     * ⇒ 现在：**一次判为屏蔽，本会话内一直屏蔽** ✓（失败不覆盖 ✓）
     */
    private val knownBlocked = ConcurrentHashMap.newKeySet<String>()

    /** 正在请求中的 id（防重复） */
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    /** 待发的批量（按 kind 分组） */
    private val pending = ConcurrentHashMap<Kind, MutableSet<String>>()

    private val flushScheduled = AtomicBoolean(false)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 黑名单接口要带齐身份头（跟举报一致 ✓ 服务端也校验）
            .addInterceptor(ServerCookieStore.interceptor())
            // ★ SSL 策略跟着「校验 SSL 证书」开关走 ✓（2026-10-31）
            .let { SslVerify.apply(it) }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** 开关（跟匿名举报联盟一致 ✓） */
    fun enabled(): Boolean = ReportAlliance.enabled

    /**
     * 同步查缓存：`true` 被屏蔽 / `false` 未屏蔽 / `null` 还不知道。
     *
     * binder 里直接读这个 ✓ 不阻塞 ✓
     */
    fun cached(kind: Kind, id: String?): Boolean? {
        if (id.isNullOrBlank()) return null
        val k = key(kind, id)
        // 已确认屏蔽的，直接回 true ✓ 不看 TTL ✗（避免"滑回来就展开" ✗）
        if (knownBlocked.contains(k)) return true
        val exp = expireAt[k] ?: return null
        if (System.currentTimeMillis() > exp) {
            verdict.remove(k)
            expireAt.remove(k)
            return null
        }
        return verdict[k]
    }

    /** 服务器给的横幅文案（没有返回 null ✓ 调用方回落到本地字符串 ✓） */
    fun cachedMsg(kind: Kind, id: String?): String? {
        if (id.isNullOrBlank()) return null
        return msgMap[key(kind, id)]
    }

    /** 便捷：是否被屏蔽（未知按 false ✓） */
    fun isBlocked(kind: Kind, id: String?): Boolean = cached(kind, id) == true

    /**
     * 后台预取一批 id（渲染卡片的同时调 ✓）。
     *
     * @param onUpdated 有新结果时回调（主线程 ✓ 调用方在里面 notifyDataSetChanged）
     */
    fun prefetch(kind: Kind, ids: Collection<String?>, onUpdated: (() -> Unit)? = null) {
        if (!enabled()) return
        val now = System.currentTimeMillis()
        var added = false
        val bucket = pending.getOrPut(kind) { ConcurrentHashMap.newKeySet() }
        for (raw in ids) {
            val id = raw?.trim().orEmpty()
            if (id.isEmpty()) continue
            val k = key(kind, id)
            if (inflight.contains(k)) continue
            val exp = expireAt[k]
            if (exp != null && now <= exp) continue        // 缓存还新鲜 ✓
            if (bucket.add(id)) added = true
            if (bucket.size >= BATCH_MAX) break
        }
        if (!added) {
            if (onUpdated != null) pendingCallbacks.add(onUpdated)
            return
        }
        if (onUpdated != null) pendingCallbacks.add(onUpdated)
        scheduleFlush()
    }

    /**
     * ★ 2026-11-01 日常黑名单运作：自动探测话题并上报
     *
     * 用户要求：「日常公共黑名单运作时，客户端自动查对象帖子是否携带话题，有就上报」
     *
     * ⚠️ 去重：同一条动态整个进程只探测一次 ✓（`autoReported` set ✓）
     * ⚠️ 后台线程 ✓ 不阻塞 UI ✓ 失败静默 ✓
     * ⚠️ 只在**判定为已屏蔽**时做 ✓（未屏蔽的正常内容不打扰 ✓）
     */
    private val autoReported = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun reportAllianceTopics(feedIds: Collection<String>) {
        if (!ReportAlliance.enabled) return
        val todo = feedIds.filter { it.isNotBlank() && autoReported.add(it) }
        if (todo.isEmpty()) return
        // 一次最多 3 条 ✓ 避免流量/功耗
        todo.take(3).forEach { id ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { ReportAlliance.autoReportWithTopics(id) }
            }
        }
    }

    /**
     * ★ 分页级批量查询（2026-10-31 用户要求）
     *
     * >「我只要首页，搜索页及其他的分页，拉到一个分页的时候，把分页的**所有 ID 一次问服务器**，
     * >  不用一个一个 ID 问效率很高」
     *
     * 用法：列表拿到一页数据后**调一次** ✓
     * ```kotlin
     * Blocklist.prefetchPage(
     *     feeds  = list.mapNotNull { it.id },
     *     users  = list.mapNotNull { it.userInfo?.uid },
     *     topics = list.mapNotNull { it.ttitle },
     * )
     * ```
     * 内部仍然走 [prefetch] 的攒批（250ms）✓ 三种类型会合并成**最多 3 个请求** ✓
     * （服务端每类上限 100 项 ✓ 超出的部分下一批自动补 ✓）
     */
    fun prefetchPage(
        feeds: Collection<String?>? = null,
        users: Collection<String?>? = null,
        topics: Collection<String?>? = null,
        onUpdated: (() -> Unit)? = null,
    ) {
        if (!enabled()) return
        // 每类最多塞 100 个（服务端上限 ✓）
        feeds?.takeIf { it.isNotEmpty() }?.let { prefetch(Kind.FEED, it, onUpdated) }
        users?.takeIf { it.isNotEmpty() }?.let { prefetch(Kind.USER, it, onUpdated) }
        topics?.takeIf { it.isNotEmpty() }?.let { prefetch(Kind.TOPIC, it, onUpdated) }
    }

    private val pendingCallbacks = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(DEBOUNCE_MS)
            flushScheduled.set(false)
            flush()
        }
    }

    private fun flush() {
        val snapshot = HashMap<Kind, List<String>>()
        for (kind in Kind.values()) {
            val b = pending[kind] ?: continue
            if (b.isEmpty()) continue
            val list = b.toList().take(BATCH_MAX)
            b.removeAll(list.toSet())
            if (list.isNotEmpty()) snapshot[kind] = list
        }
        if (snapshot.isEmpty()) return

        for ((kind, ids) in snapshot) {
            for (id in ids) inflight.add(key(kind, id))
        }

        val body = JSONObject().apply {
            for (kind in Kind.values()) {
                val ids = snapshot[kind] ?: continue
                put(kind.key, org.json.JSONArray().apply { ids.forEach { put(it) } })
            }
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

        val now = System.currentTimeMillis()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val j = runCatching { JSONObject(text) }.getOrNull()
                for (kind in Kind.values()) {
                    val ids = snapshot[kind] ?: continue
                    val hit = j?.optJSONObject(kind.key)
                    for (id in ids) {
                        val k = key(kind, id)
                        val blocked = hit?.has(id) == true
                        verdict[k] = blocked
                        expireAt[k] = now + TTL_MS
                        if (blocked) {
                            knownBlocked.add(k)
                            // ★ 2026-11-01 日常黑名单运作：这条被屏蔽 ⇒
                            //   后台探测它带的话题并一起上报 ✓
                            //   （无 Cookie 详情请求 ✓ 不污染「看过」记录 ✓）
                            if (kind == Kind.FEED) reportAllianceTopics(listOf(id))
                            // 服务器返回的文案（可空 ✓）
                            val m = hit?.optJSONObject(id)?.optString("msg", "")?.trim().orEmpty()
                            if (m.isNotEmpty()) msgMap[k] = m else msgMap.remove(k)
                        } else {
                            knownBlocked.remove(k)
                            msgMap.remove(k)
                        }
                    }
                }
                Log.i(TAG, "批量查询完成: " +
                    snapshot.entries.joinToString { "${it.key.key}=${it.value.size}" })
            }
        }.onFailure {
            // 失败静默 ✓ 当作「未屏蔽」✓ 但只缓存 30 秒（很快重试 ✓）
            Log.w(TAG, "查询失败: ${it.javaClass.simpleName}")
            for ((kind, ids) in snapshot) {
                for (id in ids) {
                    val k = key(kind, id)
                    // ⚠️ 已确认屏蔽的**不要**被失败覆盖 ✗（否则滑回来就展开 ✗）
                    if (knownBlocked.contains(k)) continue
                    verdict[k] = false
                    expireAt[k] = now + 30_000L
                }
            }
        }

        for ((kind, ids) in snapshot) {
            for (id in ids) inflight.remove(key(kind, id))
        }

        if (pendingCallbacks.isNotEmpty()) {
            val cbs = pendingCallbacks.toList()
            pendingCallbacks.clear()
            CoroutineScope(Dispatchers.Main).launch {
                for (cb in cbs) runCatching { cb() }
            }
        }
    }

    private fun key(kind: Kind, id: String) = "${kind.name}|$id"

    /** 清空缓存（调试用 / 切换账号时） */
    fun clear() {
        knownBlocked.clear()
        msgMap.clear()
        verdict.clear()
        expireAt.clear()
        inflight.clear()
        pending.clear()
    }
}
