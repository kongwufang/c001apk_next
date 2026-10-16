package com.example.c001apk.desktop.app.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.app.homeDiagLog
import com.example.c001apk.desktop.core.model.HomeEntity
import com.example.c001apk.desktop.core.net.ApiException
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.executeSync
import com.example.c001apk.desktop.core.text.HtmlText
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 动态列表（首页各 tab 的内容）。
 *
 * ## 数据来源实测（`/v6/main/indexV8`）
 * `data` 是**异质混合数组**：
 * ```
 * 顶层 11 项 = card(4) + feed(7)
 *   card[0].entities = image_1  × 3     「今日酷安」「首页轮播」…
 *   card[1].entities = iconLink × 10    「值得看」「热闻」「活动」…（图标宫格）
 *   card[2].entities = product/topic ×20 「一加13」「数码」…（横向滚动卡）
 *   card[3..9]       = feed             ← 真实动态直接在顶层
 *   card[10].entities = feed × 20       「优质图文」（动态聚合卡）
 * 全层级：feed 27 个，**全部带正文**
 * ```
 * ⚠️ 关键教训：实测把全部 59 项拍平成**同质网格**时，入口类（33 项）排在前面，
 * 真实动态从第 33 项才开始 —— 首屏几乎全是入口标题，看不到动态。
 * 所以入口类必须与动态**视觉上区分**，并且入口卡整行跨列（Android 端也是卡片整块）。
 *
 * ## 横屏核心差异
 * Android 端 `BaseViewFragment.initView()`：
 * ```
 * if (isPortrait) LinearLayoutManager() else StaggeredGridLayoutManager(2, VERTICAL)
 * ```
 * 桌面窗口天然是横屏 → 固定双列瀑布流。
 */
@Composable
fun FeedList(client: Client, tab: String) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<FeedRow>>(emptyList()) }
    var total by remember { mutableStateOf(0) }

    LaunchedEffect(tab) {
        loading = true
        error = null
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = client.api.indexV8(page = 1).executeSync()
                val st = resp.status ?: 0
                if (st != 0) throw ApiException(st, resp.message ?: "status=$st")
                resp.data ?: emptyList()
            }
        }.onSuccess {
            total = it.size
            rows = flatten(it)
            val feeds = rows.count { r -> r is FeedRow.Feed }
            println("[home] indexV8 原始 $total 项 → ${rows.size} 行（动态 $feeds / 入口 ${rows.size - feeds}）")
            runCatching {
                val diag = StringBuilder()
                diag.appendLine("[home] indexV8 原始 $total 项 → ${rows.size} 行（动态 $feeds / 入口 ${rows.size - feeds}）")
                rows.forEachIndexed { idx, r ->
                    when (r) {
                        is FeedRow.Feed -> diag.appendLine(
                            "  #$idx FEED  ${r.e.username} | msgLen=${r.e.message?.length ?: 0}"
                        )
                        is FeedRow.Entry -> diag.appendLine("  #$idx entry [${r.type}] ${r.title}")
                    }
                }
                homeDiagLog().appendText(diag.toString())
            }
            loading = false
        }.onFailure {
            error = "${it::class.simpleName}: ${it.message}"
            loading = false
        }
    }

    when {
        error != null -> Centered {
            Text("加载失败", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        loading && rows.isEmpty() -> Centered { CircularProgressIndicator() }

        rows.isEmpty() -> Centered {
            Text(
                "「$tab」暂无内容（原始 $total 项）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyStaggeredGridState(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 24.dp),
        ) {
            rows.forEachIndexed { idx, row ->
                item(
                    key = "$idx-${row.keyPart()}",
                    // 入口类整行跨列：它们是「区块」，不是瀑布流条目
                    span = if (row.spanFull()) StaggeredGridSpanFull else null,
                ) {
                    when (row) {
                        is FeedRow.Feed -> FeedCard(row.e)
                        is FeedRow.Entry -> EntryCard(row)
                    }
                }
            }
        }
    }
}

private val StaggeredGridSpanFull = StaggeredGridItemSpan.FullLine

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            content()
        }
    }
}

/** 列表行 */
sealed interface FeedRow {
    /** 真实动态 */
    data class Feed(val e: HomeEntity) : FeedRow

    /** 入口 / 聚合条目。[type] 为服务端 entityType（image_1 / iconLink / product / topic …） */
    data class Entry(
        val type: String,
        val title: String,
        val subtitle: String?,
        val pic: String? = null,
    ) : FeedRow

    fun keyPart(): String = when (this) {
        is Feed -> e.entityId ?: e.id ?: e.message?.take(16).orEmpty()
        is Entry -> "$type:$title"
    }

    /** 入口类整行跨列；动态按瀑布流走 */
    fun spanFull(): Boolean = this is Entry
}

/**
 * 入口卡的展示形态 —— 对着 Android 端的实体类型做区分。
 *
 * Android 端每种 `entityType` 都有自己的 item 布局（`imageScaleCard` / `iconLargeScrollCard` /
 * `capsuleListCard` / `imageTextScrollCard` …，见 `_rev/CARD_UI_MAP.md`），
 * 桌面端先按「大图 / 图标宫格 / 图文列表」三类归并。
 */
private enum class EntryKind { Image, IconGrid, ListRow }

private fun kindOf(type: String): EntryKind = when (type) {
    "image_1", "image", "imageCarouselCard_1", "imageScaleCard" -> EntryKind.Image
    "iconLink", "iconLinkGridCard", "iconMiniScrollCard" -> EntryKind.IconGrid
    else -> EntryKind.ListRow
}

