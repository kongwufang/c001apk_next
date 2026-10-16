package com.example.c001apk.desktop.app.ui.home

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
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 数据来源实测：`/v6/main/indexV8` 的 `data` 是**异质混合数组**：
 * ```
 * ① { entityType:"feed", username, message, … }   ← 动态直接在顶层
 * ② { entityType:"card", entities:[ … ] }         ← 卡片信封
 *      卡内元素可能是 image_1 / iconLink / product / topic / feed …
 * ```
 * 故在 JsonElement 上按 `entityType` 分发，**不能**先映射成固定 POJO（会丢字段）。
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
            val entries = rows.size - feeds
            println("[home] indexV8 原始 ${total} 项 → 渲染 ${rows.size} 行（动态 $feeds / 入口 $entries）")
            rows.filterIsInstance<FeedRow.Feed>().take(3).forEach { r ->
                println(
                    "[home]   feed username=${r.e.username} " +
                        "dateline=${r.e.dateline} msgLen=${r.e.message?.length ?: 0} " +
                        "首 40 字=${r.e.message?.take(40)?.replace("\n", " ")}"
                )
            }
            rows.filterIsInstance<FeedRow.Entry>().take(3).forEach { r ->
                println("[home]   entry title=${r.title}")
            }
            // 诊断：把「渲染顺序 + 每行类型」写进 UTF-8 日志文件。
            // 走文件而不是控制台 —— Windows 控制台默认 GBK，Java 输出的 UTF-8 中文会乱码，
            // 而且重定向还会截断长输出，靠日志文件才看得准。
            runCatching {
                val diag = StringBuilder()
                diag.appendLine("[home] indexV8 原始 $total 项 → ${rows.size} 行（动态 $feeds / 入口 $entries）")
                rows.forEachIndexed { idx, r ->
                    when (r) {
                        is FeedRow.Feed -> diag.appendLine(
                            "  #$idx FEED  ${r.e.username} | msgLen=${r.e.message?.length ?: 0} | " +
                                (r.e.message ?: "").replace("\n", " ").take(40)
                        )
                        is FeedRow.Entry -> diag.appendLine("  #$idx entry ${r.title}")
                    }
                }
                com.example.c001apk.desktop.app.homeDiagLog().appendText(diag.toString())
            }
            loading = false
        }.onFailure {
            error = "${it::class.simpleName}: ${it.message}"
            loading = false
        }
    }

    when {
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("加载失败", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "「$tab」暂无内容（原始 $total 项）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyVerticalStaggeredGrid(
            // ★ 横屏核心差异：双列瀑布流
            // Android 端 BaseViewFragment.initView()：
            //   if (isPortrait) LinearLayoutManager() else StaggeredGridLayoutManager(2, VERTICAL)
            // 桌面端窗口天然是横屏 → 固定双列。
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyStaggeredGridState(),
            // Android 端 StaggerItemDecoration(10.dp)：左/右/bottom 各 10dp，top 恒为 0
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            contentPadding = PaddingValues(
                start = 10.dp, end = 10.dp, top = 0.dp, bottom = 24.dp,
            ),
        ) {
            items(rows.size) { i ->
                when (val row = rows[i]) {
                    is FeedRow.Feed -> FeedCard(row.e)
                    is FeedRow.Entry -> EntryCard(row.title, row.subtitle)
                }
            }
        }
    }
}

/** 列表行：动态 / 卡片入口 */
sealed interface FeedRow {
    data class Feed(val e: HomeEntity) : FeedRow
    data class Entry(val title: String, val subtitle: String?) : FeedRow
}

private val gson = Gson()

private fun jsonString(el: JsonElement?, key: String): String? {
    val o = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val v = o.get(key) ?: return null
    return if (v.isJsonPrimitive) v.asString else null
}

/** 把混合数组拍平成可渲染行（递归展开卡片信封，限 3 层） */
private fun flatten(data: List<JsonElement>): List<FeedRow> {
    val out = mutableListOf<FeedRow>()

    fun emit(el: JsonElement, depth: Int) {
        val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val type = obj.get("entityType")?.let { if (it.isJsonPrimitive) it.asString else null }

        if (type == "feed" || obj.has("message")) {
            runCatching { gson.fromJson(el, HomeEntity::class.java) }.getOrNull()
                ?.let { out.add(FeedRow.Feed(it)) }
            return
        }
        if (depth < 3) {
            val inner = obj.getAsJsonArray("entities")
            if (inner != null && inner.size() > 0) {
                inner.forEach { emit(it, depth + 1) }
                return
            }
        }
        val title = jsonString(el, "title") ?: return
        if (title.isBlank()) return
        val sub = jsonString(el, "description") ?: jsonString(el, "subTitle")
        out.add(FeedRow.Entry(title, sub))
    }

    data.forEach { emit(it, 0) }
    return out
}

