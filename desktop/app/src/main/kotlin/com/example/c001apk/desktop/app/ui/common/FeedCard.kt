package com.example.c001apk.desktop.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.core.model.HomeEntity
import com.example.c001apk.desktop.core.model.Feed
import com.example.c001apk.desktop.core.text.HtmlText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 动态卡片 —— 首页与详情页共用。
 *
 * 对齐 Android 端 `res/layout/item_home_feed.xml` 实测数值：
 * ```
 * MaterialCardView: cardBackgroundColor=@color/home_card_background_color
 *                   (= colorSurfaceContainer)、elevation=0dp、strokeWidth=0dp
 *                   内容 padding=10dp
 * uname        15sp  singleLine  ellipsize=end
 * from/device  12sp  darker_gray
 * messageTitle 16sp bold  lineSpacingMultiplier=1.3
 * message      16sp  lineSpacingMultiplier=1.2
 *              列表 maxLines=5（详情页 item_feed_content 是 lineSpacingMultiplier=1.5 且不截断）
 * messageMore  「查看更多」13sp bold colorPrimary（截断才显示，默认不占位）
 * 底部动作      2 个：reply + like；左时间、右互动
 * ```
 *
 * @param expandMessage 详情页用：true 表示正文不截断
 */
@Composable
fun FeedCard(
    e: HomeEntity,
    expandMessage: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier.then(
        // Android 端 item_home_feed 的外层 MaterialCardView 是 clickable/focusable 的
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )) {
        Column(Modifier.padding(10.dp)) {
            // ── 用户名 + 时间 ──
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    e.username ?: "—",
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                e.dateline?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(fmtTime(it), fontSize = 12.sp, color = DARKER_GRAY, maxLines = 1)
                }
            }

            // ── 机型 ──
            val dev = e.deviceTitle ?: e.deviceName
            if (!dev.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(dev, fontSize = 12.sp, color = DARKER_GRAY, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // ── 标题（messageTitle）16sp bold ──
            e.messageTitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    HtmlText.toPlainText(it),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (expandMessage) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── 正文 ──
            // message 含行内 HTML（<a class="feed-link-tag" href="/t/…">#话题#</a>）与表情码，
            // 必须解析后渲染，否则会露出标签源码、表情码也不成图。
            val pieces = HtmlText.parse(e.message)
            val inline = emojiInlineContents(pieces)
            var truncated by remember(e.entityId, e.id) { mutableStateOf(false) }
            if (pieces.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = buildRichText(pieces),
                    fontSize = 16.sp,
                    // 详情页行距 1.5、列表 1.2（对齐两处布局的差异）
                    lineHeight = if (expandMessage) 24.sp else 19.sp,
                    maxLines = if (expandMessage) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = inline,
                    onTextLayout = { r -> truncated = r.hasVisualOverflow },
                )
            }
            if (truncated && !expandMessage) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "查看更多",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ── 图片位（Coil 接入前的占位）──
            val pics = e.picArr?.size ?: if (!e.pic.isNullOrBlank()) 1 else 0
            if (pics > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (pics == 1) 120.dp else 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (pics == 1) "[图片]" else "[$pics 张图片]",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 底部：时间（左）+ 评论 / 点赞（右）──
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.dateline?.let { fmtTime(it) } ?: "",
                    fontSize = 12.sp,
                    color = DARKER_GRAY,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text("评论 ${e.replynum ?: e.replynum2 ?: 0}", fontSize = 12.sp, color = DARKER_GRAY)
                Spacer(Modifier.width(10.dp))
                Text("赞 ${e.likenum ?: 0}", fontSize = 12.sp, color = DARKER_GRAY)
            }
        }
    }
}

/**
 * 评论条目 —— 对齐 `res/layout/item_feed_content_reply_item.xml`：
 * ```
 * avatar 30×30dp 圆形；uname 无显式字号（主题默认）
 * message 15sp lineSpacingMultiplier=1.2
 * 底部：pubDate（左，darker_gray）；reply 与 like 右对齐，baseline 对齐 pubDate
 * 横屏时 root 会套 text_card_bg（12dp 圆角）+ padding 10dp
 * ```
 */
@Composable
fun ReplyCard(f: Feed, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(30.dp)
                        .height(30.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (f.username ?: "?").take(1),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    f.username ?: "—",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                f.deviceTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = DARKER_GRAY, maxLines = 1)
                }
            }

            val pieces = HtmlText.parse(f.message)
            val inline = emojiInlineContents(pieces)
            if (pieces.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = buildRichText(pieces),
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    inlineContent = inline,
                )
            }

            val pics = f.picArr?.size ?: 0
            if (pics > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "[$pics 张图片]",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.dateline?.let { fmtTime(it) } ?: "",
                    fontSize = 12.sp,
                    color = DARKER_GRAY,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text("回复 ${f.replynum ?: 0}", fontSize = 12.sp, color = DARKER_GRAY)
                Spacer(Modifier.width(10.dp))
                Text("赞 ${f.likenum ?: 0}", fontSize = 12.sp, color = DARKER_GRAY)
            }
        }
    }
}

/** 统一的卡片外观：`colorSurfaceContainer` 底 + 12dp 圆角 + 零阴影（Android 端卡片 elevation 全是 0dp） */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) { content() }
}

/**
 * 正文次要文字色。Android 端全仓库**不用** `textColorSecondary`，
 * 而是一律 `@android:color/darker_gray`（= `#444444`）。
 */
val DARKER_GRAY = Color(0xFF444444)

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

internal fun fmtTime(epochSeconds: Long): String = timeFmt.format(Date(epochSeconds * 1000L))

/** 横向滚动的一行瓦片（入口组用） */
@Composable
fun TileRow(content: @Composable () -> Unit) {
    AppCard {
        LazyRow(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}
