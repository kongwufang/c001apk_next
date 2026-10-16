package com.example.c001apk.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.c001apk.desktop.app.theme.C001apkTheme
import com.example.c001apk.desktop.app.theme.ThemeState
import com.example.c001apk.desktop.app.ui.MainShell
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.Session
import com.example.c001apk.desktop.core.security.Integrity
import com.example.c001apk.desktop.core.security.IntegrityPublicKey
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JOptionPane

/**
 * 桌面端入口。
 *
 * 启动顺序：
 *   1. 打印启动环境摘要；
 *   2. 安装崩溃日志钩子；
 *   3. **完整性自校验**（[Integrity]）—— 安装目录文件是否被篡改，失败则弹窗退出；
 *   4. 打开主窗口。
 *
 * 复刻依据：Android 端 `res/layout-land/activity_main.xml`（横屏）。
 * 桌面窗口天然是宽的 → 始终使用横屏那套结构（左侧 `NavigationRailView` + 右侧 `ViewPager2`）。
 */
fun main(args: Array<String>) {
    println(
        "[startup] renderApi=${System.getProperty("skiko.renderApi") ?: "(default)"}" +
            " hwAccel=${System.getProperty("skiko.awt.hardwareAcceleration") ?: "(default)"}" +
            " headless=${System.getProperty("java.awt.headless") ?: "(unset)"}" +
            " java=${System.getProperty("java.version")}" +
            " os=${System.getProperty("os.name")}"
    )

    installCrashLogger()

    if (args.contains("--diag-window")) {
        diagnoseWindow()
        return
    }
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

private fun runUi() {
    application {
        val windowState = rememberWindowState(
            // Android 横屏布局：导航栏在左、内容占满剩余宽度。
            // 窗口默认 1280x800（16:10），是横屏比例。
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "c001apk desktop",
        ) {
            var theme by remember { mutableStateOf(ThemeState()) }
            val session = remember { Session() }
            val client = remember { Client(session = session) }

            C001apkTheme(theme) {
                MainShell(
                    client = client,
                    session = session,
                    theme = theme,
                    onThemeChange = { theme = it },
                )
            }
        }
    }
}

// ─────────────────────────── 诊断 / 日志 ───────────────────────────

/** 日志文件位置：优先安装目录，其次用户目录/临时目录（安装目录可能不可写） */
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

private fun reportFatal(stage: String, t: Throwable) {
    val sw = java.io.StringWriter()
    t.printStackTrace(java.io.PrintWriter(sw))
    val text = buildString {
        appendLine("=".repeat(70))
        appendLine("[${java.time.LocalDateTime.now()}] 启动失败（阶段：$stage）")
        appendLine(t::class.java.name + ": " + t.message)
        appendLine(sw.toString())
        appendLine(
            "环境：java=${System.getProperty("java.version")}" +
                " os=${System.getProperty("os.name")} ${System.getProperty("os.version")}" +
                " headless=${System.getProperty("java.awt.headless")}" +
                " renderApi=${System.getProperty("skiko.renderApi")}"
        )
        appendLine("=".repeat(70))
    }
    System.err.println(text)
    runCatching {
        logFile().appendText(text)
        println("[startup] 失败详情已写入: ${logFile().absolutePath}")
    }
    runCatching {
        JOptionPane.showMessageDialog(
            null,
            "程序启动失败：\n${t::class.java.simpleName}: ${t.message}\n\n" +
                "详情见日志：\n${logFile().absolutePath}",
            "c001apk desktop — 启动失败",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}

@Volatile
private var crashLogged = false

private fun installCrashLogger() {
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, t ->
        if (!crashLogged) {
            crashLogged = true
            reportFatal("未捕获异常@${thread.name}", t)
        }
        prev?.uncaughtException(thread, t)
    }
}

/**
 * 窗口能力自检（`--diag-window`）。排查「进程活着但窗口不显示」用。
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

    val ok = runCatching {
        var made = false
        javax.swing.SwingUtilities.invokeAndWait {
            val f = javax.swing.JFrame("c001apk diag")
            f.setSize(320, 160)
            f.setLocationRelativeTo(null)
            f.isVisible = true
            sb.appendLine("JFrame: displayable=${f.isDisplayable} showing=${f.isShowing} bounds=${f.bounds}")
            made = f.isShowing
            Thread.sleep(1200)
            f.dispose()
        }
        made
    }.getOrElse { t ->
        sb.appendLine("JFrame 创建异常：${t::class.java.name}: ${t.message}")
        false
    }
    sb.appendLine("RESULT=${if (ok) "可以创建窗口" else "无法创建窗口"}")
    println(sb.toString())
    runCatching { logFile().appendText(sb.toString() + "\n") }
}

/**
 * Compose 窗口自检（`--diag-compose`）。区分「AWT 建不出」与「只有 Compose 建不出」。
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
                    C001apkTheme { }
                }
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

// ─────────────────────────── 安装目录定位 ───────────────────────────

/**
 * 判断某个目录是不是「安装根目录」。
 *
 * 判据是**内容**而不是固定层数 —— 这是踩坑后的结论：
 * jpackage 的启动器 exe 就在根目录，jar 在 `<root>/app/` 下，
 * 早期按「exe 上两级」算会多退一层，导致明明有 `integrity.sig` 却报「未找到」。
 */
private fun looksLikeAppRoot(dir: Path?): Boolean {
    if (dir == null) return false
    val appSub = dir.resolve("app")
    if (!java.nio.file.Files.isDirectory(appSub)) return false
    return runCatching {
        java.nio.file.Files.list(appSub).use { st ->
            st.anyMatch { it.fileName.toString().endsWith(".jar") }
        }
    }.getOrDefault(false)
}

private fun resolveAppDir(): Path? {
    val starts = mutableListOf<Path>()

    runCatching {
        object {}.javaClass.protectionDomain?.codeSource?.location?.let { src ->
            val p = Paths.get(src.toURI()).toAbsolutePath().normalize()
            starts.add(if (p.toString().endsWith(".jar")) p.parent else p)
        }
    }
    System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let {
        runCatching { Paths.get(it).toAbsolutePath().normalize().parent }.getOrNull()
            ?.let { d -> starts.add(d) }
    }
    runCatching { Paths.get("").toAbsolutePath().normalize() }.getOrNull()?.let { starts.add(it) }

    for (start in starts) {
        var d: Path? = start
        var hops = 0
        while (d != null && hops <= 4) {
            if (looksLikeAppRoot(d)) return d
            d = d.parent
            hops++
        }
    }
    return starts.firstOrNull()
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
