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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.app.homeDiagLog
import com.example.c001apk.desktop.app.ui.common.buildRichText
import com.example.c001apk.desktop.app.ui.common.emojiInlineContents
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
            val keepEntries = tab == DEFAULT_TAB_VALUE
            rows = flatten(it, keepEntries)
            val feeds = rows.count { r -> r is FeedRow.Feed }
            val groups = rows.count { r -> r is FeedRow.EntryGroup }
            println("[home] indexV8 原始 $total 项 → ${rows.size} 行（动态 $feeds / 入口组 $groups）")
            runCatching {
                val diag = StringBuilder()
                diag.appendLine(
                    "[home] indexV8 原始 $total 项 → ${rows.size} 行" +
                        "（动态 $feeds / 入口组 $groups，keepEntries=$keepEntries）"
                )
                rows.forEachIndexed { idx, r ->
                    when (r) {
                        is FeedRow.Feed -> diag.appendLine(
                            "  #$idx FEED  ${r.e.username} | msgLen=${r.e.message?.length ?: 0}"
                        )
                        is FeedRow.Entry -> diag.appendLine("  #$idx entry [${r.type}] ${r.title}")
                        is FeedRow.EntryGroup -> diag.appendLine(
                            "  #$idx GROUP [${r.type}] ×${r.entries.size} :: " +
                                r.entries.joinToString(" / ") { e -> e.title }
                        )
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
                        is FeedRow.EntryGroup -> EntryGroupCard(row)
                        // flatten 现在只产出 EntryGroup（同类入口已打包成横向一行），
                        // 单个 Entry 仍保留类型以便将来按类型细分渲染。
                        is FeedRow.Entry -> EntryGroupCard(FeedRow.EntryGroup(row.type, listOf(row)))
                    }
                }
            }
        }
    }
}

/** 默认选中的 tab（与 [com.example.c001apk.desktop.app.ui.home.DEFAULT_TAB] 一致） */
private const val DEFAULT_TAB_VALUE = "头条"

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

    /** 单个入口（一般会被打包进 [EntryGroup]） */
    data class Entry(
        val type: String,
        val title: String,
        val subtitle: String?,
        val pic: String? = null,
    ) : FeedRow

    /**
     * 一组**同类**入口，渲染成一行横向滚动卡。
     *
     * 对应 Android 端的横向滚动卡（`iconMiniScrollCard` / `imageTextScrollCard` 等）：
     * 多个入口共用**一个纵向位置**，横向滚动查看。这样入口不会把动态挤出首屏。
     */
    data class EntryGroup(val type: String, val entries: List<Entry>) : FeedRow

    fun keyPart(): String = when (this) {
        is Feed -> e.entityId ?: e.id ?: e.message?.take(16).orEmpty()
        is Entry -> "$type:$title"
        is EntryGroup -> "group:$type:${entries.firstOrNull()?.title.orEmpty()}"
    }

    /** 入口组整行跨列；动态按双列瀑布流走 */
    fun spanFull(): Boolean = this is EntryGroup
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

/**
 * 把混合数组拍平成可渲染行（递归展开卡片信封，限 3 层）。
 *
 * ## 为什么给入口设配额（重要）
 * Android 端这些入口是**横向滚动卡**（`iconMiniScrollCard` / `imageTextScrollCard` /
 * `capsuleListCard` 等），一屏只占一行高度、内容横向滚动，所以不挤占纵向空间。
 * 桌面端目前还没有横向滚动卡，若照样把 33 个入口铺进纵向瀑布流，
 * 首屏会被入口标题占满、真实动态全被推到屏幕外
 * （实测诊断：59 行里 #0–#32 全是入口，动态从 #33 才开始，而首屏只放得下约 34 个位置）。
 * 故每个卡片区块最多收 [MAX_ENTRIES_PER_GROUP] 个入口，先把动态露出来；
 * 等横向滚动卡实现后再放开这个限制。
 */
