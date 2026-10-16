package com.example.c001apk.desktop.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.app.ui.common.FeedCard
import com.example.c001apk.desktop.app.ui.common.ReplyCard
import com.example.c001apk.desktop.core.model.Feed
import com.example.c001apk.desktop.core.net.ApiException
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.executeSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 动态详情页 —— 对齐 Android 端 `ui/feed/FeedFragment` + `res/layout/fragment_feed.xml`。
 *
 * ## Android 端结构（横屏）
 * ```
 * CoordinatorLayout
 *   contentLayout (appbar_scrolling_view_behavior)
 *     swipeRefresh → RecyclerView
 *       ConcatAdapter(HeaderAdapter, feedDataAdapter, feedFixAdapter,
 *                     feedReplyAdapter, footerAdapter)
 *   reply  FAB（回复）
 *   appBar → toolbar → titleProfile（滚动到顶时把作者行「吸」到顶栏）
 * ```
 * 横屏差异（`FeedFragment` 里逐项判断 `isPortrait`）：
 * * LayoutManager → `StaggeredGridLayoutManager(2, VERTICAL)`，**双列**
 * * 排序条（默认/最新/热门/楼主）**隐藏**：`binding.tabLayout.isVisible = false`
 * * 顶栏菜单里的「查看回复」**隐藏**
 * * 评论条目套 `text_card_bg`（12dp 圆角）+ padding 10dp
 *
 * ## 本实现
 * * 双列瀑布流；**正文卡片整行跨列**（Android 端因 `setFullSpan` 只在竖屏生效，
 *   横屏正文只占一列 —— 那更像疏漏，这里按「正文通栏 + 评论双列」处理）
 * * 顶部工具条：返回 + 标题
 * * 排序条按横屏行为隐藏（本阶段先不做排序）
 *
 * ⚠️ 接口 `/v6/feed/detail` 会限流（403 / 5902），失败时给出可重试提示。
 */
@Composable
fun DetailScreen(
    client: Client,
    feedId: String,
    onBack: () -> Unit,
) {
    var feed by remember(feedId) { mutableStateOf<Feed?>(null) }
    var replies by remember(feedId) { mutableStateOf<List<Feed>>(emptyList()) }
    var loading by remember(feedId) { mutableStateOf(true) }
    var error by remember(feedId) { mutableStateOf<String?>(null) }

    LaunchedEffect(feedId) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val d = client.api.getFeedContent(id = feedId).executeSync()
                if ((d.status ?: 0) != 0) throw ApiException(d.status ?: 0, d.message ?: "详情接口失败")
                val r = runCatching {
                    val rr = client.api.getFeedContentReply(id = feedId, page = 1).executeSync()
                    if ((rr.status ?: 0) == 0) rr.data ?: emptyList() else emptyList()
                }.getOrDefault(emptyList())
                (d.data to r)
            }
        }
        result.onSuccess { (f, r) ->
            feed = f
            replies = r
            loading = false
        }.onFailure {
            error = "${it::class.simpleName}: ${it.message}"
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = feed?.username?.let { "$it 的动态" } ?: "动态详情", onBack = onBack)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text("加载失败", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 该接口会限流，提示用户稍后重试而不是反复点
                    Text(
                        "该接口有频率限制，稍后重试即可",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyVerticalStaggeredGrid(
                // 横屏：双列（Android 端 StaggeredGridLayoutManager(2, VERTICAL)）
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyStaggeredGridState(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 24.dp),
            ) {
                // 正文：整行跨列（不截断）
                feed?.let { f ->
                    item(key = "content", span = StaggeredGridItemSpan.FullLine) {
                        FeedCard(f.toHomeEntity(), expandMessage = true)
                    }
                }
                // 评论标题
                item(key = "replyHeader", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        "共 ${replies.size} 条回复",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                if (replies.isEmpty()) {
                    item(key = "noReply", span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            "还没有回复",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                } else {
                    replies.forEachIndexed { i, r ->
                        item(key = "reply-$i-${r.id ?: i}") { ReplyCard(r) }
                    }
                }
            }
        }
    }
}

/** 顶栏：返回 + 标题（对齐 fragment_feed 的 toolbar，底色用 surfaceContainer 与内容拉开一档） */
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * [Feed] → [com.example.c001apk.desktop.core.model.HomeEntity]。
 *
 * 详情接口与列表接口的字段高度重合，但建模成了两个类型
 * （列表用 `HomeEntity`、详情/回复用 `Feed`）。渲染层统一走 `FeedCard`，
 * 故此处做一次字段搬运。两边字段口径一致（都来自 coolapk 的 feed 结构）。
 */
private fun Feed.toHomeEntity() = com.example.c001apk.desktop.core.model.HomeEntity(
    entityId = id,
    entityType = entityType,
    title = messageTitle,
    id = id,
    uid = uid,
    username = username,
    userAvatar = userAvatar,
    message = message,
    messageHtml = messageHtml,
    messageTitle = messageTitle,
    dateline = dateline,
    likenum = likenum,
    replynum = replynum,
    pic = pic,
    picArr = picArr,
    deviceTitle = deviceTitle,
    deviceName = deviceName,
)
