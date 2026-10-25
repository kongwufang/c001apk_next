package com.example.c001apk.util

import org.json.JSONObject

/**
 * 卡片渲染规则表：说明「哪个模板用哪个骨架、取哪些字段」。
 *
 * 服务端下发的顶层卡片模板实测已有 40 多种且随时会冒新的，给每个模板写死 ViewHolder 追不上。
 * 这里改成「App 提供有限骨架，规则表说明每个模板用哪个骨架、取哪些字段」，规则按实测结果
 * 全部写死在 [BUILTIN] 里。
 *
 * 字段里带 `|` 是回退链：取第一个非空值（同一位置在不同模板下换字段名是常态）。
 *
 * 链里的字段名**不受「model 里声明过什么」限制**：卡片和实体都留了一份原始 JSON
 * （`HomeFeedResponse.Data.raw` / `Entities.raw`），服务端把商品图叫 `goods_pic`、
 * 把热搜热度叫 `sub_title`，规则照配就能取到。以前取值是硬编码的九个字段名，
 * 配什么都取不到，只能往 `Entities` 里加字段发版。
 *
 * 表里没配过的模板不必逐个补：按实体数量推断兜底，没见过的新模板也能排得像样。
 */
object CardUi {

    // ---- 骨架（App 内置能力，规则表只能挑不能造） ----
    const val AUTO = "auto"
    const val LIST = "list"
    const val HSCROLL = "hscroll"
    const val GRID = "grid"

    /** 把 extraData 摊成若干行「名称 值」（子版块统计那类） */
    const val STATS = "stats"

    /**
     * 照官方重画的续航卡（模板 `subTabFeedCard1`）：80dp 卡 + 电池刻度条 + 平均亮屏。
     * 官方是专门的 Compose 布局 BatteryLifeSummaryCard，摊成「名称 值」完全不像，所以这里
     * 不再走 [STATS] 兜底，改走专属渲染。渲染与尺寸见 [GenericCardRenderer] / `_rev/SUBTAB_CARDS_SPEC.md`。
     */
    /** 到手价卡（subTabFeedCard6）：extraData 里是 final_price / update_time */
    const val SUBTAB_PRICE = "subTabPrice"
    const val SUBTAB_BATTERY = "subtabBattery"

    /** 照官方重画的跑分卡（模板 `subTabFeedCard2`）：2x2 品牌渐变格，官方对应 ScoreGridCard */
    const val SUBTAB_SCORE = "subtabScore"

    /**
     * 一行居中的 pill 链接（模板 `selectorLinkCard`）：官方布局是个壳（CoolapkCardView 里挂一个
     * 居中的流式容器），实体是一条条带自己 url 的 `selectorLink`。按宫格排会变成一堆「无图标 +
     * 标题」的方块，完全不像，所以走 [GenericCardRenderer] 的专属渲染。
     */
    const val LINKS = "links"

    /**
     * 分组标题行（模板 `titleCard`）：左边加粗分组名、右边「更多」，整卡可点。官方布局是个空壳
     * （标题由 ViewHolder 填），按宫格排会把「更多」顶到第二行、还多出几个空方块，所以走专属渲染。
     */
    const val SECTION = "section"

    /**
     * 两栏并排的图文按钮（模板 `iconButtonGridCard`）：官方是「宽高比 2.44 的背景图 + 压在图上
     * 居中的文字」。实体下发的 pic 就是这种宽图，按宫格排会把它当图标塞进方形槽里，完全不像，
     * 所以走专属渲染。
     */
    const val ICON_BUTTONS = "iconButtons"

    /**
     * 横向滚动的分类入口（模板 `linkCard`）：官方根就是一条 56dp 高的横滚行，里面是圆角 14dp 的
     * 文字胶囊（item_link_card.xml + item_link_card_tab.xml）。配成宫格会排成 4 个方块，不像。
     */
    const val LINK_TABS = "linkTabs"

    /**
     * 标题 + 胶囊列表（模板 `capsuleListCard`）：官方是「16dp 图标 + 加粗标题」一行，下面一堆
     * 热词胶囊（item_capsule_list.xml）。实体是 `hotSearch`，宫格排法会浪费掉标题行。
     */
    const val CAPSULES = "capsules"

