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
    // 启动环境一行摘要，便于排查（渲染后端/Java 版本是关键信息）
    val headless = System.getProperty("java.awt.headless")
    println(
        "[startup] renderApi=${System.getProperty("skiko.renderApi") ?: "(default)"}" +
            " hwAccel=${System.getProperty("skiko.awt.hardwareAcceleration") ?: "(default)"}" +
            " headless=${headless ?: "(unset)"}" +
            " java=${System.getProperty("java.version")}" +
            " os=${System.getProperty("os.name")}"
    )

    // ★ 把未捕获异常写到日志文件 —— 窗口创建失败常常是静默的，
    //   没有日志就无从定位（这个坑已踩过）。
    installCrashLogger()

    // 诊断模式：只探测「本环境能否创建窗口」，便于远程排查
    if (args.contains("--diag-window")) {
        diagnoseWindow()
        return
    }
    // 诊断模式：探测 Compose 窗口（AWT 能建但 Compose 不能建时用这个）
    if (args.contains("--diag-compose")) {
        diagnoseCompose()
        return
    }

    try {
        checkIntegrityOrExit(args)
        runUi()
    } catch (t: Throwable) {
        reportFatal("启动", t)
        kotlin.system.exitProcess(3)
    }
}

/**
 * Compose 窗口自检（`--diag-compose`）。
 *
 * 用于区分「AWT 建不出窗口」与「只有 Compose 建不出窗口」——
 * 实测本机能建 JFrame 但主界面没窗口，故需要单独测 Compose 这一层。
 */
private fun diagnoseCompose() {
    val log = StringBuilder()
    log.appendLine("=== Compose 窗口自检 ===")
    log.appendLine("skiko.renderApi=${System.getProperty("skiko.renderApi") ?: "(unset)"}")

    val t = Thread {
        try {
            application {
                val state = rememberWindowState(size = DpSize(420.dp, 240.dp))
                Window(onCloseRequest = ::exitApplication, state = state, title = "c001apk diag") {
                    MaterialTheme(colorScheme = lightColorScheme()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Compose 窗口已创建")
                        }
                    }
                }
                // 到这里说明窗口已成功建立
                log.appendLine("Compose application{} 已进入，窗口应已创建")
                println(log.toString())
                runCatching { logFile().appendText(log.toString() + "\n") }
                Thread.sleep(3000)
                exitApplication()
            }
            log.appendLine("RESULT=Compose 窗口可以创建")
        } catch (th: Throwable) {
            val sw = java.io.StringWriter()
            th.printStackTrace(java.io.PrintWriter(sw))
            log.appendLine("Compose 失败：${th::class.java.name}: ${th.message}")
            log.appendLine(sw.toString())
            log.appendLine("RESULT=Compose 窗口创建失败")
        } finally {
            println(log.toString())
            runCatching { logFile().appendText(log.toString() + "\n") }
        }
    }
    t.isDaemon = false
    t.start()
    t.join(20000)
    if (t.isAlive) {
        log.appendLine("RESULT=超时：20 秒内未能建立 Compose 窗口")
        println(log.toString())
        runCatching { logFile().appendText(log.toString() + "\n") }
    }
}

/**
 * 窗口能力自检（`--diag-window`）。
 *
 * 用于排查「进程活着但窗口不显示」：把 AWT/Compose 相关环境与
 * 「能否真的建出一个 Swing 窗口」写进日志，避免靠猜。
 */
private fun diagnoseWindow() {
    val sb = StringBuilder()
    sb.appendLine("=== 窗口能力自检 ===")
    sb.appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.version")}")
    sb.appendLine("java=${System.getProperty("java.version")} vendor=${System.getProperty("java.vendor")}")
    sb.appendLine("java.awt.headless=${System.getProperty("java.awt.headless") ?: "(unset)"}")
    sb.appendLine("user.name=${System.getProperty("user.name")} user.dir=${System.getProperty("user.dir")}")
    sb.appendLine("skiko.renderApi=${System.getProperty("skiko.renderApi") ?: "(unset)"}")

    val headless = runCatching { java.awt.GraphicsEnvironment.isHeadless() }
        .getOrElse { "异常: $it" }
    sb.appendLine("GraphicsEnvironment.isHeadless()=$headless")

    val env = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment() }.getOrNull()
    sb.appendLine("screenDevices=${runCatching { env?.screenDevices?.size }.getOrElse { "?" }}")

    // 真的建一个窗口试试
    val ok = runCatching {
        var made = false
        javax.swing.SwingUtilities.invokeAndWait {
            val f = javax.swing.JFrame("c001apk diag")
            f.setSize(320, 160)
            f.setLocationRelativeTo(null)
            f.isVisible = true
            sb.appendLine("JFrame: displayable=${f.isDisplayable} showing=${f.isShowing} size=${f.size} bounds=${f.bounds}")
            made = f.isShowing
            Thread.sleep(1500)
            f.dispose()
        }
        made
    }.getOrElse { t ->
        sb.appendLine("JFrame 创建异常：${t::class.java.name}: ${t.message}")
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        sb.appendLine(sw.toString())
        false
    }
    sb.appendLine("RESULT=${if (ok) "可以创建窗口" else "无法创建窗口"}")

    val text = sb.toString()
    println(text)
    runCatching { logFile().appendText(text + "\n") }
    println("[diag] 日志: ${logFile().absolutePath}")
}

