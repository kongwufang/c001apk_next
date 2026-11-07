package com.example.c001apk.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * AppLog 的文件 sink（2026-11-08 B 步）
 *
 * 照着 [HttpLog] 那套已验证过的做法写：
 * · 单线程写盘、守护线程 —— 绝不阻塞业务线程
 * · 有界队列（[QUEUE] 条）—— 满了就丢，并计数 [dropped]（宁可丢日志也不能拖慢 App）
 * · 按天分文件 + 单文件 [MAX_FILE_BYTES] 上限自动滚动
 * · 只保留 [PrefManager.logFileDays] 天
 * · **敏感字段脱敏**：和 HttpLog 用同一份关键词表（token/cookie/ssaid/uid…）
 *   日志是给用户导出、可能上传的，泄露登录态就麻烦了
 *
 * 用法：`MyApplication` 里 `LogFileSink.init(this)` 一次，
 * 然后 `AppLog.fileSink = LogFileSink::append`（开关打开时才挂，见 [install]）。
 */
object LogFileSink {

    /** 单文件上限：2 MB（和 HttpLog 一致） */
    const val MAX_FILE_BYTES = 2L * 1024 * 1024

    /** 队列上限：满了丢，不阻塞 */
    private const val QUEUE = 2000

    /** 敏感关键词（与 HttpLog.SENSITIVE 同源，那边是 private，这里显式复制一份并注释提醒同步） */
    private val SENSITIVE = listOf(
        "token", "cookie", "authorization", "password", "passwd", "secret",
        "session", "ssaid", "uid", "username", "sig", "signature",
        "x-app-token", "x-client-ssaid", "x-app-userrandomid",
    )

    private val TIME = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val dropped = AtomicLong(0)

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE),
        { r -> Thread(r, "app-log").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy() // 满了直接丢，DiscardPolicy 不抛异常 ✓
    )
    // ⚠️ 2026-11-08 真机实测崩溃：ThreadPoolExecutor.allowCoreThreadTimeOut(true) 要求
    //   keepAliveTime > 0，这里传的是 0L ⇒ 直接抛 IllegalArgumentException
    //   "Core threads must have nonzero keep alive times"，App 一启动就挂 ✗
    //   日志线程常驻完全可接受（一个空闲守护线程）⇒ 干脆不调它 ✓

    @Volatile
    private var dir: File? = null

    /** 初始化（`MyApplication.onCreate` 里调一次；重复调用无副作用） */
    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "app_logs").apply { if (!exists()) mkdirs() }
    }

    /** 挂到 [AppLog]（开关打开时才挂；关闭时置空，一点开销都不留） */
    fun install(enabled: Boolean) {
        AppLog.fileSink = if (enabled) ::append else null
    }

    /** 追加一条（由 AppLog 调用，已在内存环里存过，这里只负责落盘） */
    fun append(entry: AppLog.Entry) {
        val d = dir ?: return
        val line = try {
            redact(entry.format())
        } catch (e: Throwable) {
            return
        }
        runCatching {
            executor.execute {
                runCatching {
                    val now = Date(entry.time)
                    pickFile(d, now).appendText(line + "\n")
                    // 1/50 概率清理过期文件（和 HttpLog 同思路：不每次都扫目录）
                    if (System.currentTimeMillis() % 50 == 0L) prune(d)
                }.onFailure { dropped.incrementAndGet() }
            }
        }.onFailure { dropped.incrementAndGet() }
    }

    /** 当前落盘文件（导出 / 查看用） */
    fun files(): List<File> =
        dir?.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedBy { it.name } ?: emptyList()

    /** 最近 N 行（查看页面用；从新到旧拼） */
    fun tail(maxLines: Int = 300): List<String> {
        val all = files()
        if (all.isEmpty()) return emptyList()
        val out = ArrayList<String>(maxLines)
        for (f in all.asReversed()) {
            val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
            for (i in lines.indices.reversed()) {
                if (out.size >= maxLines) break
                out.add(lines[i])
            }
            if (out.size >= maxLines) break
        }
        return out.reversed()
    }

    /**
     * 打包成 zip（导出用）
     *
     * 落到 `cacheDir/export`（cache 目录不用申请权限；导出时由 FileProvider 分享）
     */
    fun export(context: Context): File? {
        val all = files()
        if (all.isEmpty()) return null
        val outDir = File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }
        // 先清掉上一次的导出，避免越堆越多
        outDir.listFiles()?.forEach { runCatching { it.delete() } }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zip = File(outDir, "app-logs-$stamp.zip")
        return runCatching {
            ZipOutputStream(zip.outputStream().buffered()).use { zos ->
                all.forEach { f ->
                    zos.putNextEntry(ZipEntry(f.name))
                    f.inputStream().buffered().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                // 附一份设备概要，省得再问用户机型
                zos.putNextEntry(ZipEntry("device.txt"))
                zos.write(deviceInfo().toByteArray())
                zos.closeEntry()
            }
            zip
        }.getOrNull()
    }

    /** 清空全部日志文件 */
    fun clear() {
        dir?.listFiles()?.forEach { runCatching { it.delete() } }
        AppLog.clear()
    }

    /** 被丢弃的条数（诊断包里带上，方便判断日志是否缺片） */
    fun droppedCount(): Long = dropped.get()

    private fun deviceInfo(): String = buildString {
        appendLine("brand=" + android.os.Build.BRAND)
        appendLine("model=" + android.os.Build.MODEL)
        appendLine("sdk=" + android.os.Build.VERSION.SDK_INT)
        appendLine("app=" + runCatching { PrefManager.VERSION_NAME }.getOrDefault(""))
        appendLine("uid=" + runCatching { PrefManager.uid }.getOrDefault(""))
        appendLine("logLevel=" + runCatching { PrefManager.logLevel }.getOrDefault(""))
        appendLine("modules=" + runCatching { PrefManager.logModules }.getOrDefault(emptySet()).joinToString(","))
        appendLine("dropped=" + droppedCount())
    }

    /** 关键词脱敏：`"token":"abc"` / `token=abc` 这类一律打码 */
    private fun redact(text: String): String {
        var s = text
        SENSITIVE.forEach { key ->
            // 形如  key": "value"   /  key=value   /  key: value
            s = Regex(
                "(?i)(${Regex.escape(key)}\"?\\s*[:=]\\s*\"?)([^\"&,\\s}]{2,})"
            ).replace(s) { m -> m.groupValues[1] + "***" }
        }
        return s
    }

    private fun pickFile(d: File, now: Date): File {
        val ymd = SimpleDateFormat("yyyyMMdd", Locale.US).format(now)
        var i = 1
        while (true) {
            val f = File(d, if (i == 1) "$ymd.log" else "$ymd-$i.log")
            if (!f.exists()) return f
            if (f.length() < MAX_FILE_BYTES) return f
            i++
            if (i > 99) return f
        }
    }

    private fun prune(d: File) {
        val days = runCatching { PrefManager.logFileDays }.getOrDefault(3).coerceIn(1, 30)
        val cutoff = System.currentTimeMillis() - days * 24L * 3600 * 1000
        d.listFiles()?.forEach { f -> if (f.lastModified() < cutoff) runCatching { f.delete() } }
    }
}