private fun flatten(data: List<JsonElement>, keepEntries: Boolean): List<FeedRow> {
    val flat = mutableListOf<FeedRow>()

    fun isFeedEl(el: JsonElement): Boolean {
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val t = o.get("entityType")?.takeIf { it.isJsonPrimitive }?.asString
        return t == "feed" || o.has("message")
    }

    fun emit(el: JsonElement, depth: Int) {
        val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return

        // 动态：不受入口配额限制，永远保留
        if (isFeedEl(el)) {
            runCatching { gson.fromJson(el, HomeEntity::class.java) }.getOrNull()
                ?.let { flat.add(FeedRow.Feed(it)) }
            return
        }
        val type = obj.get("entityType")?.let { if (it.isJsonPrimitive) it.asString else null }.orEmpty()

        // 卡片信封：展开，并限制该卡的入口数
        if (depth < 3) {
            val inner = obj.getAsJsonArray("entities")
            if (inner != null && inner.size() > 0) {
                var taken = 0
                for (i in 0 until inner.size()) {
                    val child = inner.get(i)
                    if (isFeedEl(child)) {
                        emit(child, depth + 1)
                    } else if (taken < MAX_ENTRIES_PER_GROUP) {
                        emit(child, depth + 1)
                        taken++
                    }
                }
                return
            }
        }
        // 入口条目
        val title = jsonString(el, "title") ?: return
        if (title.isBlank()) return
        flat.add(
            FeedRow.Entry(
                type = type,
                title = title,
                subtitle = jsonString(el, "description") ?: jsonString(el, "subTitle"),
                pic = jsonString(el, "pic"),
            )
        )
    }

    data.forEach { emit(it, 0) }

    // ── 后处理：把连续的同类入口打包成【一行横向滚动】 ──
    // Android 端这些正是横向滚动卡（一次只占一个纵向位置），
    // 逐项铺进纵向瀑布流会把真实动态挤出首屏。
    val out = mutableListOf<FeedRow>()
    var i = 0
    while (i < flat.size) {
        val it0 = flat[i]
        if (it0 is FeedRow.Entry && it0.type.isNotEmpty()) {
            val group = mutableListOf(it0)
            var j = i + 1
            while (j < flat.size && group.size < MAX_ENTRIES_PER_GROUP) {
                val nxt = flat[j]
                if (nxt is FeedRow.Entry && nxt.type == it0.type) {
                    group.add(nxt)
                    j++
                } else break
            }
            out.add(FeedRow.EntryGroup(it0.type, group))
            i = j
        } else {
            out.add(it0)
            i++
        }
    }
    return out
}

/** 每个「同类入口组」最多打包几项；超出的入口直接丢弃（详见 [flatten] 说明） */
private const val MAX_ENTRIES_PER_GROUP = 6

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
            // 解析结果同时含表情码（[受虐滑稽]…），由 buildRichText 转成行内表情图。
            val pieces = HtmlText.parse(e.message)
            val inline = emojiInlineContents(pieces)
            var truncated by remember(e.entityId, e.id) { mutableStateOf(false) }
            if (pieces.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = buildRichText(pieces),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = inline,
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

/**
 * 同类入口组 —— 渲染成**一行横向滚动卡**，对应 Android 端的
 * `iconMiniScrollCard` / `imageTextScrollCard` / `capsuleListCard` 等：
 * 多个入口共用同一个纵向位置，超出宽度横向滚动。
 *
 * 这是「入口不挤占动态」的关键：入口组只吃掉一个纵向位置，
 * 而不是每个入口各占一行。
 */
@Composable
private fun EntryGroupCard(row: FeedRow.EntryGroup) {
    when (kindOf(row.type)) {
        EntryKind.Image -> AppCard {
            LazyRow(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(row.entries.size) { i -> ImageEntryTile(row.entries[i]) }
            }
        }

        EntryKind.IconGrid -> AppCard {
            LazyRow(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(row.entries.size) { i -> IconEntryTile(row.entries[i]) }
            }
        }

        EntryKind.ListRow -> AppCard {
            LazyRow(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(row.entries.size) { i -> ListEntryTile(row.entries[i]) }
            }
        }
    }
}

/** 大图入口瓦片（image_1 / imageScaleCard） */
@Composable
private fun ImageEntryTile(e: FeedRow.Entry) {
    Column(Modifier.width(220.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                e.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        e.subtitle?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                HtmlText.toPlainText(it),
                fontSize = 12.sp,
                color = DARKER_GRAY,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 图标宫格入口瓦片（iconLink） */
@Composable
private fun IconEntryTile(e: FeedRow.Entry) {
    Column(
        Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(42.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                e.title.take(1),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            e.title,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 列表型入口瓦片（product / topic …） */
@Composable
private fun ListEntryTile(e: FeedRow.Entry) {
    Column(Modifier.width(150.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                e.title.take(1),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            e.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        e.subtitle?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                HtmlText.toPlainText(it),
                fontSize = 12.sp,
                color = DARKER_GRAY,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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

/**
 * 正文次要文字色。Android 端全仓库**不用** `textColorSecondary`，
 * 而是一律 `@android:color/darker_gray`（= `#444444`）。
 */
private val DARKER_GRAY = Color(0xFF444444)

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun fmtTime(epochSeconds: Long): String = timeFmt.format(Date(epochSeconds * 1000L))