/** 日志文件位置：优先安装目录，其次用户目录（安装目录可能不可写） */
private fun logFile(): java.io.File {
    val candidates = listOfNotNull(
        runCatching { resolveAppDir() }.getOrNull()?.resolve("c001apk-desktop.log")?.toFile(),
        java.io.File(System.getProperty("user.home"), "c001apk-desktop.log"),
        java.io.File(System.getProperty("java.io.tmpdir"), "c001apk-desktop.log"),
    )
    return candidates.firstOrNull { f ->
        runCatching {
            f.parentFile?.mkdirs()
            if (!f.exists()) f.createNewFile()
            f.canWrite()
        }.getOrDefault(false)
    } ?: candidates.last()
}

@Volatile
private var crashLogged = false

private fun reportFatal(stage: String, t: Throwable) {
    val sw = java.io.StringWriter()
    t.printStackTrace(java.io.PrintWriter(sw))
    val text = buildString {
        appendLine("=".repeat(70))
        appendLine("[${java.time.LocalDateTime.now()}] 启动失败（阶段：$stage）")
        appendLine(t::class.java.name + ": " + t.message)
        appendLine(sw.toString())
        appendLine("环境：java=${System.getProperty("java.version")}" +
            " os=${System.getProperty("os.name")} ${System.getProperty("os.version")}" +
            " headless=${System.getProperty("java.awt.headless")}" +
            " renderApi=${System.getProperty("skiko.renderApi")}")
        appendLine("=".repeat(70))
    }
    System.err.println(text)
    runCatching {
        logFile().appendText(text)
        println("[startup] 失败详情已写入: ${logFile().absolutePath}")
    }
    if (!crashLogged) {
        crashLogged = true
        runCatching {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "程序启动失败：\n${t::class.java.simpleName}: ${t.message}\n\n" +
                    "详情见日志：\n${logFile().absolutePath}",
                "c001apk desktop — 启动失败",
                javax.swing.JOptionPane.ERROR_MESSAGE,
            )
        }
    }
}

private fun installCrashLogger() {
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, t ->
        reportFatal("未捕获异常@${thread.name}", t)
        prev?.uncaughtException(thread, t)
    }
}

private fun runUi() {
    println("[startup] 进入 runUi，准备创建 Compose 窗口")
    application {
        println("[startup] application{} 已进入")
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "c001apk desktop",
        ) {
            println("[startup] Window 内容开始组合")
            MaterialTheme(colorScheme = lightColorScheme()) {
                App()
            }
        }
        println("[startup] 窗口已提交")
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
/**
 * 判断某个目录是不是「安装根目录」。
 *
 * 判据是**内容**而不是固定层数 —— 这是踩坑后的结论：
 *   * 安装根目录下应有 `app/` 子目录，里面有 jar；
 *   * `integrity.sig` 若存在则更确定。
 */
private fun looksLikeAppRoot(dir: Path?): Boolean {
    if (dir == null) return false
    val appSub = dir.resolve("app")
    if (!java.nio.file.Files.isDirectory(appSub)) return false
    val hasJar = runCatching {
        java.nio.file.Files.list(appSub).use { st ->
            st.anyMatch { it.fileName.toString().endsWith(".jar") }
        }
    }.getOrDefault(false)
    return hasJar
}

/**
 * 推断安装根目录（含 `app/` 与 `runtime/` 两层）。
 *
 * ⚠️ 不要用「固定上几级」——实测启动器 `<root>/c001apk-desktop.exe` 就在根目录，
 * 而 jar 在 `<root>/app/` 下，早期实现按「exe 的上两级」算，多退了一层，
 * 于是明明有 `integrity.sig` 却报「未找到」。
 * 改为从候选起点**逐级向上找「看起来像安装根目录」的那一层**。
 */
private fun resolveAppDir(): Path? {
    val starts = mutableListOf<Path>()

    // 自身 jar / classes 所在位置（最可靠的锚点）
    runCatching {
        object {}.javaClass.protectionDomain?.codeSource?.location?.let { src ->
            val p = Paths.get(src.toURI()).toAbsolutePath().normalize()
            starts.add(if (p.toString().endsWith(".jar")) p.parent else p)
        }
    }

    // jpackage launcher 的可执行文件位置
    System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let {
        runCatching { Paths.get(it).toAbsolutePath().normalize().parent }.getOrNull()
            ?.let { d -> starts.add(d) }
    }

    // 工作目录（兜底）
    runCatching { Paths.get("").toAbsolutePath().normalize() }.getOrNull()
        ?.let { starts.add(it) }

    for (start in starts) {
        var d: Path? = start
        var hops = 0
        while (d != null && hops <= 4) {
            if (looksLikeAppRoot(d)) return d
            d = d.parent
            hops++
        }
    }

    println("[startup] 未能定位安装根目录，锚点=${starts.map { it.toString() }}")
    return starts.firstOrNull()
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
