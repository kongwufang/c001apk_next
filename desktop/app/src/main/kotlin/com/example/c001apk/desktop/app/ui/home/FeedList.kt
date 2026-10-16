package com.example.c001apk.desktop.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillParentMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
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
            rows = flatten(it)
            val feeds = rows.count { r -> r is FeedRow.Feed }
            val cards = rows.count { r -> r is FeedRow.Card }
            println("[home] indexV8 原始 $total 项 → ${rows.size} 行（动态 $feeds / 卡片 $cards）")
            runCatching {
                val diag = StringBuilder()
                diag.appendLine(
                    "[home] indexV8 原始 $total 项 → ${rows.size} 行（动态 $feeds / 卡片 $cards）"
                )
                rows.forEachIndexed { idx, r ->
                    when (r) {
                        is FeedRow.Feed -> diag.appendLine(
                            "  #$idx FEED  ${r.e.username} | msgLen=${r.e.message?.length ?: 0}"
                        )
                        is FeedRow.Card -> diag.appendLine(
                            "  #$idx CARD  template=${r.template} entries=${r.entries.size}" +
                                " title=${r.title.orEmpty()}" +
                                " 首实体=${r.entries.firstOrNull()?.type.orEmpty()}"
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
                        // 卡片按 entityTemplate 分流（见 CardRow）
                        is FeedRow.Card -> CardRow(row)
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
    /** 顶层动态（`entityType=feed`，直接排在瀑布流里） */
    data class Feed(val e: HomeEntity) : FeedRow

    /**
     * 顶层卡片。**渲染形态由 `template`（= 服务端 `entityTemplate`）决定**，
     * 不是由 `entityType` 决定 —— 见 `AppAdapter.getItemViewType()`。
     */
    data class Card(
        val template: String,
        val title: String?,
        val entries: List<Entry>,
    ) : FeedRow

    fun keyPart(): String = when (this) {
        is Feed -> e.entityId ?: e.id ?: e.message?.take(16).orEmpty()
        is Card -> "card:$template:${entries.firstOrNull()?.title.orEmpty()}"
    }

    /**
     * 是否整行跨列 —— **都不跨列**。
     *
     * 实测真机 UI 树坐标：`imageCarouselCard` 在 x=106dp 宽 369dp、
     * `iconLinkGridCard` 在 x=485dp 宽 359dp，即入口卡也是**各占双列瀑布流的一列**。
     * （早期版本我把它们做成 FullLine 通栏，是用户指出「严重没对齐」的主因。）
     */
    fun spanFull(): Boolean = false
}

/**
 * 卡片内的一个条目。
 *
 * 既可能是「入口」（`image_1` / `iconLink` / `product` / `topic` …），
 * 也可能是「动态」（`imageTextScrollCard` 里装的是 `feedCover`）。
 */
data class Entry(
    val type: String,
    val template: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val pic: String? = null,
    /** 条目本身是动态时（`feedCover` / `feed`）解析出来的实体 */
    val feed: HomeEntity? = null,
)

/**
 * 把首页数据转成可渲染行。
 *
 * ## 关键：分流看 `entityTemplate`
 * 实测 `/v6/main/indexV8` 的 `data`：
 * ```
 * #0  card  imageCarouselCard_1   entities=2   首实体 image_1   -> 轮播
 * #1  card  iconLinkGridCard      entities=10  首实体 iconLink  -> ViewPager 每页5 + 圆点
 * #2  card  iconMiniScrollCard    entities=20  首实体 product   -> 横向胶囊
 * #3..#9  feed  template=feed     各 1 条                      -> 普通动态卡
 * #10 card  imageTextScrollCard   entities=19  title='优质图文' -> 带标题横向卡
 * ```
 * 早期版本只看 `entityType`、并把所有入口拍平进同质网格，导致：
 * ① 卡片形态全错 ② 入口把动态挤出首屏。两者都已修正。
 */
private fun flatten(data: List<JsonElement>): List<FeedRow> {
    val out = mutableListOf<FeedRow>()

    fun objOf(el: JsonElement): com.google.gson.JsonObject? =
        el.takeIf { it.isJsonObject }?.asJsonObject

    fun str(o: com.google.gson.JsonObject, k: String): String? =
        o.get(k)?.takeIf { it.isJsonPrimitive }?.asString

    fun asEntry(el: JsonElement): Entry {
        val o = objOf(el) ?: return Entry(type = "")
        val t = str(o, "entityType").orEmpty()
        val isFeed = t == "feed" || o.has("message")
        return Entry(
            type = t,
            template = str(o, "entityTemplate"),
            title = str(o, "title").orEmpty(),
            subtitle = str(o, "description") ?: str(o, "subTitle"),
            // 入口的图字段不统一：pic / logo / tpic / cover 都见过
            pic = str(o, "pic") ?: str(o, "logo") ?: str(o, "tpic") ?: str(o, "cover"),
            feed = if (isFeed) {
                runCatching { gson.fromJson(el, HomeEntity::class.java) }.getOrNull()
            } else null,
        )
    }

    data.forEach { el ->
        val o = objOf(el) ?: return@forEach
        val type = str(o, "entityType").orEmpty()
        val inner = o.getAsJsonArray("entities")

        // 顶层动态
        if (inner == null && (type == "feed" || o.has("message"))) {
            runCatching { gson.fromJson(el, HomeEntity::class.java) }.getOrNull()
                ?.let { out.add(FeedRow.Feed(it)) }
            return@forEach
        }
        // 顶层卡片
        if (inner != null && inner.size() > 0) {
            out.add(
                FeedRow.Card(
                    template = str(o, "entityTemplate").orEmpty(),
                    title = str(o, "title"),
                    entries = inner.map { asEntry(it) },
                )
            )
        }
    }
    return out
}

private val gson = Gson()

private fun jsonString(el: JsonElement?, key: String): String? {
    val o = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val v = o.get(key) ?: return null
    return if (v.isJsonPrimitive) v.asString else null
}

// ────────────────────────── 卡片渲染（按 entityTemplate 分流）──────────────────────────

/**
 * 按 `entityTemplate` 选渲染器 —— 对齐 `AppAdapter.getItemViewType()` 的映射。
 *
 * 已实现真实数据里出现的四种；其余走 [CapsuleScrollCard] 兜底
 * （Android 端对未知模板也是交给通用卡片，见 viewType 26）。
 */
@Composable
private fun CardRow(row: FeedRow.Card) {
    when (row.template) {
        "imageCarouselCard_1" -> CarouselCard(row)
        "iconLinkGridCard" -> IconLinkGridCard(row)
        "imageTextScrollCard" -> ImageTextScrollCard(row)
        "imageSquareScrollCard" -> CapsuleScrollCard(row)
        "iconMiniScrollCard", "iconMiniGridCard" -> CapsuleScrollCard(row)
        else -> CapsuleScrollCard(row)
    }
}

/**
 * 轮播卡 —— `imageCarouselCard_1`。
 *
 * 实测：369×123dp（占双列瀑布流一列），右下角 `1/N` 页码。
 * Android 端是 ViewPager2 + 页码指示器（`imageCarouselCard` 容器）。
 */
@Composable
private fun CarouselCard(row: FeedRow.Card) {
    val state = rememberLazyListState()
    val page by remember { derivedStateOf { state.firstVisibleItemIndex } }

    AppCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(123.dp),
        ) {
            LazyRow(Modifier.fillMaxSize(), state = state) {
                items(row.entries.size) { i ->
                    val e = row.entries[i]
                    Box(
                        Modifier
                            .fillParentMaxWidth()
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!e.pic.isNullOrBlank()) {
                            NetImage(url = e.pic, modifier = Modifier.fillMaxSize())
                        } else {
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
                }
            }
            if (row.entries.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${page + 1}/${row.entries.size}",
                        fontSize = 11.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 图标宫格卡 —— `iconLinkGridCard`。
 *
 * 真实实现（`AppAdapter.IconLinkGridCardViewHolder` + `IconLinkGridCardAdapter`）：
 * ```
 * MaterialCardView > ViewPager2
 *   页数 = entities.size / 5        （**整除**，多余的会被丢掉 —— 保持原行为）
 *   每页 = NoScrollGridView(numColumns = 5)
 *   item = 36dp 图标 + title（marginTop 4dp、12sp）
 * 页数 >= 2 时显示 CircleIndicator3（高 10dp、marginBottom 3dp、圆点 10x4dp）
 * ```
 */
@Composable
private fun IconLinkGridCard(row: FeedRow.Card) {
    val perPage = 5
    val pageCount = row.entries.size / perPage
    if (pageCount <= 0) return
    val pages = (0 until pageCount).map { p ->
        row.entries.subList(p * perPage, (p + 1) * perPage)
    }
    val state = rememberLazyListState()
    val cur by remember { derivedStateOf { state.firstVisibleItemIndex } }

    AppCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            LazyRow(Modifier.fillMaxWidth(), state = state) {
                items(pages.size) { pi ->
                    Row(
                        Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        pages[pi].forEach { e ->
                            Box(Modifier.weight(1f)) { IconLinkCell(e) }
                        }
                    }
                }
            }
            if (pageCount >= 2) {
                Spacer(Modifier.height(3.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pageCount) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(width = 10.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i == cur) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 宫格里的一个图标入口：36dp 图标 + 12sp 标题（marginTop 4dp） */
@Composable
private fun IconLinkCell(e: Entry) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!e.pic.isNullOrBlank()) {
                NetImage(
                    url = e.pic,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Text(
                    e.title.take(1),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            e.title,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 带标题的横向卡 —— `imageTextScrollCard`（真实数据里 title=「优质图文」）。
 *
 * 真实规格：
 * ```
 * MaterialCardView（12dp 圆角）
 *   title TextView：padding(10,10,10,0)（代码里 setPadding）
 *   横向 RecyclerView，间隔 LinearItemDecoration1(10dp)
 *     item 宽度 = (屏宽/2 - 50dp) 再乘 2/3
 *                横屏 853dp 屏 -> (426.5-50)=376.5 -> 376.5-125.5 = 251dp
 *     item = 图片（宽=251dp，高=宽×291/649≈112dp，**上两角 12dp**）
 *            + 标题（2 行、padding 10dp、**下两角 12dp** 背景）
 * ```
 */
@Composable
private fun ImageTextScrollCard(row: FeedRow.Card) {
    AppCard {
        Column(Modifier.fillMaxWidth()) {
            row.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp),
                )
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(row.entries.size) { i -> ImageTextCell(row.entries[i]) }
            }
        }
    }
}

/** `imageTextScrollCard` 的单条：251dp 宽，图 + 2 行标题 */
@Composable
private fun ImageTextCell(e: Entry) {
    Column(Modifier.width(251.dp)) {
        // 图片：上两角 12dp（textScrollImgRadius）
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!e.pic.isNullOrBlank()) {
                NetImage(url = e.pic, modifier = Modifier.fillMaxSize())
            }
        }
        // 标题：2 行、padding 10dp、下两角 12dp
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(10.dp),
        ) {
            Text(
                e.title.ifBlank { e.feed?.messageTitle.orEmpty() },
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 横向胶囊卡 —— `iconMiniScrollCard` / `iconMiniGridCard`（真实数据里 20 个 product）。
 *
 * 真实实现（`item_home_icon_mini_scroll_card(_item).xml`）：
 * ```
 * MaterialCardView（12dp 圆角）> 横向 RecyclerView，间隔 10dp
 * item（wrap_content）= 圆形图标（circleImageStyle = 50%）+ title（marginStart 5dp、13sp）
 * ```
 * ★ 图标与标题属于**同一个 item**，必须紧挨着 —— 不能拆成两块。
 */
@Composable
private fun CapsuleScrollCard(row: FeedRow.Card) {
    AppCard {
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(row.entries.size) { i -> CapsuleCell(row.entries[i]) }
        }
    }
}

/** 胶囊：圆形图标 + 紧邻的 13sp 标题 */
@Composable
private fun CapsuleCell(e: Entry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)          // circleImageStyle = 50%
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!e.pic.isNullOrBlank()) {
                NetImage(
                    url = e.pic,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                )
            } else {
                Text(
                    e.title.take(1),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(5.dp))        // title marginStart = 5dp
        Text(
            e.title,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
