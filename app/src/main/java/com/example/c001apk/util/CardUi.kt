package com.example.c001apk.util

import org.json.JSONObject

/**
 * 卡片渲染规则表：把「哪个模板怎么画」从代码里搬进可热更新的配置。
 *
 * 服务端下发的顶层卡片模板实测已有 40 多种且随时会冒新的，给每个模板写死 ViewHolder 追不上。
 * 这里改成「App 提供有限骨架，规则表说明每个模板用哪个骨架、取哪些字段」，规则表由
 * [CardUiRemote] 从自建服务拉下来缓存，**新卡片只用已有骨架就不用更新 App**。
 *
 * 远端 JSON 是**增量覆盖**：只写要改/要加的模板，其余继续用 [BUILTIN] 内置默认表。
 * ```json
 * {"version":3,"cards":{"模板名":{
 *   "layout":"auto|list|hscroll|grid|stats|text|hidden",
 *   "span":4,
 *   "titleField":"title",
 *   "summaryField":"description|subTitle|message",
 *   "heroField":"pic",
 *   "item":{"shape":"auto|row|tile|cover|text","icon":"logo|pic","title":"title",
 *           "subtitle":"entityTypeName|description|message","action":"url|apk|none"},
 *   "stats":{"names":{"avgData":"平均分"},"avgSuffix":"_avg","countSuffix":"_count"}}}}
 * ```
 * 字段里带 `|` 是回退链：取第一个非空值（同一位置在不同模板下换字段名是常态）。
 * 拉不到 / 解析失败一律静默退回内置表，绝不让配置问题把列表搞空白。
 */
object CardUi {

    // ---- 骨架（App 内置能力，规则表只能挑不能造） ----
    const val AUTO = "auto"
    const val LIST = "list"
    const val HSCROLL = "hscroll"
    const val GRID = "grid"

    /** 把 extraData 摊成若干行「名称 值」（子版块统计那类） */
    const val STATS = "stats"

    /** 只有标题/正文的纯文本卡 */
    const val TEXT = "text"

    /** 不渲染（占 0 高度） */
    const val HIDDEN = "hidden"

    // ---- 实体形状 ----
    const val SHAPE_AUTO = "auto"

    /** 图标 + 标题 + 副标题横排（置顶引导、应用列表那类） */
    const val SHAPE_ROW = "row"

    /** 图标 + 标题居中竖排（宫格链接那类，现在的默认样式） */
    const val SHAPE_TILE = "tile"

    /** 大图 + 标题（酷图、动态封面那类） */
    const val SHAPE_COVER = "cover"

    /** 只有文字 */
    const val SHAPE_TEXT = "text"

    /** 跑分项前缀 → 中文名（真源是产品页 tab 的 rule.name） */
    private val SCORE_NAMES = mapOf(
        "aututu" to "安兔兔跑分",
        "geek_bench_single" to "GeekBench 单核",
        "geek_bench_multi" to "GeekBench 多核",
        "3d_mark" to "WildLife Extreme"
    )

    data class ItemRule(
        val shape: String = SHAPE_AUTO,
        val icon: String = "logo|pic",
        val title: String = "title",
        val subtitle: String = "entityTypeName|description|message",
        val action: String = "url"
    )

    data class StatsRule(
        val names: Map<String, String> = emptyMap(),
        val avgSuffix: String = "_avg",
        val countSuffix: String = "_count"
    )

    data class Rule(
        val layout: String = AUTO,
        val span: Int = 0,
        val titleField: String = "title",
        val summaryField: String = "description|subTitle|message",
        val heroField: String = "pic",
        val item: ItemRule? = null,
        val stats: StatsRule? = null
    )

    data class CardLayout(
        val horizontal: Boolean = false,
        val span: Int = 1,
        val stats: Boolean = false,
        val hidden: Boolean = false
    )

    private val HSCROLL_TEMPLATES = listOf(
        "apkScrollCard", "apkScrollCardWithBackground", "apkImageScrollCard", "apkImageCard",
        "colorfulScrollCard", "iconLargeScrollCard", "feedScrollCard", "imageScaleCard",
        "iconScrollCard", "imageScrollCard", "imageCarouselCard", "iconMiniScrollCard"
    )

