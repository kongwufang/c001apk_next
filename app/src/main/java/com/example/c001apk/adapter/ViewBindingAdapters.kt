package com.example.c001apk.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.Html
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import com.example.c001apk.R
import com.example.c001apk.constant.Constants
import com.example.c001apk.logic.model.FeedArticleContentBean
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.UserProfileResponse
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.NetWorkUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.SpannableStringBuilderUtil
import com.example.c001apk.util.VerifyBadge
import com.example.c001apk.util.dp
import com.example.c001apk.view.LinkTextView
import com.example.c001apk.view.ninegridimageview.NineGridImageView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView

@BindingAdapter("setExtraPic")
fun setExtraPic(imageView: ImageView, extraPic: String?) {
    if (extraPic.isNullOrEmpty())
        imageView.apply {
            setBackgroundColor(
                MaterialColors.getColor(
                    imageView.context,
                    androidx.appcompat.R.attr.colorPrimary,
                    0
                )
            )
            val link = imageView.context.getDrawable(R.drawable.ic_link)
            link?.setTint(
                MaterialColors.getColor(
                    imageView.context,
                    com.google.android.material.R.attr.colorOnPrimary,
                    0
                )
            )
            setImageDrawable(link)
        }
    else {
        ImageUtil.showIMG(imageView, extraPic)
    }
}

@BindingAdapter("setFollowText")
fun setFollowText(textView: TextView, followAuthor: Int) {
    // 列表项不下发 followAuthor，详情回来前是"未知"：按钮先隐身，让 followLoading 的转圈顶上
    val unknown = followAuthor == Constants.FOLLOW_AUTHOR_UNKNOWN
    with(PrefManager.isLogin) {
        textView.isVisible = this && !unknown
        if (this && !unknown) {
            when (followAuthor) {
                0 -> {
                    textView.text = "关注"
                    textView.setTextColor(
                        MaterialColors.getColor(
                            textView.context,
                            androidx.appcompat.R.attr.colorPrimary,
                            0
                        )
                    )
                }

                1 -> {
                    textView.text = "取消关注"
                    textView.setTextColor(textView.context.getColor(android.R.color.darker_gray))
                }

                else -> {}
            }
        }
    }

}

@BindingAdapter("followLoading")
fun followLoading(view: View, followAuthor: Int) {
    view.isVisible = followAuthor == Constants.FOLLOW_AUTHOR_UNKNOWN
}

/**
 * 头像右下角的认证角标，没有认证就 GONE。
 *
 * 收拆开的三个字段而不是整个 userInfo：详情页顶栏那一行只有 username / avatar，
 * 手里没有 userInfo 对象可传。
 */
@BindingAdapter(value = ["verifyUid", "verifyIcon", "verifyStatus"], requireAll = false)
fun setVerifyBadge(imageView: ImageView, uid: String?, icon: String?, status: Int?) =
    VerifyBadge.applyTo(imageView, uid, icon, status)

/**
 * 用户卡片（搜索用户 / 关注粉丝 / 黑名单共用 item_search_user）的头像角标。
 *
 * 那三处的头像是三个来源，优先级得跟 UserViewHolder 的分支顺序一致：两个 userInfo 都在时
 * 认 userInfo，只有 fUserInfo 时认它，最后才是 Data 自己的扁平字段 —— 用户搜索返回的实体
 * 就是这个形状，verify_* 跟 uid / username 平级。
 */
@BindingAdapter("verifyUserData")
fun setVerifyUserData(imageView: ImageView, data: HomeFeedResponse.Data?) {
    val user = when {
        data?.userInfo != null && data.fUserInfo != null -> data.userInfo
        data?.fUserInfo != null -> data.fUserInfo
        else -> null
    }
    VerifyBadge.applyTo(
        imageView,
        user?.uid ?: data?.uid,
        user?.verifyIcon ?: data?.verifyIcon,
        user?.verifyStatus ?: data?.verifyStatus,
    )
}

/**
 * 个人主页的认证行：角标 + 文字，认证文字只在这里出现（列表 / 详情页只挂头像角标）。
 *
 * 绑整行而不是绑文字：角标要和头像右下角那枚同色，文字又得能顶成本地特例的文案，
 * 两者都吃同一份判定，写在一处省得各判一遍。
 */