    /**
     * 大图打底的酷安集条目（**实体**模板 `albumExpandCard`）：官方是整卡封面 + 白字标题/说明 +
     * 头像与应用图标叠放（item_album_expand_card.xml）。实体是一条完整 album，按通用实体排法
     * （图标 + 标题）完全不像。
     */
    const val ALBUM_EXPAND = "albumExpand"

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

    /**
     * 跑分项前缀 → 中文名（真源是产品页 tab 的 `rule.name`）。
     * 跑分卡现在走照官方重画的专属渲染，但「前缀 → 展示名」仍以这张表为唯一真源，
     * [GenericCardRenderer] 直接复用，免得改一处名字要改两个文件。
     */
    val SCORE_NAMES = mapOf(
        "aututu" to "安兔兔跑分",
        "geek_bench_single" to "GeekBench 单核",
        "geek_bench_multi" to "GeekBench 多核",
        "3d_mark" to "WildLife Extreme"
    )

    /**
     * 跑分卡 `extraData` 的键是 `<rule.name>_score_avg`，比 [SCORE_NAMES] 的键多一段 `_score`
     * （2026-10-11 实测：`aututu_score_avg` / `aututu_score_count`）。
     * 查 names 时这一层要去掉再试一次，否则两种配法有一种必然匹配不到、直接显示英文键名。
     */
    private const val SCORE_SUFFIX = "_score"

    /**
     * 实体字段的默认回退链。
     *
     * 服务端字段名五花八门：应用列表给 `logo`、动态给 `pic`、京东商品给 `goods_pic`、
     * 热搜的热度给 `sub_title`、机型别名给 `alias_title`……而 `Entities` 只声明了一部分。
     * 实体现在带着原始 JSON（`Entities.raw`），这里配什么名字就能取到什么，
     * 所以默认链尽量放宽 —— 规则表只在需要**改优先级**时才覆盖。
     *
     * `entityTypeName` 一律排最后：它多半是 `pear_goods`、`product` 这类内部名，
     * 有真实描述时不该把它顶出来（京东商品卡以前就显示成「商品名 / pear_goods」）。
     */
    const val DEFAULT_ICON = "logo|pic|goods_pic|icon|tpic|userAvatar|cover"
    const val DEFAULT_ITEM_TITLE = "title|goods_title|device_title|alias_title|config_name|name"
    const val DEFAULT_ITEM_SUBTITLE =
        "sub_title|subTitle|description|message|goods_promo_title|goods_promo_price|entityTypeName"

    /** 卡片自身的字段默认链。卡片级字段名比实体级稳定（就那十几个），补下划线变体即可 */
    const val DEFAULT_CARD_TITLE = "title|goods_title"
    const val DEFAULT_CARD_SUMMARY = "description|subTitle|sub_title|message"
    const val DEFAULT_CARD_HERO = "pic"

    data class ItemRule(
        val shape: String = SHAPE_AUTO,
        val icon: String = DEFAULT_ICON,
        val title: String = DEFAULT_ITEM_TITLE,
        val subtitle: String = DEFAULT_ITEM_SUBTITLE,
        val action: String = "url"
    ) {
        /** 上面几个字段写的是 `|` 回退链，渲染前拆成列表（与 [CardUi.chain] 同一套规则） */
        val iconChain: List<String> get() = CardUi.chain(icon, DEFAULT_ICON)
        val titleChain: List<String> get() = CardUi.chain(title, DEFAULT_ITEM_TITLE)
        val subtitleChain: List<String>
            get() = CardUi.chain(subtitle, DEFAULT_ITEM_SUBTITLE)
    }

    data class StatsRule(
        val names: Map<String, String> = emptyMap(),
        val avgSuffix: String = "_avg",
        val countSuffix: String = "_count"
    )

    data class Rule(
        val layout: String = AUTO,
        val span: Int = 0,
        val titleField: String = DEFAULT_CARD_TITLE,
        val summaryField: String = DEFAULT_CARD_SUMMARY,
        val heroField: String = DEFAULT_CARD_HERO,
        val item: ItemRule? = null,
        val stats: StatsRule? = null
    )

