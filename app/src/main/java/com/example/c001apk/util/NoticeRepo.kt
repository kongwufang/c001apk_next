package com.example.c001apk.util

import android.content.Context
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
     */
    suspend fun fetch(): List<Card> {
        val json = runCatching { UpdateChecker.fetchConfig(URL) }.getOrNull() ?: return emptyList()
        return parse(json)
    }

    fun parse(json: String): List<Card> = runCatching {
        val root = JSONObject(json)
        val arr = root.optJSONArray("cards") ?: return emptyList()
        val out = ArrayList<Card>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val card = parseOne(o) ?: continue
            if (!PrefManager.isNoticeDismissed(card.id)) out.add(card)
        }
        out.sortedByDescending { it.priority }.take(MAX_CARDS)
    }.getOrDefault(emptyList())

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