    private val LIST_TEMPLATES = listOf(
        "iconListCard", "apkListCard", "listCard", "feedListCard", "productTimelineListCard"
    )

    /** 宫格模板 → 每行几个（真源：_rev/card_tpl_shape.py 实测的实体形状） */
    private val GRID_TEMPLATES = mapOf(
        "iconButtonGridCard" to 2,
        "subTabLinkCard" to 2,
        "verticalColumnsFullPageCard" to 2,
        "feedCoolPictureGridCard" to 2,
        "rankAwardCard" to 3,
        "iconMiniLinkGridCard" to 3,
        "capsuleListCard" to 3,
        "iconTabLinkGridCard" to 4,
        "linkCard" to 4,
        "selectorLinkCard" to 4,
        "titleCard" to 5,
        "iconLinkGridCard" to 5
    )

    private val BUILTIN: Map<String, Rule> = run {
        val m = mutableMapOf<String, Rule>()
        // 子版块统计：一个版块一个平均分
        m["subTabFeedCard1"] = Rule(
            layout = STATS,
            stats = StatsRule(names = mapOf("avgData" to "平均分", "countData" to "参与人数"))
        )
        // 跑分统计：extraData 是成对的 *_avg / *_count
        m["subTabFeedCard2"] = Rule(layout = STATS, stats = StatsRule(names = SCORE_NAMES))
        m["subTabFeedCard3"] = Rule(layout = STATS, stats = StatsRule(names = SCORE_NAMES))
        // 置顶引导（headline 实体：「来点评」+ 话题标题 + 图标，点了进话题）
        m["topContent"] = Rule(
            layout = LIST,
            item = ItemRule(
                shape = SHAPE_ROW,
                icon = "logo",
                title = "title",
                subtitle = "entityTypeName|description",
                action = "url"
            )
        )
        // 纯文本卡
        m["messageCard"] = Rule(layout = TEXT)
        m["textCard"] = Rule(layout = TEXT)
        m["sponsorArticleNews"] = Rule(layout = TEXT)

        HSCROLL_TEMPLATES.forEach { m[it] = Rule(layout = HSCROLL) }
        LIST_TEMPLATES.forEach { m[it] = Rule(layout = LIST) }
        GRID_TEMPLATES.forEach { (t, span) -> m[t] = Rule(layout = GRID, span = span) }
        m
    }

    @Volatile
    private var remote: Map<String, Rule> = emptyMap()

    /**
     * 装载远端规则表。空串 / 解析不出来时什么都不做（继续用上次的远端表或内置表），
     * 这样配置写坏了最多是「这次不生效」，不会把卡片变空白。
     */
    fun load(json: String?) {
        val text = json?.takeIf { it.isNotBlank() } ?: return
        val parsed = runCatching { parse(text) }.getOrNull() ?: return
        if (parsed.isNotEmpty()) remote = parsed
    }

    fun reset() {
        remote = emptyMap()
    }

    fun remoteSize(): Int = remote.size

    fun rule(template: String?): Rule? = template?.let { remote[it] ?: BUILTIN[it] }

    /**
     * 实体的排列方式。规则表配了就用配的；没配过的模板按实体数量推断
     * （一两条竖排、四五条宫格、再多横滚），所以没见过的新模板也能排得像样。
     */
    fun layout(template: String?, entityCount: Int): CardLayout {
        val r = rule(template)
        when (r?.layout) {
            HIDDEN -> return CardLayout(hidden = true)
            STATS -> return CardLayout(stats = true)
            TEXT -> return CardLayout()
            HSCROLL -> return CardLayout(horizontal = true)
            LIST -> return CardLayout()
            GRID -> return CardLayout(span = r.span.coerceAtLeast(1))
        }
        return when {
            entityCount <= 0 -> CardLayout()
            entityCount == 1 -> CardLayout()
            entityCount <= 4 -> CardLayout(span = entityCount)
            else -> CardLayout(horizontal = true)
        }
    }

    fun titleField(template: String?): List<String> = chain(rule(template)?.titleField, "title")

    fun summaryField(template: String?): List<String> =
        chain(rule(template)?.summaryField, "description|subTitle|message")

    fun heroField(template: String?): List<String> = chain(rule(template)?.heroField, "pic")

    fun itemRule(template: String?): ItemRule = rule(template)?.item ?: ItemRule()