    data class CardLayout(
        val horizontal: Boolean = false,
        val span: Int = 1,
        val stats: Boolean = false,
        val hidden: Boolean = false,
        /**
         * 非空表示这条模板走「照官方重画」的专属渲染，值就是骨架名（[SUBTAB_BATTERY] /
         * [SUBTAB_SCORE] 这些）。普通骨架都为 null，所以老规则不受影响。
         */
        val official: String? = null
    )

    private val HSCROLL_TEMPLATES = listOf(
        "apkScrollCard", "apkScrollCardWithBackground", "apkImageScrollCard", "apkImageCard",
        "colorfulScrollCard", "iconLargeScrollCard", "feedScrollCard",
        "iconScrollCard", "imageScrollCard", "imageCarouselCard", "iconMiniScrollCard"
    )

    private val LIST_TEMPLATES = listOf(
        "iconListCard", "apkListCard", "listCard", "feedListCard", "productTimelineListCard"
    )

    /** 宫格模板 → 每行几个（真源：_rev/card_tpl_shape.py 实测的实体形状） */
    private val GRID_TEMPLATES = mapOf(
        "subTabLinkCard" to 2,
        "verticalColumnsFullPageCard" to 2,
        "feedCoolPictureGridCard" to 2,
        "rankAwardCard" to 3,
        "iconMiniLinkGridCard" to 3,
        "iconTabLinkGridCard" to 4,
        "iconLinkGridCard" to 5
    )

    private val BUILTIN: Map<String, Rule> = run {
        val m = mutableMapOf<String, Rule>()
        // 子版块续航卡 / 跑分卡：官方是专门的 Compose 布局（电池刻度条、2x2 品牌渐变格），
        // 原先摊成「名称 值」完全不像官方，这里改走照官方重画的专属渲染
        // （见 GenericCardRenderer.renderSubTabBattery / renderSubTabScore）
        m["subTabFeedCard1"] = Rule(layout = SUBTAB_BATTERY)
        m["subTabFeedCard2"] = Rule(layout = SUBTAB_SCORE)
        // 到手价（subTabFeedCard6）：extraData = {final_price, update_time, feed_id}
        m["subTabFeedCard6"] = Rule(layout = SUBTAB_PRICE)
        m["subTabFeedCard3"] = Rule(layout = SUBTAB_SCORE)
        // 首页/发现页顶部那排分类入口：官方是一行居中的 pill 链接（走专属渲染），
        // 配成宫格会排成一堆方块
        m["selectorLinkCard"] = Rule(layout = LINKS)
        // 分组标题行（「应用更新」那类）：官方是一行「标题 + 更多」，配成宫格会把「更多」
        // 挤到第二行，还会多出几个空方块
        m["titleCard"] = Rule(layout = SECTION)
        // 「试试手气 / 爆棚热门」这类两栏图文按钮：官方把宽图当底、文字压在图上
        m["iconButtonGridCard"] = Rule(layout = ICON_BUTTONS)
        // 「应用分类 / 游戏分类」这类横向滚动的分类入口（linkCard）：官方是一条 56dp 高的
        // 横滚胶囊行，按宫格排会变成 4 个方块
        m["linkCard"] = Rule(layout = LINK_TABS)
        // 热搜胶囊（capsuleListCard）：官方是「图标 + 加粗标题」一行 + 一片胶囊，
        // 实体是 hotSearch，按宫格排会把标题行丢掉
        m["capsuleListCard"] = Rule(layout = CAPSULES)
        // 置顶引导（topContent）不在这张表里：官方是话题/机型页单独画的一行式卡片，见
        // GenericCardRenderer.renderTopContent —— 原先这里配的 row 形状会把标题挤成 0 宽
        // 纯文本卡
        m["messageCard"] = Rule(layout = TEXT)
        m["textCard"] = Rule(layout = TEXT)
        // 实测是「自绘广告占位卡」（只有 sponsorType / reward_type，没内容）：配 auto，
        // 真有内容时能按实体排出来，没内容时自然不占高度，比写死 text 稳
        m["sponsorArticleNews"] = Rule(layout = AUTO)

        HSCROLL_TEMPLATES.forEach { m[it] = Rule(layout = HSCROLL) }
        LIST_TEMPLATES.forEach { m[it] = Rule(layout = LIST) }
        GRID_TEMPLATES.forEach { (t, span) -> m[t] = Rule(layout = GRID, span = span) }

        // 下面两条是实测过的例外，必须排在上面几个批量模板之后才不会被盖掉
        // 图片放大卡：实测是整宽大图 + 标题，按实体数推断会排成宫格
        m["imageScaleCard"] = Rule(layout = LIST)   // 实际渲染被 GenericCardRenderer 的 IMAGE_SCALE_CARD 分支拦截 ✓
        // 数码首页那排商品横滚：实体里是 goods_pic / goods_promo_price，配成 tile 形状才排得出价格行
        m["iconLargeScrollCard"] = Rule(
            layout = HSCROLL,
            item = ItemRule(
                shape = SHAPE_TILE,
                icon = "goods_pic|logo|pic",
                title = "goods_title|title",
                subtitle = "goods_promo_price|goods_promo_title|sub_title"
            )
        )

        // 实测是 1080x240 的全宽横幅（不是横滚小图），竖排整宽才对
        m["imageScaleCard"] = Rule(layout = LIST)   // 实际渲染被 GenericCardRenderer 的 IMAGE_SCALE_CARD 分支拦截 ✓
        m
    }

