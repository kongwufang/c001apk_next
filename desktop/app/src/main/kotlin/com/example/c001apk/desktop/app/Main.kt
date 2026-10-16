package com.example.c001apk.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.c001apk.desktop.core.model.HomeCard
import com.example.c001apk.desktop.core.model.HomeEntity
import com.example.c001apk.desktop.core.net.ApiException
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.Session
import com.example.c001apk.desktop.core.net.executeSync
import com.example.c001apk.desktop.core.security.Integrity
import com.example.c001apk.desktop.core.security.IntegrityPublicKey
import com.example.c001apk.desktop.core.text.HtmlText
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JOptionPane

/**
 * 桌面端入口。
 *
 * 启动顺序：
 *   1. **完整性自校验**（[Integrity]）—— 安装目录文件是否被篡改，失败则弹窗退出；
 *   2. 打开主窗口。
 *
 * UI 结构对齐 Android 端「横屏布局」（`app/src/main/res/layout-land/activity_main.xml`）：
 * ```
 *   横屏（本实现）                 竖屏（Android 端 layout/）
 *   ┌────┬──────────────────┐      ┌──────────────────┐
 *   │Nav │                  │      │                  │
 *   │Rail│   内容区          │      │    内容区         │
 *   │(左)│                  │      ├──────────────────┤
 *   └────┴──────────────────┘      │  BottomNavigation│
 *                                  └──────────────────┘
 * ```
 */
fun main(args: Array<String>) {
    println(
        "[startup] renderApi=${System.getProperty("skiko.renderApi") ?: "(default)"}" +
            " hwAccel=${System.getProperty("skiko.awt.hardwareAcceleration") ?: "(default)"}" +
            " env=${System.getenv("SKIKO_RENDER_API") ?: "-"}" +
            " java=${System.getProperty("java.version")}" +
            " os=${System.getProperty("os.name")}"
    )
    checkIntegrityOrExit(args)

    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "c001apk desktop",
        ) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                App()
            }
        }
    }
}

/** 启动时的完整性自校验；开发构建（公钥为空）自动跳过。 */
private fun checkIntegrityOrExit(args: Array<String>) {
    val appDir = resolveAppDir() ?: return
    val result = runCatching {
        Integrity.verify(appDir, IntegrityPublicKey.BASE64, args.toList())
    }.getOrElse {
        Integrity.Result(ok = false, reason = "校验过程异常：${it::class.simpleName}: ${it.message}")
    }

    when {
        result.skipped -> println("[integrity] 已跳过：${result.reason}")
        result.ok -> println("[integrity] 通过，已校验 ${result.checked} 个文件（$appDir）")
        else -> {
            val detail = buildString {
                appendLine("程序文件完整性校验未通过，为保护账号安全已停止启动。")
                appendLine()
                appendLine("原因：${result.reason}")
                result.mismatch.take(10).forEach { appendLine("  已篡改：$it") }
                result.missing.take(10).forEach { appendLine("  缺失：$it") }
                if (result.mismatch.size + result.missing.size > 20) appendLine("  …")
                appendLine()
                appendLine("请从官方渠道重新下载安装。")
            }
            System.err.println("[integrity] 校验失败：$detail")
            runCatching {
                JOptionPane.showMessageDialog(
                    null, detail, "c001apk desktop — 完整性校验失败", JOptionPane.ERROR_MESSAGE,
                )
            }
            kotlin.system.exitProcess(2)
        }
    }
}

/**
 * 推断安装根目录（含 `app/` 与 `runtime/` 两层）。
 *
 * 顺序很重要：
 *  1. `jpackage.app-path` —— launcher 会设置，最可靠；
 *  2. **本类所在 jar 的位置** —— 便携版是 `…/app/app-xxx.jar`，其父目录的父目录即根目录。
 *     这一条是必需的：从命令行/快捷方式启动时**工作目录不可靠**，
 *     早期版本回退到 `Paths.get("")`，导致明明有 `integrity.sig` 却报「未找到」（已实测踩坑）；
 *  3. 最后才退回工作目录。
 */
private fun resolveAppDir(): Path? {
    // ① jpackage launcher 提供的路径：<root>/app/<name>.exe
    System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let { appPath ->
        runCatching { Paths.get(appPath).toAbsolutePath().normalize() }.getOrNull()
            ?.parent?.parent?.let { return it }
    }

    // ② 从自身 jar 推断：…/app/<jar> → 上一层是 app/，再上一层是安装根目录
    runCatching {
        val src = object {}.javaClass.protectionDomain?.codeSource?.location
        if (src != null) {
            val p = Paths.get(src.toURI()).toAbsolutePath().normalize()
            // p 可能是 jar 文件，也可能是 classes 目录（开发期）
            val parent = if (p.toString().endsWith(".jar")) p.parent else p
            if (parent?.fileName?.toString() == "app") return parent.parent
        }
    }

    // ③ 兜底
    return runCatching { Paths.get("").toAbsolutePath() }.getOrNull()
}

