package com.example.c001apk.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 侧栏「公告卡片」：**所有参数都由服务器决定** ✓（2026-11-01 用户要求）
 *
 * 接口：`https://service.houlangs.cn/c001apk/notice/`
 * 复用 [UpdateChecker.fetchConfig]（同一个 client / UA / 身份头 ✓ 不多开连接 ✓）
 *
 * ## 服务端 JSON 格式
 *
 * ```json
 * {
 *   "code": 0,
 *   "cards": [
 *     {
 *       "id": "20261101-a",          // ★ 必填，用来记「已读隐藏」✓ 改了 id 就等于新公告 ✓
 *       "title": "维护公告",          // 可空（空则不显示标题行 ✓ 只留叉）
 *       "body": "今晚 2 点重启\n大约 5 分钟",   // 支持 \n ✓
 *       "bgColor": "#FFF3E0",        // 可空 ⇒ 用主题色 ✓（深色模式也跟着走 ✓）
 *       "bgImage": "https://…",      // 可空 ⇒ 不加载背景图 ✓
 *       "accent": "#E65100",         // 可空 ⇒ 用主题色 ✓（标题 + 按钮文字）
 *       "dismissible": true,         // 默认 true ✓ false = 不给叉（强公告）
 *       "buttons": [                 // 可空 / 最多 3 个 ✓
 *         {"text": "查看详情", "url": "https://…"},
 *         {"text": "知道了"}         // url 空 = 点了只关掉这张卡 ✓
 *       ],
 *       "time": "2026-11-01",        // 可空 ⇒ 不显示 ✓
 *       "priority": 100,             // 大的排前面 ✓ 默认 0
 *       "minCode": 10008,            // 可空 ⇒ 低于这个 versionCode 不显示 ✓
 *       "maxCode": 0                 // 可空 / 0 ⇒ 不限制
 *     }
 *   ]
 * }
 * ```
 *
 * ## 客户端这边的规矩
 *   · 卡片**不缓存到磁盘** ✗（公告是时效性的 ✓ 每次进「我的」都重新拉 ✓ 服务端 60 秒缓存兜底 ✓）
 *   · 拉不到 ⇒ 什么都不显示 ✓ 绝不弹错误 ✗
 *   · 已读隐藏只记在本地 ✓（`PrefManager.isNoticeDismissed` ✓）
 *   · 最多同时显示 3 张 ✓（太多就把宫格挤没了 ✗）
 */
object NoticeRepo {

    const val URL = "https://service.houlangs.cn/c001apk/notice/"

    /** 最多同时显示几张 */
    private const val MAX_CARDS = 3

    data class Button(val text: String, val url: String)

    data class Card(
        val id: String,
        val title: String,
        val body: String,
        val bgColor: String,
        val bgImage: String,
        val accent: String,
        val dismissible: Boolean,
        val buttons: List<Button>,
        val time: String,
        val priority: Int,
    )

    /**
     * 拉公告。失败返回空表 ✓（不抛 ✗ 不弹提示 ✗）
     *
     * ⚠️ 2026-11-07：**拉不到也要显示心跳推送的卡** ✓
     *   （推送卡存在本机 ✓ 断网 / 接口挂了照样能看到 ✓）
     */
    suspend fun fetch(): List<Card> {
        val json = runCatching { UpdateChecker.fetchConfig(URL) }.getOrNull()
        if (json == null) {
            return pushedCards()
                .filter { !PrefManager.isNoticeDismissed(it.id) }
                .sortedByDescending { it.priority }
                .take(MAX_CARDS)
        }
        return parse(json)
    }

    fun parse(json: String): List<Card> = runCatching {
        val root = JSONObject(json)
        val arr = root.optJSONArray("cards") ?: JSONArray()
        val out = ArrayList<Card>(arr.length() + 3)
        // ★ 2026-11-07：先放「心跳推送的卡」✓ 再放服务器下发的 ✓
        //   两边都可能出现同一个 id ⇒ 用 LinkedHashMap 按 id 去重（推送优先 ✓）
        val seen = HashSet<String>()
        pushedCards().forEach { c ->
            if (seen.add(c.id) && !PrefManager.isNoticeDismissed(c.id)) out.add(c)
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val card = parseOne(o) ?: continue
            if (seen.add(card.id) && !PrefManager.isNoticeDismissed(card.id)) out.add(card)
        }
        out.sortedByDescending { it.priority }.take(MAX_CARDS)
    }.getOrDefault(emptyList())

    // ---------------- 心跳推送的公告卡（2026-11-07） ----------------