@BindingAdapter("verifyTitle")
fun setVerifyTitle(row: View, data: UserProfileResponse.Data?) {
    val title = VerifyBadge.title(data?.uid, data?.verifyTitle)
    row.isVisible = !title.isNullOrEmpty()
    if (title.isNullOrEmpty()) return
    row.findViewById<TextView>(R.id.verifyTitleText)?.text = title
    row.findViewById<ImageView>(R.id.verifyTitleBadge)?.backgroundTintList =
        ColorStateList.valueOf(VerifyBadge.badgeColor(row.context, data?.uid, data?.verifyIcon))
}

@BindingAdapter("setArticleImage")
fun setArticleImage(
    imageView: NineGridImageView,
    setArticleImage: FeedArticleContentBean.Data,
) {
    setNineGridImage(imageView, setArticleImage.url, true)
}

/**
 * 图文详情的封面。列表项里只有一个 messageCover 裸地址，作为整页头图要顶在作者行下面，
 * 所以按原图取（清晰优先），排版宽高仍靠 url 里的 `@WxH` 提示算。
 */
@BindingAdapter("setFeedCover")
fun setFeedCover(imageView: NineGridImageView, cover: String?) {
    setNineGridImage(imageView, cover, false)
}

private fun setNineGridImage(imageView: NineGridImageView, url: String?, thumbnail: Boolean) {
    if (url.isNullOrEmpty()) return
    val urlList = ArrayList<String>()
    urlList.add(if (thumbnail) "$url.s.jpg" else url)
    val imageLp = ImageUtil.getImageLp(url)
    imageView.imgWidth = imageLp.first
    imageView.imgHeight = imageLp.second
    imageView.isCompress = true
    imageView.setUrlList(urlList)
}

@BindingAdapter(value = ["targetRow", "relationRows", "isFeedContent"], requireAll = true)
fun setRows(
    linearLayout: LinearLayout,
    targetRow: HomeFeedResponse.TargetRow?,
    relationRows: ArrayList<HomeFeedResponse.RelationRows>?,
    isFeedContent: Boolean?
) {
    linearLayout.removeAllViews()
    relationRows?.let {
        val dataList = it.toMutableList()
        targetRow?.id?.let {
            dataList.add(
                0,
                HomeFeedResponse.RelationRows(
                    targetRow.id,
                    targetRow.logo,
                    targetRow.title,
                    targetRow.url,
                    targetRow.targetType.toString()
                )
            )
        }
        val context = linearLayout.context
        dataList.forEachIndexed { index, relationRows ->
            val view = LayoutInflater.from(context).inflate(
                R.layout.item_home_icon_mini_scroll_card_item, linearLayout, false
            )
            if (isFeedContent == true)
                view.background = context.getDrawable(R.drawable.round_corners_20)
            if (index != 0) {
                view.layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(5.dp, 0, 0, 0)
                }
            }
            view.findViewById<TextView>(R.id.title).text = relationRows.title
            ImageUtil.showIMG(
                view.findViewById<ShapeableImageView>(R.id.iconMiniScrollCard), relationRows.logo
            )

            view.setOnClickListener {
                NetWorkUtil.openLinkDyh(
                    relationRows.entityType,
                    context,
                    relationRows.url,
                    relationRows.id,
                    relationRows.title
                )
            }
            linearLayout.addView(view)
        }

    }
}

@BindingAdapter(value = ["pic", "picArr", "feedType"], requireAll = true)
fun setGridView(
    imageView: NineGridImageView,
    pic: String?,
    picArr: List<String>?,
    feedType: String?
) {
    if (!picArr.isNullOrEmpty()) {
        imageView.isVisible = true
        if (picArr.size == 1 || feedType in listOf("feedArticle", "trade")) {
            val imageLp = ImageUtil.getImageLp(pic ?: picArr[0])
            imageView.imgWidth = imageLp.first
            imageView.imgHeight = imageLp.second
        }
        imageView.apply {
            val urlList: MutableList<String> = ArrayList()
            if (feedType in listOf("feedArticle", "trade") && imgWidth > imgHeight)
                if (!pic.isNullOrEmpty()) urlList.add("$pic.s.jpg")
                else urlList.add("${picArr[0]}.s.jpg")
            else
                urlList.addAll(picArr.map { "$it.s.jpg" })
            setUrlList(urlList)
        }
    } else {
        imageView.isVisible = false
    }
}