/** 导航项（对应 Android 端 `res/menu/nav_menu.xml`） */
private enum class NavItem(val label: String, val icon: ImageVector) {
    Home("头条", Icons.Filled.Home),
    Discover("发现", Icons.Filled.Search),
    Message("消息", Icons.Filled.Notifications),
    Collection("收藏", Icons.Filled.Star),
    Mine("我的", Icons.Filled.AccountCircle),
}

@Composable
private fun App() {
    var selected by remember { mutableStateOf(NavItem.Home) }
    val session = remember { Session() }
    val client = remember { Client(session = session) }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Spacer(Modifier.height(12.dp))
            NavItem.entries.forEach { item ->
                NavigationRailItem(
                    selected = selected == item,
                    onClick = { selected = item },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize()) {
            when (selected) {
                NavItem.Home -> HomeScreen(client)
                else -> Placeholder(selected)
            }
        }
    }
}

@Composable
private fun Placeholder(item: NavItem) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.label, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "该板块待实现",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────── 首页 ───────────────────────────

/**
 * 列表行：把「卡片信封」拍平。
 *
 * 实测 `/v6/main/indexV8` 的 `data` 是**卡片列表**（`entityType=card`），
 * 真实内容在 `entities` 里（feed / 图文 / 商品…）。统一转成两种可渲染行。
 */
private sealed interface Row {
    data class Feed(val e: HomeEntity) : Row
    data class Card(val title: String, val subtitle: String?, val hasPic: Boolean) : Row
}

private val gson = Gson()

private fun jsonString(el: JsonElement?, key: String): String? {
    val o = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val v = o.get(key) ?: return null
    return if (v.isJsonPrimitive) v.asString else null
}

/** 把卡片数组拍平成可渲染行，保留顺序 */
private fun flatten(cards: List<HomeCard>): List<Row> {
    val out = mutableListOf<Row>()
    for (card in cards) {
        val entities: List<JsonElement> = card.entities.orEmpty()
        if (entities.isEmpty()) {
            val t = card.title.orEmpty()
            if (t.isNotBlank()) {
                out.add(Row.Card(t, null, !card.pic.isNullOrBlank()))
            }
            continue
        }
        for (el in entities) {
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val type = obj.get("entityType")?.let { if (it.isJsonPrimitive) it.asString else null }
            if (type == "feed" || obj.has("message")) {
                val e = runCatching { gson.fromJson(el, HomeEntity::class.java) }.getOrNull()
                if (e != null) out.add(Row.Feed(e))
            } else {
                val title = jsonString(el, "title")
                val sub = jsonString(el, "description") ?: jsonString(el, "subTitle")
                val pic = jsonString(el, "pic")
                if (!title.isNullOrBlank() || !sub.isNullOrBlank()) {
                    out.add(Row.Card(title.orEmpty(), sub, !pic.isNullOrBlank()))
                }
            }
        }
    }
    return out
}

@Composable
private fun HomeScreen(client: Client) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<Row>>(emptyList()) }
    var cardCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
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
            cardCount = it.size
            rows = flatten(it)
            loading = false
        }.onFailure {
            error = "${it::class.simpleName}: ${it.message}"
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏：文本显式限宽 + 省略，避免超长撑出横向滚动条
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "头条",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "桌面端 · ${cardCount} 张卡片 / ${rows.size} 条内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text("请求失败", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在请求首页…", style = MaterialTheme.typography.bodySmall)
                }
            }

            else -> LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
                items(rows) { row ->
                    when (row) {
                        is Row.Feed -> FeedItem(row.e)
                        is Row.Card -> CardItem(row)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 动态条目 */
@Composable
private fun FeedItem(e: HomeEntity) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                e.username ?: "—",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            e.dateline?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    fmtTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val dev = e.deviceTitle ?: e.deviceName
            if (!dev.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "来自 $dev",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ★ 正文：message 内含行内 HTML，必须解析后渲染，否则会显示标签源码
        val runs = HtmlText.parse(e.message)
        if (runs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = runs.toAnnotated(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!e.pic.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "[含图片]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "赞 ${e.likenum ?: 0}    评论 ${e.replynum ?: e.replynum2 ?: 0}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 非动态卡片（专题入口等） */
@Composable
private fun CardItem(row: Row.Card) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            row.title.ifBlank { "(无标题卡片)" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        row.subtitle?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                HtmlText.toPlainText(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

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

private fun fmtTime(epochSeconds: Long): String =
    timeFmt.format(Date(epochSeconds * 1000L))
