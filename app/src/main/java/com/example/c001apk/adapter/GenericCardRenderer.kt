package com.example.c001apk.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemHomeGenericCardBinding
import com.example.c001apk.databinding.ItemHomeGenericCardEntityBinding
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.dp
import org.jsoup.Jsoup

/**
 * 通用卡片渲染：把服务端下发的任意卡片画出来，不再为每个模板写一个 ViewHolder。
 *
 * `_rev/card_tpl_scan.py` 实测服务端会下发 39 种顶层卡片模板，且随时会冒新的。
 * 这些卡片摊开看只有两种东西：
 *   1. 卡片自身的字段（title / description / pic / url）；
 *   2. 一个 `entities` 数组，每条实体的内容也跑不出几类（图标、应用/商品、动态、图片、纯文字）。
 * 所以渲染 = 「卡片四块 + 实体列表」，排列由 [GenericCardLayout] 按「模板名 + 实体数量」决定，
 * 每条实体的样式由 [GenericCardEntityAdapter] 按实体内容决定（有 logo 当图标、只有 pic 当大图、
 * 都没有就当纯文字）。新模板进来时这两处都不需要改代码。
 *
 * 注意：`HomeFeedResponse.Entities` 的 `url` / `pic` / `title` 都声明成非空，但 Gson 不走构造器，
 * 运行时完全可能是 null（本项目踩过这个坑），所以这里全程按可空处理，
 * 并且**不碰** `entity.userInfo`——`selectorLink` / `iconTabLink` 这类实体压根不下发它。
 */
object GenericCardRenderer {

    fun render(
        binding: ItemHomeGenericCardBinding,
        data: HomeFeedResponse.Data,
        listener: ItemListener
    ) {
        val entities = data.entities.orEmpty().filter { it.hasContent() }
        val title: String? = data.title
        val summary = data.description?.takeIf { it.isNotBlank() }
            ?: data.subTitle?.takeIf { it.isNotBlank() }
            ?: plainText(data.message)
        // 卡片自带配图：只有卡片里没有实体时才当主图用（imageScaleCard、只有图的 fabCard）
        val hero = data.pic?.takeIf { it.isNotBlank() }?.takeIf { entities.isEmpty() }

        binding.title.isVisible = !title.isNullOrEmpty()
        binding.title.text = title

        binding.summary.isVisible = !summary.isNullOrEmpty()
        binding.summary.text = summary

        binding.hero.isVisible = hero != null
        if (hero != null) ImageUtil.showIMG(binding.hero, hero)

        val spec = GenericCardLayout.resolve(data.entityTemplate, entities.size)
        binding.recyclerView.isVisible = entities.isNotEmpty()
        if (entities.isEmpty()) {
            binding.recyclerView.adapter = null
        } else {
            binding.recyclerView.layoutManager = if (spec.horizontal) {
                LinearLayoutManager(
                    binding.root.context, LinearLayoutManager.HORIZONTAL, false
                )
            } else {
                GridLayoutManager(binding.root.context, spec.span)
            }
            binding.recyclerView.adapter =
                GenericCardEntityAdapter(entities, listener, spec.horizontal)
        }

        // 一个能显示的东西都没有（sponsorArticleNews 只给 extraData 就是这种）：
        // 正式包里直接不占位，别把「暂不支持」这种技术字样甩给用户；debug 频道里留一行提示
        // 标明是哪种模板没渲染出来，便于排障（HTTP_LOG 只在 debug 频道为 true）
        val label = data.entityTemplate?.takeIf { it.isNotBlank() } ?: data.entityType
        val renderable =
            !title.isNullOrEmpty() || !summary.isNullOrEmpty() || entities.isNotEmpty() || hero != null
        val debugTip = BuildConfig.HTTP_LOG && !label.isNullOrEmpty()
        binding.tip.isVisible = debugTip
        if (debugTip) {
            binding.tip.text = binding.root.context.getString(
                if (renderable) R.string.generic_card else R.string.unsupported_card, label
            )
        }
        binding.root.isVisible = renderable || debugTip

        // 整卡可点：实体各自会吃掉自己那份点击，落到这里的只有空白区域
        val url: String? = data.url
        val clickable = !url.isNullOrEmpty()
        binding.root.isClickable = clickable
        binding.root.isFocusable = clickable
        binding.root.setOnClickListener(
            if (clickable) View.OnClickListener { view -> listener.onOpenLink(view, url, title) }
            else null
        )
    }

    /** 卡片正文可能是 HTML（feed 的 message 就带 `<a>`），排成纯文本，别把标签显示出来 */
    private fun plainText(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().takeIf { text -> text.isNotBlank() } }
}