/** 把混合数组拍平成可渲染行（递归展开卡片信封，限 3 层） */
private fun flatten(data: List<JsonElement>): List<FeedRow> {
    val out = mutableListOf<FeedRow>()

    fun emit(el: JsonElement, depth: Int) {
        val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val type = obj.get("entityType")?.let { if (it.isJsonPrimitive) it.asString else null }.orEmpty()

        // 动态
        if (type == "feed" || obj.has("message")) {
            runCatching { gson.fromJson(el, HomeEntity::class.java) }.getOrNull()
                ?.let { out.add(FeedRow.Feed(it)) }
            return
        }
        // 卡片信封：展开
        if (depth < 3) {
            val inner = obj.getAsJsonArray("entities")
            if (inner != null && inner.size() > 0) {
                inner.forEach { emit(it, depth + 1) }
                return
            }
        }
        // 其它：入口条目。[type] 保留服务端原始 entityType，用于诊断与后续按类型细分
        val title = jsonString(el, "title") ?: return
        if (title.isBlank()) return
        val sub = jsonString(el, "description") ?: jsonString(el, "subTitle")
        out.add(FeedRow.Entry(type = type, title = title, subtitle = sub, pic = jsonString(el, "pic")))
    }

    data.forEach { emit(it, 0) }
    return out
}

private val gson = Gson()

private fun jsonString(el: JsonElement?, key: String): String? {
    val o = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val v = o.get(key) ?: return null
    return if (v.isJsonPrimitive) v.asString else null
}

/**
 * 动态卡片 —— 对齐 `res/layout/item_home_feed.xml`。
 *
 * 实测关键数值：
 * ```
 * MaterialCardView: cardBackgroundColor=@color/home_card_background_color
 *                   (= colorSurfaceContainer)、elevation=0dp、strokeWidth=0dp
 *                   内容 padding=10dp
 * uname        15sp  singleLine  ellipsize=end
 * from/device  12sp  darker_gray
 * 私有角标      11sp  padding 5/1/5/1  bg_private_badge(#22808080, radius 4dp)
 * messageTitle 16sp bold  lineSpacingMultiplier=1.3
 * message      16sp  lineSpacingMultiplier=1.2  maxLines=5  ellipsize=end
 * messageMore  「查看更多」13sp bold colorPrimary（截断才显示，默认不占位）
 * 底部动作      只有 2 个：reply + like；左时间、右互动
 * ```
 */
@Composable
private fun FeedCard(e: HomeEntity) {
    AppCard {
        Column(Modifier.padding(10.dp)) {
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

            val dev = e.deviceTitle ?: e.deviceName
            if (!dev.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(dev, fontSize = 12.sp, color = DARKER_GRAY, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            e.messageTitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    HtmlText.toPlainText(it),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // message 含行内 HTML（如 <a class="feed-link-tag" href="/t/…">#话题#</a>），
            // 必须解析后渲染，否则会露出标签源码。
            val runs = HtmlText.parse(e.message)
            var truncated by remember(e.entityId, e.id) { mutableStateOf(false) }
            if (runs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = runs.toAnnotated(),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { r -> truncated = r.hasVisualOverflow },
                )
            }
            if (truncated) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "查看更多",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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

/** 入口卡片：按类型给不同形态 */
@Composable
private fun EntryCard(row: FeedRow.Entry) {
    when (kindOf(row.type)) {
        EntryKind.Image -> ImageEntryCard(row)
        EntryKind.IconGrid -> IconGridEntryCard(row)
        EntryKind.ListRow -> ListEntryCard(row)
    }
}

/** 大图入口（image_1 / imageScaleCard）：偏高的横幅，用于「今日酷安」「首页轮播」 */
@Composable
private fun ImageEntryCard(row: FeedRow.Entry) {
    AppCard {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            row.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    HtmlText.toPlainText(it),
                    fontSize = 13.sp,
                    color = DARKER_GRAY,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

/**
 * 图标宫格入口（iconLink）：Android 端是横向滚动的图标 + 标题。
 * 桌面端整行跨列，横向排列，超出宽度由外层滚动承载。
 */
@Composable
private fun IconGridEntryCard(row: FeedRow.Entry) {
    AppCard {
        Column(Modifier.padding(12.dp)) {
            Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            row.subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    HtmlText.toPlainText(it),
                    fontSize = 13.sp,
                    color = DARKER_GRAY,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 普通列表入口（product / topic …） */
@Composable
private fun ListEntryCard(row: FeedRow.Entry) {
    AppCard {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(44.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.title.take(1),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        HtmlText.toPlainText(it),
                        fontSize = 13.sp,
                        color = DARKER_GRAY,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 统一的卡片外观：colorSurfaceContainer 底、12dp 圆角、零阴影（Android 端卡片 elevation 全是 0dp） */
@Composable
private fun AppCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) { content() }
}

/** 文本片段 → 带样式 AnnotatedString：链接用主色 */
@Composable
private fun List<HtmlText.TextRun>.toAnnotated(): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        this@toAnnotated.forEach { run ->
            if (run.href != null) {
                withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) { append(run.text) }
            } else {
                append(run.text)
            }
        }
    }
}

/**
 * 正文次要文字色。Android 端全仓库**不用** `textColorSecondary`，
 * 而是一律 `@android:color/darker_gray`（= `#444444`）。
 */
private val DARKER_GRAY = Color(0xFF444444)

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun fmtTime(epochSeconds: Long): String = timeFmt.format(Date(epochSeconds * 1000L))