@BindingAdapter("setLike")
fun setLike(textView: TextView, isLike: Int?) {
    isLike?.let {
        val color = if (it == 1)
            MaterialColors.getColor(
                textView.context,
                androidx.appcompat.R.attr.colorPrimary,
                0
            )
        else textView.context.getColor(android.R.color.darker_gray)
        val size = textView.textSize.toInt()
        val drawableLike = textView.context.getDrawable(R.drawable.ic_like).also { drawable ->
            drawable?.setBounds(0, 0, size, size)
            drawable?.setTint(color)
        }
        textView.setCompoundDrawables(drawableLike, null, null, null)
        textView.setTextColor(color)
    }
}

/**
 * 收藏状态：已收藏把星标连同数字一起染成主题色，未收藏保持灰
 * （`ic_feed_favorite` 向量自带的 darker_gray tint 会被这里的 setTint 覆盖掉）。
 *
 * 图标由本适配器自己挂——不能再同时写 `app:icon`，两个适配器都会调 setCompoundDrawables，
 * 执行顺序不定，谁后跑谁说了算。
 */
@BindingAdapter("setFavorite")
fun setFavorite(textView: TextView, isFavorite: Boolean?) {
    val color = if (isFavorite == true)
        MaterialColors.getColor(
            textView.context,
            androidx.appcompat.R.attr.colorPrimary,
            0
        )
    else textView.context.getColor(android.R.color.darker_gray)
    val size = textView.textSize.toInt()
    val drawableFavorite = textView.context.getDrawable(R.drawable.ic_feed_favorite)
        .also { drawable ->
            drawable?.setBounds(0, 0, size, size)
            drawable?.setTint(color)
        }
    textView.setCompoundDrawables(drawableFavorite, null, null, null)
    textView.setTextColor(color)
}

@BindingAdapter(
    value = ["customText", "icon", "isHtml", "isRichText"], requireAll = false
)
fun setCustomText(
    textView: TextView,
    customText: String?,
    icon: Drawable?,
    isHtml: Boolean?,
    isRichText: Boolean?
) {

    icon?.let {
        val size = textView.textSize.toInt()
        icon.setBounds(0, 0, size, size)
        textView.setCompoundDrawables(icon, null, null, null)
    }

    textView.text =
        if (isHtml == true && !customText.isNullOrEmpty()) Html.fromHtml(
            customText,
            Html.FROM_HTML_MODE_COMPACT
        ) else if (isRichText == true && !customText.isNullOrEmpty()) {
            textView.movementMethod = LinkTextView.LocalLinkMovementMethod.instance
            SpannableStringBuilderUtil.setText(
                textView.context,
                customText,
                textView.textSize,
                null
            )
        } else customText

}

@BindingAdapter("setHotReply")
fun setHotReply(hotReply: TextView, replyRow: HomeFeedResponse.ReplyRows?) {
    if (replyRow != null) {
        hotReply.isVisible = true
        hotReply.highlightColor = Color.TRANSPARENT
        hotReply.movementMethod = LinkMovementMethodCompat.getInstance()
        hotReply.text = SpannableStringBuilderUtil.setText(
            hotReply.context,
            replyRow.message.toString(),
            hotReply.textSize,
            replyRow.picArr
        )
    } else
        hotReply.isVisible = false
}


@BindingAdapter("setImage")
fun setImage(imageView: ImageView, imageUrl: String?) {
    if (imageUrl.isNullOrEmpty())
        imageView.isVisible = false
    else {
        imageView.isVisible = true
        ImageUtil.showIMG(imageView, imageUrl)
    }
}

@BindingAdapter("setCover")
fun setCover(imageView: ImageView, imageUrl: String?) {
    imageUrl?.let {
        ImageUtil.showIMG(imageView, it, true)
    }
}

/**
 * 聊天气泡头像：只负责加载图片，**不碰 visibility**。
 * 用 setImage 的话它会按「url 是否为空」改可见性，和布局里的
 * `android:visibility="@{isMe ? GONE : VISIBLE}"` 抢同一个属性。
 */
@BindingAdapter("avatarImage")
fun avatarImage(imageView: ImageView, imageUrl: String?) {
    if (imageUrl.isNullOrEmpty())
        imageView.setImageDrawable(null)
    else
        ImageUtil.showIMG(imageView, imageUrl)
}