package com.example.c001apk.adapter

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemHomeGenericCardAlbumExpandBinding
import com.example.c001apk.databinding.ItemHomeGenericCardBatteryBinding
import com.example.c001apk.databinding.ItemHomeGenericCardBinding
import com.example.c001apk.databinding.ItemHomeGenericCardCapsulesBinding
import com.example.c001apk.databinding.ItemHomeGenericCardEntityBinding
import com.example.c001apk.databinding.ItemHomeGenericCardIconButtonBinding
import com.example.c001apk.databinding.ItemHomeGenericCardIconButtonsBinding
import com.example.c001apk.databinding.ItemHomeGenericCardLinkTabsBinding
import com.example.c001apk.databinding.ItemHomeGenericCardLinksBinding
import com.example.c001apk.databinding.ItemHomeGenericCardScoreBinding
import com.example.c001apk.databinding.ItemHomeGenericCardSectionBinding
import com.example.c001apk.databinding.ItemHomeGenericCardScoreItemBinding
import com.example.c001apk.databinding.ItemHomeGenericCardTopContentRowBinding
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.util.CardUi
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.dp
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.color.MaterialColors
import com.google.gson.JsonObject
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale

/**
 * 通用卡片渲染：把服务端下发的任意卡片画出来，不再为每个模板写 ViewHolder。
 *
 * 「怎么画」不再写死在这里，而是查 [CardUi] 的规则表（App 内置默认表 + 服务端热更新的覆盖
 * 表）：模板 → 骨架（竖排/横滚/宫格/统计/纯文本）+ 字段映射（标题取谁、副标题取谁）。
 * 规则表里没有的模板按实体数量推断排列、按实体内容推断形状，所以完全没见过的新卡片也能出内容。
 *
 * 注意：`HomeFeedResponse.Entities` 的 url/pic/title 声明成非空，但 Gson 不走构造器，
 * 运行时可能是 null（本项目踩过），所以全程按可空处理。
 */
object GenericCardRenderer {

    fun render(
        binding: ItemHomeGenericCardBinding,
        data: HomeFeedResponse.Data,
        listener: ItemListener
    ) {
        val template = data.entityTemplate
        val entities = data.entities.orEmpty().filter { it.hasContent() }

        // 骨架内边距按模板可变（见下面 renderTopContent），而 ViewHolder 是回收复用的，
        // 先复位成 XML 里的 12dp，免得用上一次的模板留下的值
        binding.content.setPadding(CARD_PADDING.dp, CARD_PADDING.dp, CARD_PADDING.dp, CARD_PADDING.dp)
        binding.topContent.isVisible = false
        binding.topContent.removeAllViews()
        // 照官方重画的卡片是直接 addView 进 content 的，而 ViewHolder 会回收复用，
        // 先把自己上一轮塞进去的那张摘掉，否则越叠越多
        binding.content.findViewWithTag<View>(OFFICIAL_TAG)?.let { binding.content.removeView(it) }

        // 置顶内容卡官方不是通用卡片，是话题/机型页专门写的一行式「置顶内容」，照官方单独画
        if (template == TOP_CONTENT) {
            renderTopContent(binding, entities, listener)
            return
        }

        val spec = CardUi.layout(template, entities.size)

        // 照官方重画的卡片（续航卡 / 跑分卡）：官方这几张是专门写的 Compose 布局（电池刻度条、
        // 2x2 品牌渐变格），按通用骨架摊成「名称 值」完全不像，所以和 topContent 一样单独画。
        // 反编译记录与尺寸表见 _rev/SUBTAB_CARDS_SPEC.md。
        if (spec.official != null) {
            renderOfficialCard(binding, spec.official, template, data, listener, entities)
            return
        }

        // 卡片自身四块（取哪些字段可以被规则表改，默认还是原来那套）
        val title = pickCard(data, CardUi.titleField(template))
        val summary = pickCard(data, CardUi.summaryField(template))?.let { plainText(it) }
        // 卡片自带配图只在没有实体时当主图（imageScaleCard、只有图的 fabCard）
        val hero = pickCard(data, CardUi.heroField(template))?.takeIf { entities.isEmpty() }

        binding.title.isVisible = !title.isNullOrEmpty()
        binding.title.text = title
        binding.summary.isVisible = !summary.isNullOrEmpty()
        binding.summary.text = summary
        binding.hero.isVisible = hero != null
        if (hero != null) ImageUtil.showIMG(binding.hero, hero)

        // 统计卡（subTabFeedCard1/2 那类）：内容全在 extraData 里，摊成几行「名称 值」
        val stats = if (spec.stats) CardUi.stats(template, data.extraData) else emptyList()
        binding.stats.removeAllViews()
        binding.stats.isVisible = stats.isNotEmpty()
        stats.forEach { (name, value) -> binding.stats.addView(statRow(binding, name, value)) }

        val showEntities = entities.isNotEmpty() && !spec.stats && !spec.hidden
        binding.recyclerView.isVisible = showEntities
        if (!showEntities) {
            binding.recyclerView.adapter = null
        } else {
            binding.recyclerView.layoutManager = if (spec.horizontal) {
                LinearLayoutManager(
                    binding.root.context, LinearLayoutManager.HORIZONTAL, false
                )
            } else {
                GridLayoutManager(binding.root.context, spec.span.coerceAtLeast(1))
            }
            binding.recyclerView.adapter = GenericCardEntityAdapter(
                entities, listener, spec.horizontal, CardUi.itemRule(template)
            )
        }

        // 一个能显示的东西都没有（sponsorArticleNews 只给 extraData 就是这种）：
        // 正式包里不占位，别把「暂不支持」这种技术字样甩给用户；debug 频道留一行标明模板名排障
        val label = template?.takeIf { it.isNotBlank() } ?: data.entityType
        val renderable = !title.isNullOrEmpty() || !summary.isNullOrEmpty() ||
                entities.isNotEmpty() || hero != null || stats.isNotEmpty()
        val debugTip = BuildConfig.HTTP_LOG && !label.isNullOrEmpty()
        binding.tip.isVisible = debugTip
        if (debugTip) {
            binding.tip.text = binding.root.context.getString(
                if (renderable) R.string.generic_card else R.string.unsupported_card, label
            )
        }
        binding.root.isVisible = (renderable || debugTip) && !spec.hidden

        val url: String? = data.url
        val clickable = !url.isNullOrEmpty()
        binding.root.isClickable = clickable
        binding.root.isFocusable = clickable
        binding.root.setOnClickListener(
            if (clickable) View.OnClickListener { view -> listener.onOpenLink(view, url, title) }
            else null
        )
    }

