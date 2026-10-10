package com.example.c001apk.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.util.FeedExtra
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.dp
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * 动态卡里的附加信息块：**点评评分 / 二手商品 / 商品清单**。
 *
 * 三块共用一个槽位（放在媒体区之下、热评之上），谁有数据谁显示；都没有就整块隐藏
 * （官方是三个独立槽位，但实测这三类在数据上基本互斥 —— 一条动态要么是点评、要么是二手、
 * 要么挂商品清单，拆三个槽位只会让卡片布局多三份空占位）。
 *
 * 槽位取舍与字段口径见 [FeedExtra]。
 */
class FeedExtraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 二手 / 商品清单要给外面的卡片让路（打开链接 / 打开闲鱼），自己无法处理跳转 */
    var onOpen: ((url: String, title: String) -> Unit)? = null

    private val ratingHeader = TextView(context)
    private val ratingItems = LinearLayout(context)
    private val ratingBlock = LinearLayout(context)

    private val ershouLogo = ShapeableImageView(context)
    private val ershouTitle = TextView(context)
    private val ershouPrice = TextView(context)
    private val ershouMeta = TextView(context)
    private val ershouSource = TextView(context)
    private val ershouBlock = LinearLayout(context)

    private val goodsPic = ShapeableImageView(context)
    private val goodsTitle = TextView(context)
    private val goodsHint = TextView(context)
    private val goodsBlock = LinearLayout(context)

    private var ershouUrl = ""
    private var goodsUrl = ""

    init {
        orientation = VERTICAL
        visibility = View.GONE
        buildRating()
        buildErshou()
        buildGoods()
    }

    fun bind(data: HomeFeedResponse.Data?) = bind(data?.let { FeedExtra.from(it) })

    fun bind(extra: FeedExtra?) {
        if (extra == null) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        bindRating(extra.rating)
        bindErshou(extra.ershou)
        bindGoods(extra.goods)
    }

    private fun bindRating(rating: FeedExtra.Rating?) {
        ratingBlock.isVisible = rating != null
        if (rating == null) return
        val parts = ArrayList<String>()
        if (rating.star.isNotEmpty()) parts += "★ ${rating.star}"
        if (rating.score.isNotEmpty()) parts += "${rating.score} 分"
        ratingHeader.text = if (parts.isEmpty()) "点评" else parts.joinToString("  ")
        ratingItems.removeAllViews()
        for (item in rating.items) {
            ratingItems.addView(chip("${item.name} ${item.score}"))
        }
    }

    private fun bindErshou(ershou: FeedExtra.Ershou?) {
        ershouBlock.isVisible = ershou != null
        if (ershou == null) return
        ershouUrl = ershou.linkUrl
        ershouTitle.text = if (ershou.config.isEmpty()) {
            ershou.title
        } else {
            "${ershou.title} · ${ershou.config}"
        }
        ershouPrice.text = if (ershou.price.isEmpty()) "面议" else "¥${ershou.price}"
        ershouMeta.text = listOf(ershou.area, ershou.storeType)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        ershouSource.text = ershou.linkSource
        ershouSource.isVisible = ershou.linkSource.isNotEmpty()
        if (ershou.logo.isEmpty()) ershouLogo.setImageDrawable(null)
        else ImageUtil.showIMG(ershouLogo, ershou.logo)
        ershouBlock.setOnClickListener {
            if (ershouUrl.isNotEmpty()) onOpen?.invoke(ershouUrl, ershou.title)
        }
    }

    private fun bindGoods(goods: FeedExtra.Goods?) {
        goodsBlock.isVisible = goods != null
        if (goods == null) return
        goodsUrl = goods.url
        goodsTitle.text = goods.title
        if (goods.pic.isEmpty()) goodsPic.setImageDrawable(null)
        else ImageUtil.showIMG(goodsPic, goods.pic)
        goodsBlock.setOnClickListener {
            if (goodsUrl.isNotEmpty()) onOpen?.invoke(goodsUrl, goods.title)
        }
    }

    /** 评分：`★ 5  10 分` 一行 + 各维度分值小标签 */
    private fun buildRating() {
        ratingBlock.orientation = VERTICAL
        ratingBlock.background = roundedBackground(12, surfaceColor())
        ratingBlock.setPadding(10.dp, 8.dp, 10.dp, 8.dp)

        ratingHeader.apply {
            textSize = 13f
            setTextColor(MaterialColors.getColor(this, TEXT_COLOR_ATTR, 0))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        ratingBlock.addView(ratingHeader)

        ratingItems.apply {
            orientation = HORIZONTAL
        }
        ratingBlock.addView(
            ratingItems,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 6.dp
            }
        )
        addBlock(ratingBlock)
    }

    private fun buildErshou() {
        ershouBlock.orientation = HORIZONTAL
        ershouBlock.background = roundedBackground(12, surfaceColor())
        ershouBlock.setPadding(10.dp, 10.dp, 10.dp, 10.dp)
        ershouBlock.isClickable = true
        ershouBlock.isFocusable = true
        ershouBlock.gravity = Gravity.CENTER_VERTICAL

        thumb(ershouLogo)
        ershouBlock.addView(ershouLogo, LayoutParams(56.dp, 56.dp))

        val column = LinearLayout(context).apply { orientation = VERTICAL }
        ershouTitle.apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(MaterialColors.getColor(this, TEXT_COLOR_ATTR, 0))
        }
        ershouPrice.apply {
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, PRICE_COLOR_ATTR, 0))
        }
        ershouMeta.apply {
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, HINT_COLOR_ATTR, 0))
        }
        column.addView(ershouTitle)
        column.addView(
            ershouPrice,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 2.dp
            }
        )
        column.addView(
            ershouMeta,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 2.dp
            }
        )
        ershouBlock.addView(
            column,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10.dp
                rightMargin = 10.dp
            }
        )

        ershouSource.apply {
            textSize = 12f
            setTextColor(MaterialColors.getColor(this, HINT_COLOR_ATTR, 0))
        }
        ershouBlock.addView(
            ershouSource,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        addBlock(ershouBlock)
    }

    /** 商品清单单卡：缩略图 + 标题 + 「查看清单」 */
    private fun buildGoods() {
        goodsBlock.orientation = HORIZONTAL
        goodsBlock.background = roundedBackground(12, surfaceColor())
        goodsBlock.setPadding(10.dp, 10.dp, 10.dp, 10.dp)
        goodsBlock.isClickable = true
        goodsBlock.isFocusable = true
        goodsBlock.gravity = Gravity.CENTER_VERTICAL

        thumb(goodsPic)
        goodsBlock.addView(goodsPic, LayoutParams(56.dp, 56.dp))

        goodsTitle.apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(MaterialColors.getColor(this, TEXT_COLOR_ATTR, 0))
        }
        goodsBlock.addView(
            goodsTitle,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10.dp
                rightMargin = 10.dp
            }
        )

        goodsHint.apply {
            textSize = 12f
            text = "查看清单"
            setTextColor(MaterialColors.getColor(this, HINT_COLOR_ATTR, 0))
        }
        goodsBlock.addView(goodsHint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addBlock(goodsBlock)
    }

    /**
     * 间距全部走「块自己的上边距」，根布局不留下边距 —— 整块隐藏时高度必须正好为 0，
     * 否则下面热评的位置会跟着动（这个槽是 GONE 态最常见的那一个）。
     */
    private fun addBlock(block: View) {
        addView(
            block,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (childCount == 0) 10.dp else 6.dp
            }
        )
    }

    private fun chip(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 11f
        setTextColor(MaterialColors.getColor(this, HINT_COLOR_ATTR, 0))
        background = roundedBackground(6, surfaceColor())
        setPadding(6.dp, 2.dp, 6.dp, 2.dp)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            rightMargin = 6.dp
        }
    }

    private fun thumb(view: ImageView) {
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        if (view is ShapeableImageView) {
            view.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, 8.dp.toFloat())
                .build()
        }
    }

    private fun roundedBackground(radiusDp: Int, color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radiusDp.dp.toFloat()
            setColor(color)
        }

    private fun surfaceColor(): Int =
        MaterialColors.getColor(this, BACKGROUND_COLOR_ATTR, 0)

    private companion object {
        /**
         * 块内文字：正文 / 次要说明 / 价格。
         * 注意不能写成 `const val`：这些常量取自**依赖库的 R**（material / appcompat），
         * 库模块的 R 字段不是 Kotlin 编译期常量，写 const 会报
         * "Const 'val' initializer should be a constant value"（CI 上实测过）。
         */
        val TEXT_COLOR_ATTR = com.google.android.material.R.attr.colorOnSurface
        val HINT_COLOR_ATTR = com.google.android.material.R.attr.colorOnSurfaceVariant
        /** colorPrimary 在 appcompat 而不在 material，写 material.R.attr 会 Unresolved reference */
        val PRICE_COLOR_ATTR = androidx.appcompat.R.attr.colorPrimary
        /**
         * 块底色：与卡片（colorSurfaceContainer）拉开一档，才能看出是卡片里的独立信息块。
         * 用 surface 而不是 surfaceVariant —— 卡片里的热评 / 转发块（`round_corners_12_win`）
         * 用的就是这个档，跟它们保持一致；variant 会把块整片压灰，和首页割裂。
         */
        val BACKGROUND_COLOR_ATTR = com.google.android.material.R.attr.colorSurface
    }
}