/**
 * 动态卡片 —— 对齐 Android 端 `res/layout/item_home_feed.xml`。
 *
 * 实测关键数值（来自该 XML 与 `AppAdapter.FeedViewHolder`）：
 * ```
 * 外层 MaterialCardView: cardBackgroundColor=@color/home_card_background_color
 *                        (= <selector> ?attr/colorSurfaceContainer). cardElevation=0dp, strokeWidth=0dp
 *                        内容 ConstraintLayout padding=10dp
 * uname      textSize=15sp singleLine ellipsize=end      marginStart/End=10dp
 * from/device textSize=12sp  textColor=@android:color/darker_gray   marginStart=10dp / 5dp
 * 私有角标    textSize=11sp  padding 5/1/5/1  bg_private_badge(solid #22808080, radius 4dp)
 * messageTitle textSize=16sp bold lineSpacingMultiplier=1.3
 * message    textSize=16sp lineSpacingMultiplier=1.2 maxLines=5 ellipsize=end  marginTop=10dp
 * messageMore「查看更多」13sp bold colorPrimary  marginTop=6dp  默认 gone（不占位）
 * hotReply   9宫格等，见下阶段
 * pubDate    textColor=darker_gray（未写 textSize → 主题默认）
 * 底部动作    只有 2 个：reply(commentnum) + like(likenum)
 *            like end=parent；reply end_toStartOf=like；两者 baseline 对齐 pubDate
 * ```
 * 折叠判定（`AppAdapter.FeedViewHolder`）：`layout.lineCount > 5` 或末行有省略号 → 显示「查看更多」。
 */
@Composable
private fun FeedCard(e: HomeEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        // 卡片底 = colorSurfaceContainer（与页面底 surface 刻意拉开一档）
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            // ── 用户名 + 时间 + 机型 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.username ?: "—",
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                e.dateline?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        fmtTime(it),
                        fontSize = 12.sp,
                        color = DARKER_GRAY,
                        maxLines = 1,
                    )
                }
                val dev = e.deviceTitle ?: e.deviceName
                if (!dev.isNullOrBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        dev,
                        fontSize = 12.sp,
                        color = DARKER_GRAY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── 标题（messageTitle）：16sp bold，行距 1.3 ──
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

            // ── 正文（message）：16sp，行距 1.2，最多 5 行 ──
            // message 含行内 HTML（如 <a class="feed-link-tag" href="/t/…">#话题#</a>），
            // 必须解析后渲染，否则会露出标签源码。
            val runs = HtmlText.parse(e.message)
            var truncated by remember { mutableStateOf(false) }
            if (runs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = runs.toAnnotated(),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,   // 16sp × 1.2
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { r -> truncated = r.hasVisualOverflow || r.lineCount > 5 },
                )
            }

            // ── 「查看更多」：13sp bold colorPrimary，仅被截断时显示（默认不占位）──
            if (truncated) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "查看更多",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (!e.pic.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "[含图片]",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // ── 底部：时间（左） + 评论 / 点赞（右）──
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.dateline?.let { fmtTime(it) } ?: "",
                    fontSize = 12.sp,
                    color = DARKER_GRAY,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "评论 ${e.replynum ?: e.replynum2 ?: 0}",
                    fontSize = 12.sp,
                    color = DARKER_GRAY,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "赞 ${e.likenum ?: 0}",
                    fontSize = 12.sp,
                    color = DARKER_GRAY,
                )
            }
        }
    }
}

/** 卡片入口（image_1 / iconLink / product / topic 等） */
@Composable
private fun EntryCard(title: String, subtitle: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(5.dp))
                Text(
                    HtmlText.toPlainText(it),
                    fontSize = 13.sp,
                    color = DARKER_GRAY,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 正文次要文字色。Android 端全仓库**不用** `textColorSecondary`，
 * 而是一律用 `@android:color/darker_gray`（= `#444444`）—— 见各 item xml。
 */
private val DARKER_GRAY = androidx.compose.ui.graphics.Color(0xFF444444)

/** 文本片段 → 带样式 AnnotatedString：链接用主色 */
@Composable
private fun List<HtmlText.TextRun>.toAnnotated(): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        this@toAnnotated.forEach { run ->
            if (run.href != null) {
                withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                    append(run.text)
                }
            } else {
                append(run.text)
            }
        }
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun fmtTime(epochSeconds: Long): String = timeFmt.format(Date(epochSeconds * 1000L))