    /** 把 `"a|b"` 拆成回退链；空串时用默认链 */
    fun chain(raw: String?, fallback: String): List<String> =
        (raw?.takeIf { it.isNotBlank() } ?: fallback).split("|").map { it.trim() }.filter { it.isNotEmpty() }

    // ---------------------------------------------------------------- 远端 JSON

    private fun parse(json: String): Map<String, Rule> {
        val cards = JSONObject(json).optJSONObject("cards") ?: return emptyMap()
        val out = mutableMapOf<String, Rule>()
        cards.keys().forEach { name ->
            cards.optJSONObject(name)?.let { out[name] = it.toRule() }
        }
        return out
    }

    private fun JSONObject.toRule(): Rule {
        val itemObj = optJSONObject("item")
        val statsObj = optJSONObject("stats")
        return Rule(
            layout = optString("layout", AUTO).lowercase().ifBlank { AUTO },
            span = optInt("span", 0),
            titleField = optString("titleField", "title").ifBlank { "title" },
            summaryField = optString("summaryField", "description|subTitle|message")
                .ifBlank { "description|subTitle|message" },
            heroField = optString("heroField", "pic").ifBlank { "pic" },
            item = itemObj?.let { o ->
                ItemRule(
                    shape = o.optString("shape", SHAPE_AUTO).lowercase().ifBlank { SHAPE_AUTO },
                    icon = o.optString("icon", "logo|pic").ifBlank { "logo|pic" },
                    title = o.optString("title", "title").ifBlank { "title" },
                    subtitle = o.optString("subtitle", "entityTypeName|description|message")
                        .ifBlank { "entityTypeName|description|message" },
                    action = o.optString("action", "url").ifBlank { "url" }
                )
            },
            stats = statsObj?.let { o ->
                val namesObj = o.optJSONObject("names")
                val names = mutableMapOf<String, String>()
                namesObj?.keys()?.forEach { k -> names[k] = namesObj.optString(k) }
                StatsRule(
                    names = names,
                    avgSuffix = o.optString("avgSuffix", "_avg").ifBlank { "_avg" },
                    countSuffix = o.optString("countSuffix", "_count").ifBlank { "_count" }
                )
            }
        )
    }

    // ---------------------------------------------------------------- 统计卡

    /**
     * 把统计卡的 `extraData` 摊成「名称 → 值」。两种下发形式都见过：
     *   - 平铺：`{"avgData":"6.9","countData":"303.0"}`（names 里配中文名）；
     *   - 成对：`{"aututu_score_avg":3041833,"aututu_score_count":25,...}`（按 avgSuffix
     *     找 `*_avg`，前缀经 names 换成中文名，再配上 `*_count`）。
     * 值做「上万折成 N.N万、整数去尾零」的美化，原始数字（如 3041833）会撑破一行。
     */
    fun stats(template: String?, extraData: String?): List<Pair<String, String>> {
        val obj = runCatching { JSONObject(extraData.orEmpty()) }.getOrNull() ?: return emptyList()
        val rule = rule(template)?.stats ?: StatsRule()
        val rows = mutableListOf<Pair<String, String>>()

        val avgKeys = obj.keys().asSequence().filter { it.endsWith(rule.avgSuffix) }.toList()
        if (avgKeys.isNotEmpty()) {
            avgKeys.forEach { key ->
                val prefix = key.removeSuffix(rule.avgSuffix)
                val value = obj.optString(key).takeIf { it.isNotBlank() } ?: return@forEach
                val name = rule.names[prefix] ?: rule.names[key] ?: prefix
                val countKey = prefix + rule.countSuffix
                val count = if (obj.has(countKey)) obj.optString(countKey) else ""
                rows += name to if (count.isNotBlank()) "${pretty(value)} · $count" else pretty(value)
            }
            return rows
        }

        obj.keys().forEach { key ->
            val value = obj.optString(key)
            if (value.isNotBlank()) rows += (rule.names[key] ?: key) to pretty(value)
        }
        return rows
    }

    private fun pretty(raw: String): String {
        val n = raw.toDoubleOrNull() ?: return raw
        return when {
            n >= 10000 -> String.format(java.util.Locale.CHINA, "%.1f万", n / 10000)
            n == n.toLong().toDouble() -> n.toLong().toString()
            else -> raw
        }
    }
}
