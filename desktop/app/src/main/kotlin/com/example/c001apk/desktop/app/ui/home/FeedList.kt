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
import androidx.compose.foundation.layout.size
import com.example.c001apk.desktop.app.ui.common.NetImage
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import com.example.c001apk.desktop.app.ui.common.AppCard
import com.example.c001apk.desktop.app.ui.common.DARKER_GRAY
import com.example.c001apk.desktop.app.ui.common.FeedCard
import com.example.c001apk.desktop.app.ui.common.fmtTime
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
fun FeedList(client: Client, tab: String, onOpenFeed: (String) -> Unit = {}) {
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
                        is FeedRow.Feed -> FeedCard(
                            row.e,
                            onClick = (row.e.entityId ?: row.e.id)?.let { fid -> { onOpenFeed(fid) } },
                        )
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

    /**
     * 是否整行跨列。
     *
     * ★ 实测修正（真机 UI 树量出来的坐标）：**入口卡不跨列**。
     * Android 端首屏坐标：
     * ```
     * imageCarouselCard  x=106dp  宽 369dp   ← 双列瀑布流的**左列**
     * iconLinkGridCard   x=485dp  宽 359dp   ← **右列**
     * 动态 CardView       x=106 / 485，宽 369 / 359
     * ```
     * 即「今日酷安」轮播与「值得看」图标卡是**左右并排**，各占一列 —— 不是通栏。
     * 上一版我把入口做成 `FullLine` 通栏，正是用户指出的「严重没对齐」的主因。
     */
    fun spanFull(): Boolean = false
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
 * 入口卡 —— 对应 Android 端各类「顶部聚合卡」。
 *
 * ## 尺寸全部来自真机 UI 树实测（853×640dp 横屏平板）
 * ```
 * imageCarouselCard  369×123dp  ← 「今日酷安」，占双列瀑布流的一列
 * iconLinkGridCard   359×65dp   ← 「值得看…」图标行；图标 36dp，y 方向 87..122
 * 每列内容左右各有 10dp padding（卡片内元素 x=116 / 495 相对卡片 x=106 / 485）
 * ```
 * ★ 关键结构：这些卡**各占一列**（不是通栏），与动态卡同处一个双列瀑布流。
 */
@Composable
private fun EntryGroupCard(row: FeedRow.EntryGroup) {
    when (kindOf(row.type)) {
        // 对应 `item_home_icon_link_grid_card.xml`：
        //   MaterialCardView（12dp 圆角）> ViewPager2（**每页 5 个**）+ CircleIndicator3
        //   页码指示器：高 10dp、marginBottom 3dp、圆点 10×4dp
        EntryKind.Image -> AppCard {
            Box(Modifier.fillMaxWidth().height(123.dp)) {
                LazyRow(Modifier.fillMaxSize()) {
                    items(row.entries.size) { i -> ImageEntryTile(row.entries[i]) }
                }
                if (row.entries.size > 1) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(androidx.compose.ui.graphics.Color(0x66000000))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "1/${row.entries.size}",
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
        }

        // 对应 `item_home_icon_mini_scroll_card.xml` / `item_home_image_text_scroll_card.xml`：
        //   MaterialCardView（12dp 圆角）> 横向 RecyclerView
        //   item（`..._item.xml`）：
        //     · iconMiniScrollCard 图标用 `circleImageStyle`（**50% 圆形**）
        //     · title 13sp、`marginStart 5dp`
        //     · 整个 item 是 wrap_content（图标 + 文字紧挨着，成为一枚「胶囊」）
        //   横向间隔由 `LinearItemDecoration1(10.dp)` 提供
        //
        // ★ 之前我把每个入口渲染成「大图标 + 下方文字」的竖排瓦片，
        //   与真机的「圆形小图标 + 右侧文字」横向胶囊完全不同 —— 这是用户
        //   圈出来指正的地方（看起来像被拆开了）。
        EntryKind.IconGrid -> AppCard {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(row.entries.size) { i -> CapsuleEntry(row.entries[i]) }
            }
        }

        EntryKind.ListRow -> AppCard {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(row.entries.size) { i -> CapsuleEntry(row.entries[i]) }
            }
        }
    }
}

/**
 * 横向胶囊入口 —— 复刻 `item_home_icon_mini_scroll_card_item.xml`：
 * 圆形图标（`circleImageStyle` = 50%）+ 紧邻的 13sp 标题。
 *
 * 真实结构是 `wrap_content` 的一整块（图标与文字属于同一个 item），
 * 所以这里也必须用一个 Row 把它们绑在一起 —— 不能各占一块。
 */
@Composable
private fun CapsuleEntry(e: FeedRow.Entry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                // iconMiniScrollCard 用 circleImageStyle：**50% 圆角 = 圆形**
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!e.pic.isNullOrBlank()) {
                NetImage(
                    url = e.pic,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                )
            } else {
                Text(
                    e.title.take(1),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // title 的 marginStart = 5dp，textSize = 13sp
        Spacer(Modifier.width(5.dp))
        Text(
            e.title,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 轮播瓦片：占满整张卡（369×123dp），文字居中 */
@Composable
private fun ImageEntryTile(e: FeedRow.Entry) {
    Box(
        Modifier
            .width(369.dp)
            .height(123.dp)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            e.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}