    /**
     * 置顶内容卡（topContent）：官方没走卡片体系，是话题/机型页专门写的一行式「置顶内容」，
     * 这里照官方 NodeTopContentComposeUI.kt 的 TopContentNewHeadlineUI 重写（反编译记录留在
     * _rev/node_top_out/sources/defpackage/w0b.java，外层容器见同目录 c1b.java）：
     *
     * 每行 = 18dp 图标（染主题色）+ 标签 + 标题 + 右侧箭头，行内水平 12dp / 垂直 8dp，整行可点；
     * 卡片自身没有标题 / 说明 / 配图，也不是整卡可点 —— 要跳的目标在每条实体自己的 url 上
     * （这卡的实体 url 是 /t/xxx，卡片自己的 url 是 null）。
     */
    private fun renderTopContent(
        binding: ItemHomeGenericCardBinding,
        entities: List<HomeFeedResponse.Entities>,
        listener: ItemListener
    ) {
        binding.title.isVisible = false
        binding.summary.isVisible = false
        binding.hero.isVisible = false
        binding.stats.removeAllViews()
        binding.stats.isVisible = false
        binding.recyclerView.adapter = null
        binding.recyclerView.isVisible = false

        // 官方的留白全在行里（水平 12dp / 垂直 8dp），骨架自己的 12dp 要清掉，否则叠成 24dp
        binding.content.setPadding(0, 0, 0, 0)
        binding.topContent.isVisible = entities.isNotEmpty()
        entities.forEach { entity ->
            binding.topContent.addView(topContentRow(binding, entity, listener))
        }

        // 卡片自己没有 url：别给出「会亮但点了没反应」的按压反馈
        binding.root.isClickable = false
        binding.root.isFocusable = false
        binding.root.setOnClickListener(null)

        // 排障提示：debug 频道标出「这支是照官方重画的」，正式包看不到
        val debugTip = BuildConfig.HTTP_LOG
        binding.tip.isVisible = debugTip
        if (debugTip) {
            binding.tip.text = binding.root.context.getString(R.string.official_card, TOP_CONTENT)
        }
        binding.root.isVisible = entities.isNotEmpty() || debugTip
    }

    /** 置顶内容卡的一行，排布照官方 TopContentNewHeadlineUI（见行布局文件里的注释） */
    private fun topContentRow(
        binding: ItemHomeGenericCardBinding,
        entity: HomeFeedResponse.Entities,
        listener: ItemListener
    ): View {
        val row = ItemHomeGenericCardTopContentRowBinding.inflate(
            LayoutInflater.from(binding.root.context), binding.topContent, false
        )

        // 官方的 logo 是 18dp 单色底图，颜色在布局里统一染成主题强调色
        val logo: String? = entity.logo
        row.icon.isVisible = !logo.isNullOrEmpty()
        if (!logo.isNullOrEmpty()) ImageUtil.showIMG(row.icon, logo)

        // entityTypeName 是标签（「来点评」），官方排在标题左边、用强调色；没有就整块不留
        val tag: String? = entity.entityTypeName
        row.tag.isVisible = !tag.isNullOrEmpty()
        row.tag.text = tag

        val title = entity.title.orEmpty()
        row.title.isVisible = title.isNotEmpty()
        row.title.text = title

        // 点这一行进实体自己的 url；apk 实体在这张卡里没有（官方只认头条这类实体）
        val url: String? = entity.url
        if (url.isNullOrEmpty()) {
            row.root.isClickable = false
            row.root.isFocusable = false
            row.root.setOnClickListener(null)
        } else {
            row.root.isClickable = true
            row.root.isFocusable = true
            row.root.setOnClickListener { view -> listener.onOpenLink(view, url, title) }
        }
        return row.root
    }

