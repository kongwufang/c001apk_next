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

    /**
     * 收到一条心跳推送卡时打个信号 ✓（2026-11-07）
     *
     * 谁在听：[com.example.c001apk.ui.message.MineFragment] —— 侧栏活着就立刻重拉一次 ✓
     * 没人听：卡已经存本机 ✓ 下次打开侧栏照样能看到 ✓（信号不丢 ✓）
     */
    val pushedSignal = androidx.lifecycle.MutableLiveData<Long>()

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
        /**
         * 要不要显示底部那行时间（默认 **false** ✓ 2026-11-07 用户要求）
         *
         * 理由：卡片空间宝贵，「2026-11-07 17:58」这种信息对用户没有价值 ✗
         * 服务器想让某张卡显示 ⇒ 显式写 `"showTime": true` ✓
         */
        val showTime: Boolean,
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
        val arr = root.optJSONArray("cards")
        // ★ 2026-11-13 用户要求：服务端撤下的卡片要**立刻**从本机消失
        //   以前：推送卡存在 prefs 里，服务端删了它还在（要等到期或用户点叉）✗
        //   现在：拿到公告池后按 id 对齐，本地有、池里没有的 ⇒ 删掉 ✓
        //   ⚠️ 只有响应里**真的带了 cards 字段**才对齐，网络异常/旧服务端不乱删 ✗
        if (arr != null) {
            val serverIds = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("id")?.trim()?.let {
                    if (it.isNotEmpty()) serverIds.add(it)
                }
            }
            pruneWithdrawn(serverIds)
        }
        val arr2 = arr ?: JSONArray()
        val out = ArrayList<Card>(arr2.length() + 3)
        // ★ 2026-11-07：先放「心跳推送的卡」✓ 再放服务器下发的 ✓
        //   两边都可能出现同一个 id ⇒ 用 LinkedHashMap 按 id 去重（推送优先 ✓）
        val seen = HashSet<String>()
        pushedCards().forEach { c ->
            if (seen.add(c.id) && !PrefManager.isNoticeDismissed(c.id)) out.add(c)
        }
        for (i in 0 until arr2.length()) {
            val o = arr2.optJSONObject(i) ?: continue
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
        // ★ 通知侧栏「有新卡了」✓ 侧栏活着就立刻刷新（不用等用户退出再进）
        pushedSignal.postValue(System.currentTimeMillis())
        return true
    }

    /**
     * 把「服务端已经撤回」的推送卡从本机删掉（2026-11-13）
     *
     * 判断依据：`pushedNotices` 里的卡，如果 id 不在本次公告池里 ⇒ 删除
     * 例外：没有 id 的历史脏数据也一并清掉（反正没法展示/去重）
     */
    private fun pruneWithdrawn(serverIds: Set<String>) {
        runCatching {
            val arr = JSONArray(PrefManager.pushedNotices)
            if (arr.length() == 0) return
            val kept = JSONArray()
            var removed = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isEmpty() || !serverIds.contains(id)) {
                    removed++
                    continue
                }
                kept.put(o)
            }
            if (removed > 0) {
                PrefManager.pushedNotices = kept.toString()
                AppLog.i(LogModule.NOTICE, "NoticeRepo") {
                    "PRUNE 服务端已撤回的卡片 " + removed + " 张（剩余 " + kept.length() + "）"
                }
            }
        }
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
                // ★ 2026-11-13 修：服务端历史上用 `name`，客户端只认 `text`
                //   ⇒ 卡片能显示但按钮丢失 ✗ 这里做双向兼容（text 优先，回退 name）✓
                val label = b.optString("text").trim()
                    .ifEmpty { b.optString("name").trim() }
                if (label.isEmpty()) continue
                val url = b.optString("url").trim()
                if (!url.startsWith("https://") && !url.startsWith("http://")) continue
                buttons.add(Button(label, url))
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
            // ★ 2026-11-07 用户要求：时间那行默认**不显示** ✗
            //   卡片空间很宝贵，"2026-11-07 17:58" 对用户没意义 ✓
            //   服务器想让某张卡显示时间 ⇒ 显式写 "showTime": true ✓
            showTime = o.optBoolean("showTime", false),
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