/**
 * 卡片里实体的排列方式。
 *
 * 服务端不告诉我们「这张卡是横滚还是宫格」，所以按模板名查表；表里没有的按实体数量推断
 * （一两条竖排、四五条宫格、再多就横滚），所以没见过的模板也能排得像样。
 */
object GenericCardLayout {

    /** 宫格：每行放几个 */
    private val SPAN_BY_TEMPLATE = mapOf(
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
        "iconLinkGridCard" to 5,
    )

    /** 一条一条竖着排（应用/话题列表型卡片） */
    private val VERTICAL_LIST = setOf(
        "iconListCard", "apkListCard", "listCard", "feedListCard", "productTimelineListCard"
    )

    /** 横向滚动 */
    private val HORIZONTAL_ROW = setOf(
        "apkScrollCard", "apkScrollCardWithBackground", "apkImageScrollCard", "apkImageCard",
        "colorfulScrollCard", "iconLargeScrollCard", "feedScrollCard", "imageScaleCard",
        "iconScrollCard", "imageScrollCard", "imageCarouselCard", "iconMiniScrollCard",
    )

    data class Spec(val horizontal: Boolean, val span: Int)

    fun resolve(template: String?, size: Int): Spec {
        if (size <= 0) return Spec(false, 1)
        if (template != null) {
            if (template in HORIZONTAL_ROW) return Spec(true, 0)
            if (template in VERTICAL_LIST) return Spec(false, 1)
            SPAN_BY_TEMPLATE[template]?.let { return Spec(false, it.coerceAtMost(size)) }
        }
        return when {
            size == 1 -> Spec(false, 1)
            size <= 4 -> Spec(false, size)
            else -> Spec(true, 0)
        }
    }
}

/**
 * 通用卡片里的实体列表。横向滚动时每条定宽（否则一条占满一屏），宫格/竖排时撑满自己的格子。
 *
 * 样式只按「实体自己有什么」分，不看 entityType：
 *   - 有 logo → 图标（56dp 方图，应用/商品/用户都是这个形状）；
 *   - 只有 pic → 大图（动态、酷图那类，宽度撑满格子或定宽 130dp）；
 *   - 都没有 → 纯文字（热榜词条、子标签那种）。
 */
class GenericCardEntityAdapter(
    private val entities: List<HomeFeedResponse.Entities>,
    private val listener: ItemListener,
    private val horizontal: Boolean
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

        val image = entity.logo?.takeIf { it.isNotBlank() } ?: entity.pic?.takeIf { it.isNotBlank() }
        val wide = image != null && entity.logo.isNullOrEmpty() &&
                entity.entityType.orEmpty() in WIDE_IMAGE_TYPES

        // 条目宽度：横滚时定宽（大图条目给宽一点），宫格/竖排时撑满自己那一格。
        // 图本身非「大图」时一律 56dp 方图——宫格里 5 列时一格只有 70dp 出头，
        // 再宽就顶出格子被裁掉了。
        binding.root.layoutParams = binding.root.layoutParams.apply {
            width = if (horizontal) {
                (if (wide) WIDE_ITEM_WIDTH else ICON_ITEM_WIDTH).dp
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
                    !wide -> ICON_SIZE.dp
                    horizontal -> WIDE_ITEM_WIDTH.dp
                    else -> ViewGroup.LayoutParams.MATCH_PARENT
                }
                height = (if (wide) WIDE_HEIGHT else ICON_SIZE).dp
            }
        }

        val title: String? = entity.title
        binding.title.isVisible = !title.isNullOrEmpty()
        binding.title.text = title

        val desc = entity.description?.takeIf { it.isNotBlank() }
            ?: plainText(entity.message)
        binding.desc.isVisible = !desc.isNullOrEmpty()
        binding.desc.text = desc

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

    private fun plainText(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().takeIf { text -> text.isNotBlank() } }

    private companion object {
        /** 图标尺寸（方形，宫格里也放得下） */
        const val ICON_SIZE = 56

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
 * 实体有没有可显示的内容。全空实体直接丢掉，免得卡片里留一个占位的空框。
 *
 * `title` / `pic` / `url` 虽然声明成非空，但 Gson 不走构造器（本项目踩过这个坑），
 * 运行时可能是 null，所以这里全部按可空判。
 */
private fun HomeFeedResponse.Entities.hasContent(): Boolean =
    !title.isNullOrEmpty() || !logo.isNullOrEmpty() || !pic.isNullOrEmpty() ||
            !message.isNullOrEmpty() || !description.isNullOrEmpty()
