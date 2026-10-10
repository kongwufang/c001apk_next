package com.example.c001apk.adapter

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemHomeGenericCardBinding
import com.example.c001apk.databinding.ItemHomeGenericCardEntityBinding
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.util.CardUi
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.dp
import org.jsoup.Jsoup

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
        val spec = CardUi.layout(template, entities.size)

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

    /** 卡片正文可能是 HTML（feed 的 message 就带 `<a>`），排成纯文本，别把标签显示出来 */
    private fun plainText(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().takeIf { text -> text.isNotBlank() } }

    /** 统计行名称用的灰色（跟 XML 里的 darker_gray 一个观感） */
    private val GRAY = 0xFF888888.toInt()

    /**
     * 按字段链取卡片自己的字段，`"description|subTitle|message"` 取第一个非空。
     * title/pic 这些声明成非空但运行时可能是 null，所以先当 Any? 判，别直接调方法。
     */
    private fun pickCard(data: HomeFeedResponse.Data, chain: List<String>): String? {
        chain.forEach { name ->
            val raw: Any? = when (name) {
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
            (raw as? String)?.takeIf { it.isNotBlank() }?.let { return it }
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
 */
class GenericCardEntityAdapter(
    private val entities: List<HomeFeedResponse.Entities>,
    private val listener: ItemListener,
    private val horizontal: Boolean,
    private val rule: CardUi.ItemRule
) : RecyclerView.Adapter<GenericCardEntityAdapter.EntityViewHolder>() {

    class EntityViewHolder(val binding: ItemHomeGenericCardEntityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder =
        EntityViewHolder(
            ItemHomeGenericCardEntityBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun getItemCount() = entities.size

    override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
        val entity = entities[position]
        val binding = holder.binding
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
 * 按字段链取实体的字段，`"entityTypeName|description|message"` 取第一个非空。
 * title/pic/url 声明成非空但 Gson 不走构造器、运行时可能是 null，所以先当 Any? 再判。
 */
private fun pickEntity(entity: HomeFeedResponse.Entities, chain: List<String>): String? {
    chain.forEach { name ->
        val raw: Any? = when (name) {
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
        (raw as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

/**
 * 实体有没有可显示的内容。全空实体直接丢掉，免得卡片里留一个占位的空框。
 *
 * `title` / `pic` / `url` 虽然声明成非空，但 Gson 不走构造器（本项目踩过这个坑），
 * 运行时可能是 null，所以这里全部按可空判。
 */
private fun HomeFeedResponse.Entities.hasContent(): Boolean =
    !title.isNullOrEmpty() || !logo.isNullOrEmpty() || !pic.isNullOrEmpty() ||
            !message.isNullOrEmpty() || !description.isNullOrEmpty()