    /** 推送卡最多保留几张（防 prefs 无限涨 ✗） */
    private const val MAX_PUSHED = 5

    /**
     * 心跳 `notice` 事件把一张卡**直接塞进来** ✓（不需要请求 `/notice/` ✓）
     *
     * 存哪儿：`PrefManager.pushedNotices`（一个 JSON 数组字符串 ✓）
     * 什么时候消失：
     *   · 用户在侧栏点叉（已读）⇒ 按 id 隐藏 ✓
     *   · 卡里带 `until`（yyyy-MM-dd）且过期 ⇒ 自动丢弃 ✓
     *   · 超过 [MAX_PUSHED] 张 ⇒ 丢最旧的 ✓
     *
     * @return true = 收下了（心跳那边据此记为"已处理"✓）；false = 卡不合法 ✓
     */
    fun pushCard(o: JSONObject): Boolean {
        val card = parseOne(o) ?: return false
        val arr = runCatching { JSONArray(PrefManager.pushedNotices) }.getOrNull() ?: JSONArray()

        val kept = JSONArray()
        // 先按 id 覆盖旧的 ✓（同 id 再推一次 = 更新内容 ✓）
        for (i in 0 until arr.length()) {
            val old = arr.optJSONObject(i) ?: continue
            if (old.optString("id").trim() == card.id) continue
            if (isExpired(old)) continue
            kept.put(old)
        }
        kept.put(o)
        // 只留最近 MAX_PUSHED 张
        val start = if (kept.length() > MAX_PUSHED) kept.length() - MAX_PUSHED else 0
        val trimmed = JSONArray()
        for (i in start until kept.length()) trimmed.put(kept.opt(i))
        PrefManager.pushedNotices = trimmed.toString()
        return true
    }

    /** 读出推送卡（已过滤过期 / 已读 ✓） */
    private fun pushedCards(): List<Card> = runCatching {
        val arr = JSONArray(PrefManager.pushedNotices)
        val out = ArrayList<Card>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (isExpired(o)) continue
            parseOne(o)?.let { out.add(it) }
        }
        out
    }.getOrDefault(emptyList())

    /** 卡里的 until（yyyy-MM-dd）过期了吗 ✓ 没写 = 不过期 ✓ */
    private fun isExpired(o: JSONObject): Boolean = runCatching {
        val until = o.optString("until").trim()
        if (until.isEmpty()) return false
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val end = fmt.parse(until) ?: return false
        val today = fmt.parse(fmt.format(java.util.Date())) ?: return false
        today.after(end)
    }.getOrDefault(false)


    private fun parseOne(o: JSONObject): Card? {
        val id = o.optString("id").trim()
        if (id.isEmpty()) return null                       // 没 id 就没法记已读 ✗ 直接丢
        if (!o.optBoolean("enabled", true)) return null      // 服务端可以临时关掉 ✓
        val body = o.optString("body").trim()
        val title = o.optString("title").trim()
        if (title.isEmpty() && body.isEmpty()) return null    // 全空没意义 ✓

        // 版本门槛（老版本不该看到新公告 ✓）
        val cur = com.example.c001apk.BuildConfig.VERSION_CODE
        val minCode = o.optInt("minCode", 0)
        val maxCode = o.optInt("maxCode", 0)
        if (minCode > 0 && cur < minCode) return null
        if (maxCode > 0 && cur > maxCode) return null

        val buttons = ArrayList<Button>(3)
        o.optJSONArray("buttons")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (buttons.size >= 3) break
                val b = arr.optJSONObject(i) ?: continue
                val t = b.optString("text").trim()
                if (t.isEmpty()) continue
                buttons.add(Button(t, b.optString("url").trim()))
            }
        }

        return Card(
            id = id,
            title = title,
            body = body,
            bgColor = o.optString("bgColor").trim(),
            bgImage = o.optString("bgImage").trim(),
            accent = o.optString("accent").trim(),
            dismissible = o.optBoolean("dismissible", true),
            buttons = buttons,
            time = o.optString("time").trim(),
            priority = o.optInt("priority", 0),
        )
    }

    /** 解析 `#RRGGBB` / `#AARRGGBB`；解析不了返回 0（调用方用主题色 ✓） */
    fun parseColor(s: String): Int = runCatching {
        if (s.isEmpty()) return 0
        val hex = s.removePrefix("#")
        when (hex.length) {
            6 -> 0xFF000000.toInt() or hex.toLong(16).toInt()
            8 -> hex.toLong(16).toInt()
            else -> 0
        }
    }.getOrDefault(0)
}