    fun rule(template: String?): Rule? = template?.let { BUILTIN[it] }

    /**
     * 实体级（**不是卡片级**）的官方重画表：实体自己的 `entityTemplate` 决定单个实体怎么画，
     * 外层卡片照常走骨架。
     *
     * 实测 `albumExpandCard` 就是这样：外层卡片是 `listCard`（标题「推荐应用集」），里面的
     * album 实体自带 `entityTemplate=albumExpandCard`（见
     * `_rev/card_scan_out/16_V8_MARKET_ALBUM.json`）。DEX 侧也对得上 ——
     * EntityListFragment 的卡片模板大表里没有它，它是单独一个按模板名判断的方法
     * （`_rev/card_scan_out/_FEED_CHAIN.txt` 里 `ʱ(Ljava/lang/Object;)Z → 模板名: albumExpandCard`
     * + 紧跟一个建 ViewHolder 的方法）。所以这张表配的是**实体**模板，别往下面的卡片表里加。
     */
    private val ENTITY_OFFICIAL = mapOf(
        "albumExpandCard" to ALBUM_EXPAND
    )

    /** 实体级官方重画：返回该实体要用的专属布局名，没有就返回 null（走通用实体排版） */
    fun entityLayout(entityTemplate: String?): String? =
        entityTemplate?.let { ENTITY_OFFICIAL[it] }

    /**
     * 实体的排列方式。规则表配了就用配的；没配过的模板按实体数量推断
     * （一两条竖排、四五条宫格、再多横滚），所以没见过的新模板也能排得像样。
     */
    fun layout(template: String?, entityCount: Int): CardLayout {
        val r = rule(template)
        when (r?.layout) {
            HIDDEN -> return CardLayout(hidden = true)
            STATS -> return CardLayout(stats = true)
            SUBTAB_BATTERY -> return CardLayout(official = SUBTAB_BATTERY)
            SUBTAB_SCORE -> return CardLayout(official = SUBTAB_SCORE)
            // 2026-10-25 补：到手价卡（subTabFeedCard6）也要走「照官方重画」
            //   ⚠️ 之前只加了 m["subTabFeedCard6"] 映射 ✗ 忘了在这里分派 ✗
            //   ⇒ official 一直 null ⇒ 掉回通用路径 ⇒ 界面显示卡片标题「子版块统计」✗
            SUBTAB_PRICE -> return CardLayout(official = SUBTAB_PRICE)
            LINKS -> return CardLayout(official = LINKS)
            SECTION -> return CardLayout(official = SECTION)
            ICON_BUTTONS -> return CardLayout(official = ICON_BUTTONS)
            LINK_TABS -> return CardLayout(official = LINK_TABS)
            CAPSULES -> return CardLayout(official = CAPSULES)
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
                // 配 `<rule.name>_score`（服务端实际键形）或 `<rule.name>`（内置表写法）都能命中
                val name = rule.names[prefix]
                    ?: rule.names[prefix.removeSuffix(SCORE_SUFFIX)]
                    ?: rule.names[key]
                    ?: prefix
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