    /** 统计卡的一行：左名称（灰）、右数值（加粗），值太长时截断 */
    private fun statRow(binding: ItemHomeGenericCardBinding, name: String, value: String): View {
        val context = binding.root.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 2.dp, 0, 2.dp)
        }
        row.addView(TextView(context).apply {
            text = name
            textSize = 13f
            setTextColor(GRAY)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        row.addView(TextView(context).apply {
            text = value
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8.dp }
        })
        return row
    }

    /**
     * 照官方重画的那几张卡片的分派入口。
     *
     * 这几张卡的官方实现都不走通用卡片体系：产品页子版块那几张是专门的 Compose 布局，
     * `selectorLinkCard` 是 CoolapkCardView 里挂一个流式的私有控件。所以这里不复用骨架的
     * title/summary/实体列表，而是把它们全部收起来，再按 [official] 挑一块专属布局填进去。
     * 尺寸来源：Compose 那几张见 `_rev/SUBTAB_CARDS_SPEC.md`，`selectorLinkCard` / `titleCard`
     * 见 item_home_generic_card_links.xml / item_home_generic_card_section.xml 的注释。
     */
    private fun renderOfficialCard(
        binding: ItemHomeGenericCardBinding,
        official: String,
        template: String?,
        data: HomeFeedResponse.Data,
        listener: ItemListener,
        entities: List<HomeFeedResponse.Entities>
    ) {
        binding.title.isVisible = false
        binding.summary.isVisible = false
        binding.hero.isVisible = false
        binding.stats.removeAllViews()
        binding.stats.isVisible = false
        binding.recyclerView.adapter = null
        binding.recyclerView.isVisible = false
        binding.topContent.isVisible = false
        binding.topContent.removeAllViews()

        // 官方的留白全在自己那块布局里，骨架自带的 12dp 要清掉，否则叠成 24dp
        binding.content.setPadding(0, 0, 0, 0)

        val extra = parseExtra(data.extraData)
        val cardView = when (official) {
            CardUi.SUBTAB_BATTERY -> bindBatteryCard(binding, extra)
            CardUi.SUBTAB_SCORE -> bindScoreCard(binding, extra)
            CardUi.LINKS -> bindLinksCard(binding, entities, listener)
            CardUi.SECTION -> bindSectionCard(binding, template, data)
            CardUi.ICON_BUTTONS -> bindIconButtonsCard(binding, entities, listener)
            CardUi.LINK_TABS -> bindLinkTabsCard(binding, entities, listener)
            CardUi.CAPSULES -> bindCapsulesCard(binding, template, data, entities, listener)
            else -> null
        }
        if (cardView != null) {
            // 打标记，好让 render() 开头能把上一轮塞进 content 的那张摘掉（ViewHolder 会复用）
            cardView.tag = OFFICIAL_TAG
            binding.content.addView(cardView)
        }

        // 卡片 url 为空时官方不带跳转（续航卡、跑分卡、selectorLinkCard 都是这种）：
        // 别给出「会亮但点了没反应」的按压反馈
        val url: String? = data.url
        val clickable = !url.isNullOrEmpty()
        binding.root.isClickable = clickable
        binding.root.isFocusable = clickable
        binding.root.setOnClickListener(
            if (clickable) View.OnClickListener { view -> listener.onOpenLink(view, url, data.title) }
            else null
        )

        // 排障提示：debug 频道标出「这支是照官方重画的」，正式包看不到
        val debugTip = BuildConfig.HTTP_LOG && !template.isNullOrEmpty()
        binding.tip.isVisible = debugTip
        if (debugTip) {
            binding.tip.text = binding.root.context.getString(R.string.official_card, template)
        }
        // 专属布局没画出来时不占位（比如 selectorLinkCard 一条链接都没有）
        binding.root.isVisible = cardView != null || debugTip
    }

    /**
     * 续航卡（`subTabFeedCard1`），照官方 BatteryLifeSummaryCard 排
     * （尺寸与配色对应关系写在 item_home_generic_card_battery.xml 的注释里）。
     *
     * 数据只有两个字段：`avgData` 是平均亮屏小时数、`countData` 是提供数据的人数。
     * 官方在人数解析不出正数时不显示右上角那句，在小时数解析不出正数时把「平均亮屏 + 刻度条」
     * 整块收起来、只留标题行 —— 这里照做，别拿 0 去画一根空电池。
     */
    private fun bindBatteryCard(
        binding: ItemHomeGenericCardBinding,
        extra: JSONObject?
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardBatteryBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        // 官方这三处是硬编码中文，不走 strings.xml（跟 topContent 那套一致）
        card.batteryTitle.text = "续航时长"
        card.batteryAvgLabel.text = "平均亮屏"
        card.batteryUnit.text = "小时"

        val count = extra.number("countData")
        val hasCount = count != null && count > 0f
        card.batteryCount.isVisible = hasCount
        card.batteryCount.text = if (hasCount && count != null) "${count.toLong()}人提供数据" else ""

        val hours = extra.number("avgData")
        val hasData = hours != null && hours > 0f
        card.batteryAvgRow.isVisible = hasData
        card.batterySlider.isVisible = hasData
        if (hasData && hours != null) {
            card.batteryAvg.text = compactNumber(hours)
            card.batterySlider.setHours(hours)
        }
        return card.root
    }

    /**
     * 跑分卡（`subTabFeedCard2`），照官方 ScoreGridCard 排：两行两格，每格是「品牌名 → 分数 →
     * N 人分享」加右下角品牌图标，底子是「顶部品牌浅色 → 底部透明」的垂直渐变（暗色主题另给一套
     * 起始色）。四格的顺序、图标、强调色、渐变起始色都照官方写死，见 [SCORE_ITEMS]。
     *
     * extraData 是成对的 `xxx_score_avg` / `xxx_score_count`；某一格没数据时官方那格显示
     * 「暂无人分享」并且分数行用次要色，这里照做。
     */
    private fun bindScoreCard(
        binding: ItemHomeGenericCardBinding,
        extra: JSONObject?
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardScoreBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        val inflater = LayoutInflater.from(context)
        val rows = listOf(card.scoreRow1, card.scoreRow2)
        rows.forEach { it.removeAllViews() }

        val night = isNight(context)
        // 注意：MaterialColors.getColor 没有 (Context, attr) 这个两参重载（只有 (View, attr) 和
        // (Context, attr, 缺省值)），传 context 会被匹配到 View 那个签名而编译不过
        val divider =
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
        val muted = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        SCORE_ITEMS.forEachIndexed { index, item ->
            val row = rows[index / 2]
            val cell = ItemHomeGenericCardScoreItemBinding.inflate(inflater, row, false)
            val score = extra.number("${item.prefix}_score_avg")
            val count = extra.number("${item.prefix}_score_count")

            cell.scoreTitle.text = CardUi.SCORE_NAMES[item.prefix]

            val hasScore = score != null && score > 0f
            cell.scoreValue.text =
                if (hasScore && score != null) compactNumber(score) else "暂无人分享"
            cell.scoreValue.setTextColor(if (hasScore) item.accent else muted)
            cell.scoreValue.textSize = if (hasScore) 18f else 14f

            val hasCount = count != null && count > 0f
            cell.scoreCount.isVisible = hasCount
            cell.scoreCount.text =
                if (hasCount && count != null) "${count.toLong()}人分享" else ""

            cell.scoreIcon.setImageResource(item.icon)

            cell.root.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(if (night) item.darkTop else item.lightTop, Color.TRANSPARENT)
            ).apply {
                cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, divider)
            }

            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (index % 2 == 1) lp.marginStart = 8.dp
            row.addView(cell.root, lp)
        }
        return card.root
    }

    /**
     * 两栏并排的图文按钮（模板 `iconButtonGridCard`），照官方 item_icon_button_grid_card.xml：
     * 卡片左右各留 12dp，里面两栏等宽并排、间距 8dp；每栏一张宽高比 2.44 的图（实测实体下发的
     * 就是 @468x192 这种宽图），文字压在图上居中。官方那栏里还有一枚 18dp 图标，实测这个实体
     * 不带图标字段，所以只有文字。每一栏点自己的 url。
     */
    private fun bindIconButtonsCard(
        binding: ItemHomeGenericCardBinding,
        entities: List<HomeFeedResponse.Entities>,
        listener: ItemListener
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardIconButtonsBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        card.iconButtonsColumn.removeAllViews()
        val titleChain = CardUi.chain(CardUi.DEFAULT_ITEM_TITLE, CardUi.DEFAULT_ITEM_TITLE)

        entities.chunked(2).forEach { rowEntities ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            rowEntities.forEachIndexed { index, entity ->
                val cell = ItemHomeGenericCardIconButtonBinding.inflate(
                    LayoutInflater.from(context), row, false
                )
                cell.root.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    if (index > 0) marginStart = 8.dp
                }
                ImageUtil.showIMG(cell.iconButtonImage, entity.pic)
                val title = pickEntity(entity, titleChain)
                cell.iconButtonTitle.isVisible = !title.isNullOrEmpty()
                cell.iconButtonTitle.text = title
                val url: String? = entity.url
                if (!url.isNullOrEmpty()) {
                    cell.root.isClickable = true
                    cell.root.isFocusable = true
                    cell.root.setOnClickListener { view ->
                        listener.onOpenLink(view, url, title.orEmpty())
                    }
                }
                row.addView(cell.root)
            }
            card.iconButtonsColumn.addView(row)
        }
        return card.root
    }

    /**
     * 分组标题行（模板 `titleCard`）：左边加粗分组名，右边「更多」，整卡可点。
     * 官方布局 item_title_card.xml 是个空壳（里面只有一个 Space），标题由 ViewHolder 动态填，
     * 实测下发的就是 `title` + `subTitle`（「更多」）+ 卡片 url。卡片没 url 时「更多」没有去处，
     * 按官方收起来。
     */
    private fun bindSectionCard(
        binding: ItemHomeGenericCardBinding,
        template: String?,
        data: HomeFeedResponse.Data
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardSectionBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        card.sectionTitle.text = pickCard(data, CardUi.titleField(template))
        val more = pickCard(data, CardUi.summaryField(template))
        card.sectionMore.isVisible = !more.isNullOrEmpty() && !data.url.isNullOrEmpty()
        card.sectionMore.text = more
        return card.root
    }

    /**
     * 一行居中的 pill 链接（模板 `selectorLinkCard`），照官方 item_selector_link_view.xml：
     * CoolapkCardView 里挂一个居中的流式容器、上下 6dp；项样式照桌面版基准
     * （`.discovery-pill-btn`：高 30dp、圆角 15dp、浅底 + 1dp 描边、12.5sp 主色文字，带图标时
     * 15dp 图标排左、距文字 5dp）。
     *
     * 跳转目标是**每条实体自己的 url**（实测卡片自己的 url 为空），所以整卡不给点击反馈，
     * 可点的是一个个 pill。
     */
    private fun bindLinksCard(
        binding: ItemHomeGenericCardBinding,
        entities: List<HomeFeedResponse.Entities>,
        listener: ItemListener
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardLinksBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        card.linksRow.removeAllViews()

        val fill = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val stroke = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOutlineVariant
        )
        val textColor = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurface
        )
        val iconChain = CardUi.chain(CardUi.DEFAULT_ICON, CardUi.DEFAULT_ICON)
        val titleChain = CardUi.chain(CardUi.DEFAULT_ITEM_TITLE, CardUi.DEFAULT_ITEM_TITLE)

        entities.forEach { entity ->
            val title = pickEntity(entity, titleChain)
            if (title.isNullOrEmpty()) return@forEach

            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(13.dp, 0, 13.dp, 0)
                // 每个 pill 一份 drawable：共享同一个实例时某些 ROM 上按压/重绘会串
                background = GradientDrawable().apply {
                    cornerRadius = 15.dp.toFloat()
                    setColor(fill)
                    setStroke(1.dp, stroke)
                }
                layoutParams = FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 30.dp
                ).apply {
                    marginEnd = 8.dp
                    bottomMargin = 8.dp
                }
            }

            val icon = pickEntity(entity, iconChain)
            if (!icon.isNullOrEmpty()) {
                pill.addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(15.dp, 15.dp).apply { marginEnd = 5.dp }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    ImageUtil.showIMG(this, icon)
                })
            }

            pill.addView(TextView(context).apply {
                text = title
                textSize = 12.5f
                setTextColor(textColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            val url: String? = entity.url
            if (!url.isNullOrEmpty()) {
                pill.isClickable = true
                pill.isFocusable = true
                pill.setOnClickListener { view -> listener.onOpenLink(view, url, title) }
            }
            card.linksRow.addView(pill)
        }
        return card.root
    }

    /**
     * 横向滚动的分类入口（模板 `linkCard`），照官方 item_link_card.xml + item_link_card_tab.xml：
     * 一条 56dp 高的横滚行，里面一项是一个 28dp 的胶囊（实心浅底 + 14dp 圆角，文字左右各 10dp，
     * 项间距 12dp）。实测实体只给 title + url（样本里 4 条链接的 pic 是同一张遗留图，官方没用），
     * 所以不摆图，每一项点自己的 url。
     */
    private fun bindLinkTabsCard(
        binding: ItemHomeGenericCardBinding,
        entities: List<HomeFeedResponse.Entities>,
        listener: ItemListener
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardLinkTabsBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        card.linkTabs.removeAllViews()

        val fill = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val stroke = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOutlineVariant
        )
        val textColor = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurface
        )
        val titleChain = CardUi.chain(CardUi.DEFAULT_ITEM_TITLE, CardUi.DEFAULT_ITEM_TITLE)

        entities.forEach { entity ->
            val title = pickEntity(entity, titleChain)
            if (title.isNullOrEmpty()) return@forEach

            val tab = TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(10.dp, 0, 10.dp, 0)
                // 每项一份 drawable：共享同一个实例时某些 ROM 上按压/重绘会串
                background = GradientDrawable().apply {
                    cornerRadius = 14.dp.toFloat()
                    setColor(fill)
                    setStroke(1.dp, stroke)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 28.dp
                ).apply { marginEnd = 12.dp }
            }

            val url: String? = entity.url
            if (!url.isNullOrEmpty()) {
                tab.isClickable = true
                tab.isFocusable = true
                tab.setOnClickListener { view -> listener.onOpenLink(view, url, title) }
            }
            card.linkTabs.addView(tab)
        }
        return card.root
    }

    /**
     * 热搜胶囊（模板 `capsuleListCard`），照官方 item_capsule_list.xml：上面一行「16dp 图标 +
     * 加粗 16sp 标题」，下面一片胶囊（官方是内边距 6dp、上留白 4dp 的可滚动容器）。
     *
     * 两个实测结论决定了这里的画法：
     *   · 卡片自己的 title 实测是空串（extraData 里只有一个 includeTitleBackground），所以标题行
     *     按「有标题才显示」处理，别留一条空白；
     *   · 实体是 10 条 `hotSearch`，`sub_title` 是热度数字（1920 / 1886…），官方把热度带上来了，
     *     胶囊就画成「词 + 热度」；只认数字，免得别的模板借这条规则时把一段描述塞进胶囊。
     */
    private fun bindCapsulesCard(
        binding: ItemHomeGenericCardBinding,
        template: String?,
        data: HomeFeedResponse.Data,
        entities: List<HomeFeedResponse.Entities>,
        listener: ItemListener
    ): View {
        val context = binding.root.context
        val card = ItemHomeGenericCardCapsulesBinding.inflate(
            LayoutInflater.from(context), binding.content, false
        )
        card.capsuleRow.removeAllViews()

        val title = pickCard(data, CardUi.titleField(template))
        // 官方标题行左侧那枚 16dp 图标：卡片级没有专门的 icon 字段，按 logo/pic 兜底
        val icon = pickCard(data, listOf("logo", "pic", "icon"))
        card.capsuleTitleRow.isVisible = !title.isNullOrEmpty()
        card.capsuleTitle.text = title
        card.capsuleIcon.isVisible = !icon.isNullOrEmpty()
        if (!icon.isNullOrEmpty()) ImageUtil.showIMG(card.capsuleIcon, icon)

        val fill = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val stroke = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOutlineVariant
        )
        val textColor = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurface
        )
        val heatColor = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val titleChain = CardUi.chain(CardUi.DEFAULT_ITEM_TITLE, CardUi.DEFAULT_ITEM_TITLE)
        val heatChain = listOf("sub_title", "subTitle", "count", "num")

        entities.forEach { entity ->
            val word = pickEntity(entity, titleChain)
            if (word.isNullOrEmpty()) return@forEach

            val capsule = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(14.dp, 0, 14.dp, 0)
                background = GradientDrawable().apply {
                    cornerRadius = 16.dp.toFloat()
                    setColor(fill)
                    setStroke(1.dp, stroke)
                }
                layoutParams = FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 32.dp
                ).apply {
                    marginEnd = 8.dp
                    bottomMargin = 8.dp
                }
            }

            capsule.addView(TextView(context).apply {
                text = word
                textSize = 13f
                setTextColor(textColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            // 热度只认数字：`sub_title` 在别的实体上是文本，别把一段话当热度画出来
            val heat = pickEntity(entity, heatChain)?.takeIf { it.toFloatOrNull() != null }
            if (!heat.isNullOrEmpty()) {
                capsule.addView(TextView(context).apply {
                    text = heat
                    textSize = 11f
                    setTextColor(heatColor)
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = 6.dp }
                })
            }

            val url: String? = entity.url
            if (!url.isNullOrEmpty()) {
                capsule.isClickable = true
                capsule.isFocusable = true
                capsule.setOnClickListener { view -> listener.onOpenLink(view, url, word) }
            }
            card.capsuleRow.addView(capsule)
        }
        return card.root
    }

    /**
     * 按字段链取实体自己的字段，规则与 [pickCard] 一致：先查实体留的原始 JSON，
     * 再兜底声明过的字段。给不走 [GenericCardEntityAdapter] 的专属渲染取数用
     * （比如 selectorLinkCard 的 pill 标题和图标）。
     */
    private fun pickEntity(entity: HomeFeedResponse.Entities, chain: List<String>): String? {
        chain.forEach { name ->
            entity.raw?.jsonString(name)?.let { return it }
            val field: Any? = when (name) {
                "title" -> entity.title
                "pic" -> entity.pic
                "logo" -> entity.logo
                "url" -> entity.url
                "entityTypeName" -> entity.entityTypeName
                else -> null
            }
            (field as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** extraData 是 JSON 字符串；解析不出来（空的、被截断的）就当没有，别让整卡崩掉 */
    private fun parseExtra(extraData: String?): JSONObject? =
        runCatching { JSONObject(extraData.orEmpty()) }.getOrNull()

    /**
     * 从 extraData 取数值。服务端这两种写法都下发过：`"6.9"` 这种字符串、以及裸数字，
     * 统一走 [JSONObject.optString] 再转，两种都能吃。字段不在就直接当没有 ——
     * 官方「暂无人分享」那套就是靠 `has(key)` / 值 > 0 判的，别把缺失当成 0。
     */
    private fun JSONObject?.number(key: String): Float? {
        val obj = this ?: return null
        if (!obj.has(key)) return null
        return obj.optString(key).toFloatOrNull()
    }

    /** 官方 m2947 那套：整数就显示整数，带小数才保留一位（`2814` / `6.9`） */
    private fun compactNumber(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)

    private fun isNight(context: android.content.Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    /**
     * 跑分卡四格的固定顺序与配色，前缀跟 [CardUi.SCORE_NAMES] 对齐（真源一致）。
     *
     * 渐变是「顶部品牌浅色 → 底部透明」，浅色 / 深色主题各一套起始色；强调色用来染分数
     * （安兔兔红、GeekBench 蓝、3DMark 橙），都照官方 ScoreGridCard 抄的。
     */
    private val SCORE_ITEMS = listOf(
        ScoreItem(
            "aututu", R.drawable.ic_antutu,
            0xFFF44336.toInt(), 0xFFFFEBEE.toInt(), 0xFF360E15.toInt()
        ),
        ScoreItem(
            "geek_bench_single", R.drawable.ic_geekbench,
            0xFF2196F3.toInt(), 0xFFE3F2FD.toInt(), 0xFF0E2436.toInt()
        ),
        ScoreItem(
            "geek_bench_multi", R.drawable.ic_geekbench,
            0xFF2196F3.toInt(), 0xFFE3F2FD.toInt(), 0xFF0E2436.toInt()
        ),
        ScoreItem(
            "3d_mark", R.drawable.ic_3dmark,
            0xFFFF9800.toInt(), 0xFFFFF3E0.toInt(), 0xFF36250E.toInt()
        )
    )

    private data class ScoreItem(
        /** extraData 里的字段前缀，如 `aututu` → `aututu_score_avg` */
        val prefix: String,
        val icon: Int,
        /** 分数文字色（官方每个牌子一个强调色） */
        val accent: Int,
        /** 浅色主题的渐变起始色（顶部），往下渐隐到透明 */
        val lightTop: Int,
        /** 深色主题的渐变起始色 */
        val darkTop: Int
    )

    /** 卡片正文可能是 HTML（feed 的 message 就带 `<a>`），排成纯文本，别把标签显示出来 */
    private fun plainText(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().takeIf { text -> text.isNotBlank() } }

    /** 统计行名称用的灰色（跟 XML 里的 darker_gray 一个观感） */
    private val GRAY = 0xFF888888.toInt()

    /** 骨架内边距，跟 item_home_generic_card.xml 里 content 的 12dp 对齐 */
    private val CARD_PADDING = 12

    /** 官方单独画的置顶内容卡模板名（官方对应 NodeTopContentViewHolder） */
    private const val TOP_CONTENT = "topContent"

    /** 照官方重画的卡片塞进 content 时打的标记，供复用前清理（见 render 开头） */
    private const val OFFICIAL_TAG = "officialCard"

    /**
     * 按字段链取卡片自己的字段，`"description|subTitle|message"` 取第一个非空。
     * title/pic 这些声明成非空但运行时可能是 null，所以先当 Any? 判，别直接调方法。
     */
    private fun pickCard(data: HomeFeedResponse.Data, chain: List<String>): String? {
        chain.forEach { name ->
            // 先查原始 JSON：规则表里配的字段名不再受限于下面这份硬编码清单，
            // 服务端换个 key（比如卡片标题叫 `goods_title`）只改云端规则表就行。
            data.raw?.jsonString(name)?.let { return it }
            // 兜底：raw 为空（对象经 Parcel 传递过、或代码手工构造）时仍按声明字段取
            val field: Any? = when (name) {
                "title" -> data.title
                "description" -> data.description
                "subTitle" -> data.subTitle
                "message" -> data.message
                "pic" -> data.pic
                "cover" -> data.cover
                "url" -> data.url
                "extraData" -> data.extraData
                else -> null
            }
            (field as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}

/**
 * 通用卡片里的实体列表。
 *
 * 形状（图标+标题+副标题横排 / 居中图标格 / 大图 / 纯文字）优先按 [CardUi] 规则表走，
 * 规则表没规定时按「实体自己有什么」推：有图当图标、动态酷图那类 pic 当大图、都没图当纯文字。
 * 宽度：横滚时定宽（否则一条占满一屏），宫格/竖排时撑满自己那一格。
 *
 * 少数实体自己就带着「照官方重画」的模板名（见 [CardUi.entityLayout]，目前只有
 * `albumExpandCard`）：这些实体的排版跟通用形状差太远，单独一个 viewType 走专属布局，
 * 其余实体照旧。
 */
class GenericCardEntityAdapter(
    private val entities: List<HomeFeedResponse.Entities>,
    private val listener: ItemListener,
    private val horizontal: Boolean,
    private val rule: CardUi.ItemRule
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class EntityViewHolder(val binding: ItemHomeGenericCardEntityBinding) :
        RecyclerView.ViewHolder(binding.root)

    /** 实体级专属布局的 ViewHolder：目前只有酷安集一条 */
    class OfficialEntityViewHolder(val album: ItemHomeGenericCardAlbumExpandBinding) :
        RecyclerView.ViewHolder(album.root)

    override fun getItemViewType(position: Int): Int =
        if (CardUi.entityLayout(entityTemplateOf(entities[position])) != null) VIEW_TYPE_OFFICIAL
        else VIEW_TYPE_DEFAULT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_OFFICIAL) {
            OfficialEntityViewHolder(
                ItemHomeGenericCardAlbumExpandBinding.inflate(inflater, parent, false)
            )
        } else {
            EntityViewHolder(
                ItemHomeGenericCardEntityBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun getItemCount() = entities.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entity = entities[position]
        if (holder is OfficialEntityViewHolder) {
            bindAlbumExpandEntity(holder.album, entity)
            return
        }
        val binding = (holder as EntityViewHolder).binding
        val shape = shapeOf(entity)
        val row = shape == CardUi.SHAPE_ROW
        val cover = shape == CardUi.SHAPE_COVER
        val image = pickEntity(entity, rule.iconChain)

        // 排列：row 是「图标左 + 文字右」横排（置顶引导那类），其余竖排居中
        binding.container.orientation = if (row) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        binding.container.gravity = if (row) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL

        binding.container.layoutParams = binding.container.layoutParams.apply {
            width = if (horizontal) {
                (if (cover) WIDE_ITEM_WIDTH else ICON_ITEM_WIDTH).dp
            } else {
                ViewGroup.LayoutParams.MATCH_PARENT
            }
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        binding.icon.isVisible = image != null
        if (image != null) {
            ImageUtil.showIMG(binding.icon, image)
            binding.icon.layoutParams = binding.icon.layoutParams.apply {
                width = when {
                    cover && horizontal -> WIDE_ITEM_WIDTH.dp
                    cover -> ViewGroup.LayoutParams.MATCH_PARENT
                    row -> ROW_ICON_SIZE.dp
                    else -> ICON_SIZE.dp
                }
                height = when {
                    !cover -> (if (row) ROW_ICON_SIZE else ICON_SIZE).dp
                    else -> WIDE_HEIGHT.dp
                }
            }
        }

        val title = pickEntity(entity, rule.titleChain)
        binding.title.isVisible = !title.isNullOrEmpty()
        binding.title.text = title
        binding.title.gravity = if (row) Gravity.START else Gravity.CENTER

        val desc = pickEntity(entity, rule.subtitleChain)?.let { plainText(it) }
        binding.desc.isVisible = !desc.isNullOrEmpty()
        binding.desc.text = desc
        binding.desc.gravity = if (row) Gravity.START else Gravity.CENTER
        // row 形状把副标题挪到标题上面（「来点评」这类是标签，官方就排标题上方）
        binding.container.removeView(binding.desc)
        binding.container.addView(binding.desc, if (row) 1 else 2)

        // 点击：有 url 走链接分发，apk 实体没有 url 时进应用详情
        val url: String? = entity.url
        val apkId: String? = if (entity.entityType == "apk") entity.id else null
        val hasAction = !url.isNullOrEmpty() || !apkId.isNullOrEmpty()
        binding.root.isClickable = hasAction
        binding.root.isFocusable = hasAction
        binding.root.setOnClickListener(
            when {
                !url.isNullOrEmpty() -> View.OnClickListener { view ->
                    listener.onOpenLink(view, url, title)
                }

                !apkId.isNullOrEmpty() -> View.OnClickListener { view ->
                    listener.onViewApk(view, apkId)
                }

                else -> null
            }
        )
    }

    /**
     * 大图打底的酷安集条目（实体模板 `albumExpandCard`），照官方 item_album_expand_card.xml：
     * 整张封面（centerCrop）+ 内边距 16dp 的白字内容（18sp 加粗标题两行、12sp 说明、底下一行
     * 18dp 头像 + 用户名 + 一串 24dp 应用图标）。实测这条实体就是一条完整 album
     * （title / description / logo / username / userAvatar / apkRows / apkRowsMoreCount）。
     *
     * 说明行取 `description`（实测 sample 里跟 `intro` 同值），没有就退成「N 个应用」。
     */
    private fun bindAlbumExpandEntity(
        card: ItemHomeGenericCardAlbumExpandBinding,
        entity: HomeFeedResponse.Entities
    ) {
        val context = card.root.context
        ImageUtil.showIMG(card.albumBg, pickEntity(entity, listOf("logo", "icon", "pic", "bg")))
        card.albumTitle.text = pickEntity(entity, CardUi.chain(null, CardUi.DEFAULT_ITEM_TITLE))
        val info = pickEntity(entity, listOf("description", "intro"))
            ?: pickEntity(entity, listOf("apknum"))?.let { "$it 个应用" }
        card.albumInfo.isVisible = !info.isNullOrEmpty()
        card.albumInfo.text = info

        val user = pickEntity(entity, listOf("username"))
        val avatar = pickEntity(entity, listOf("userAvatar"))
        card.albumAvatar.isVisible = !avatar.isNullOrEmpty()
        if (!avatar.isNullOrEmpty()) ImageUtil.showIMG(card.albumAvatar, avatar)
        card.albumUser.isVisible = !user.isNullOrEmpty()
        card.albumUser.text = user

        // 整条可点（ViewHolder 会复用，没 url 时必须把上一轮的点击清掉，否则点了跳上一集的链接）
        val url: String? = entity.url
        val clickable = !url.isNullOrEmpty()
        card.root.isClickable = clickable
        card.root.isFocusable = clickable
        card.root.setOnClickListener(
            if (clickable) View.OnClickListener { view ->
                listener.onOpenLink(view, url, card.albumTitle.text?.toString())
            } else null
        )

        // 应用图标取原始 JSON 的 apkRows[].pic（模型里没有这个数组）
        card.albumIconList.removeViews(ICON_LIST_FIXED_CHILDREN, card.albumIconList.childCount)
        val icons = entity.raw?.get("apkRows")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { row ->
                row.takeIf { it.isJsonObject }?.asJsonObject?.get("pic")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
            }.orEmpty()
        val showIcons = !avatar.isNullOrEmpty() || !user.isNullOrEmpty() || icons.isNotEmpty()
        card.albumIconList.isVisible = showIcons
        if (!showIcons) return

        // 官方那串图标白底 + 1dp 内边距、互相叠 7dp（第一个跟左边留 8dp）
        icons.take(4).forEachIndexed { index, pic ->
            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                    marginStart = if (index == 0) 8.dp else -7.dp
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.WHITE)
                setPadding(1.dp, 1.dp, 1.dp, 1.dp)
            }
            ImageUtil.showIMG(icon, pic)
            card.albumIconList.addView(icon)
        }

        // 第 5 个圆形槽位：「还有多少个」（官方 more_count_view_5，这里带上 `+` 更好读）
        val more = entity.raw?.get("apkRowsMoreCount")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (icons.isNotEmpty() && more > 0) {
            card.albumIconList.addView(TextView(context).apply {
                text = "+$more"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(0xFF333333.toInt())
                background = ContextCompat.getDrawable(context, R.drawable.album_more_circle)
                layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply { marginStart = -7.dp }
            })
        }
    }

    /** 规则表定了形状就用它，否则按实体内容推（有图当图标、动态图当大图、没图当文字） */
    private fun shapeOf(entity: HomeFeedResponse.Entities): String {
        if (rule.shape != CardUi.SHAPE_AUTO) return rule.shape
        val image = pickEntity(entity, rule.iconChain) ?: return CardUi.SHAPE_TEXT
        val wide = entity.logo.isNullOrEmpty() &&
                entity.entityType.orEmpty() in WIDE_IMAGE_TYPES
        return if (wide && image.isNotEmpty()) CardUi.SHAPE_COVER else CardUi.SHAPE_TILE
    }

    private fun plainText(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().takeIf { text -> text.isNotBlank() } }

    private companion object {
        /** 通用实体排版 */
        const val VIEW_TYPE_DEFAULT = 0

        /** 走专属布局的实体（见 [CardUi.entityLayout]） */
        const val VIEW_TYPE_OFFICIAL = 1

        /** 专属布局里图标区自带的固定子 view 数（头像、用户名、占位 Space），追加图标前保留 */
        const val ICON_LIST_FIXED_CHILDREN = 3

        /** 图标尺寸（方形，宫格里也放得下）；横排时略小一点 */
        const val ICON_SIZE = 56
        const val ROW_ICON_SIZE = 44

        /** 横滚时一个条目占多宽 */
        const val ICON_ITEM_WIDTH = 76
        const val WIDE_ITEM_WIDTH = 130

        /** 大图高度 */
        const val WIDE_HEIGHT = 80

        /** 这些实体的 pic 是内容图（该放大），其余实体的图一律当图标 */
        val WIDE_IMAGE_TYPES = setOf(
            "feed", "feedCover", "image", "imageScale", "pic", "picture",
            "coolPicture", "picCategory"
        )
    }
}

/**
 * 实体自己的模板名（实体级专属布局靠它分派）。
 *
 * `entityTemplate` 只在 [HomeFeedResponse.Data] 上声明了字段（卡片级），实体级没有这个属性，
 * 只能从实体留的原始 JSON 里取 —— 别照 AppAdapter 里 `data.entityTemplate` 那样直接点出来，
 * 那是在卡片上取的（编译能过的是 Data，实体会直接报 Unresolved reference）。
 */
private fun entityTemplateOf(entity: HomeFeedResponse.Entities): String? =
    entity.raw?.jsonString("entityTemplate")

/**
 * 按字段链取实体的字段，`"entityTypeName|description|message"` 取第一个非空。
 * title/pic/url 声明成非空但 Gson 不走构造器、运行时可能是 null，所以先当 Any? 再判。
 */
private fun pickEntity(entity: HomeFeedResponse.Entities, chain: List<String>): String? {
    chain.forEach { name ->
        // 先查原始 JSON。实体侧字段名变数最大 —— 热搜的热度在 `sub_title`、
        // 京东商品的图在 `goods_pic`、价格在 `goods_promo_price`，声明式字段表永远追不上，
        // 但规则表里配什么名字这里就能取到什么。
        entity.raw?.jsonString(name)?.let { return it }
        val field: Any? = when (name) {
            "title" -> entity.title
            "logo" -> entity.logo
            "pic" -> entity.pic
            "icon" -> entity.icon
            "url" -> entity.url
            "description" -> entity.description
            "message" -> entity.message
            "subTitle" -> entity.subTitle
            "entityTypeName" -> entity.entityTypeName
            else -> null
        }
        (field as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

/**
 * 从原始 JSON 里取一个文本字段，只认标量（字符串/数字/布尔）。
 *
 * 对象和数组不取：卡片上要显示的是文本，不是一段 JSON。`extraData` 那类
 * 「内容其实是 JSON」的字段走各自的声明分支处理。
 */
private fun JsonObject.jsonString(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

/**
 * 实体有没有可显示的内容。全空实体直接丢掉，免得卡片里留一个占位的空框。
 *
 * `title` / `pic` / `url` 虽然声明成非空，但 Gson 不走构造器（本项目踩过这个坑），
 * 运行时可能是 null，所以这里全部按可空判。
 */
private fun HomeFeedResponse.Entities.hasContent(): Boolean =
    !title.isNullOrEmpty() || !logo.isNullOrEmpty() || !pic.isNullOrEmpty() ||
            !message.isNullOrEmpty() || !description.isNullOrEmpty()
