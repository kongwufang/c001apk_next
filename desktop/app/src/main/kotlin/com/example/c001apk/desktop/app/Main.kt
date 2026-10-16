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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.c001apk.desktop.core.model.HomeEntity
import com.example.c001apk.desktop.core.net.ApiException
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.Session
import com.example.c001apk.desktop.core.net.executeSync
import com.example.c001apk.desktop.core.security.Integrity
import com.example.c001apk.desktop.core.security.IntegrityPublicKey
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
 *   1. **完整性自校验**（[Integrity]）—— 校验安装目录文件是否被篡改，失败则弹窗并退出；
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
 * 桌面窗口天然是宽的 → 直接用横屏那套结构（左侧竖向导航 + 内容区），不需要做双栏。
 *
 * 当前进度：骨架 + 「首页头条」已接真实接口（走 core 协议层）。其余板块为占位。
 */
fun main(args: Array<String>) {
    checkIntegrityOrExit(args)

    application {
        val windowState = rememberWindowState(
            // 横屏比例；1280x800 在 1080p 屏上居中且不顶满
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

/**
 * 启动时的完整性自校验。
 *
 * * 开发构建（仓库里的公钥是空串）→ 自动跳过，不阻塞调试；
 * * 发行构建 → 校验全部文件；不一致则提示并退出进程；
 * * 应急 → 命令行 `--skip-integrity-check` 可强制跳过。
 */
private fun checkIntegrityOrExit(args: Array<String>) {
    val appDir = resolveAppDir() ?: return
    val result = runCatching {
        Integrity.verify(appDir, IntegrityPublicKey.BASE64, args.toList())
    }.getOrElse {
        Integrity.Result(ok = false, reason = "校验过程异常：${it::class.simpleName}: ${it.message}")
    }

    when {
        result.skipped -> {
            println("[integrity] 已跳过：${result.reason}")
        }

        result.ok -> {
            println("[integrity] 通过，已校验 ${result.checked} 个文件（目录：$appDir）")
        }

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
                    null, detail, "c001apk desktop — 完整性校验失败",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
            kotlin.system.exitProcess(2)
        }
    }
}

/**
 * 推断安装根目录（含 `app/` 与 `runtime/` 两层）。
 *
 * 优先用 `jpackage.app-path`（launcher 会设置这个系统属性）；取不到时退回工作目录。
 */
private fun resolveAppDir(): Path? {
    val appPath = System.getProperty("jpackage.app-path")
    if (!appPath.isNullOrBlank()) {
        // jpackage.app-path 指向 app/<name>.exe，安装根目录是它的上两级
        val p = runCatching { Paths.get(appPath).toAbsolutePath().normalize() }.getOrNull()
        val root = p?.parent?.parent
        if (root != null) return root
    }
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
        // ★ 左侧竖向导航栏 —— 对应 layout-land 里的 NavigationRailView
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

/** 首页头条：真实调用 `/v6/main/indexV8`，验证 core 协议层在桌面端可用 */
@Composable
private fun HomeScreen(client: Client) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<HomeEntity>>(emptyList()) }

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
            items = it
            loading = false
        }.onFailure {
            error = "${it::class.simpleName}: ${it.message}"
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("头条", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text(
                    "协议层联调 · desktop skeleton",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在请求首页…", style = MaterialTheme.typography.bodySmall)
                }
            }

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

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items) { item -> FeedRow(item) }
            }
        }
    }
}

/** 单条动态（对齐 Android 端条目：昵称 + 时间 + 机型 + 正文 + 互动数） */
@Composable
private fun FeedRow(e: HomeEntity) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.username ?: "—",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            e.dateline?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    fmtTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            e.deviceTitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    "来自 $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val text = (e.message ?: e.title ?: "").trim()
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "赞 ${e.likenum ?: 0}    评论 ${e.replynum ?: 0}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun fmtTime(epochSeconds: Long): String =
    timeFmt.format(Date(epochSeconds * 1000L))
