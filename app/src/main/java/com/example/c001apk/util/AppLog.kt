package com.example.c001apk.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局分模块日志门面（2026-11-08）
 *
 * ## 为什么要有它
 * 原来 85 处 `android.util.Log.x(TAG, "...")`：
 * · tag 随手写，没法按模块筛 ✗
 * · 关日志要改代码重打包 ✗
 * · 字符串**无论开关都会拼**（`"…${it.url}"` 这种在热路径上很贵）✗
 *
 * ## 设计
 * · 三个 sink：
 *   ① logcat —— 始终跟着当前级别走（调试时最好用）
 *   ② 内存环形缓冲 —— [capacity] 条，常驻、极便宜；崩溃时可直接取现场（App 内诊断页用）✓
 *   ③ 文件 —— 由 [fileSink] 承接（B 步接 HttpLog 那套轮转 + 脱敏）
 * · 开关：`PrefManager.logEnabled` / `logLevel` / `logModules`（全默认关，release 只有 W/E 走 logcat）
 * · 级别关闭时**不拼字符串**：用 `() -> String` 的重载
 *
 * ## 用法
 * ```
 * AppLog.i(LogModule.NET, "ShareLinkRepo", "现下图片 ${out.size}/${urls.size} 张")
 * AppLog.w(LogModule.CACHE, "OfflineCache", e) { "上云异常 $fid" }
 * ```
 * tag 会是 `M:net/ShareLinkRepo`，logcat 里 `findstr M:net` 就能只看网络层。
 */
object AppLog {

    /** 级别，数值与 logcat 对齐（VERBOSE=2 … ERROR=6） */
    const val V = 2
    const val D = 3
    const val I = 4
    const val W = 5
    const val E = 6

    /** 内存环形缓冲容量（条）。日志只留最近这么多，够定位问题又不吃内存 */
    private const val CAPACITY = 2000

    /** 单条消息上限：日志里塞 3MB 响应体是真实踩过的坑，一律截断 */
    private const val MAX_MESSAGE = 2000

    /** tag 前缀，方便 logcat 里一眼区分 App 自己的日志 */
    private const val TAG_PREFIX = "M:"

    /** 一条日志 */
    data class Entry(
        val time: Long,
        val level: Int,
        val module: LogModule,
        val sub: String,
        val message: String,
        val throwable: Throwable? = null,
    ) {
        fun format(): String {
            val t = TIME_FORMAT.format(Date(time))
            val lv = levelName(level)
            val body = if (throwable == null) message else "$message\n${Log.getStackTraceString(throwable)}"
            return "$t $lv/${module.key}/$sub $body"
        }
    }

    private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val buffer = ArrayDeque<Entry>(CAPACITY)

    /**
     * 当前正在加载的列表/页面的标识（2026-11-08）
     *
     * 卡片日志要能回答「这张卡是哪个请求来的」⇒ 各 ViewModel 拉数据前把它设成
     * 请求 url（或 `HOME_TAB_FOLLOW/type=product` 这种短标识），卡片渲染时带上 ✓
     */
    @Volatile
    var page: String = ""

    /** 文件 sink（B 步由 HttpLog 那套实现挂进来；为空表示只走内存 + logcat） */
    @Volatile
    var fileSink: ((Entry) -> Unit)? = null

    /** 供导出/查看页面用：返回快照（拷贝，外部随便用） */
    @Synchronized
    fun snapshot(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()

    /** 当前生效的最低级别 */
    fun minLevel(): Int = PrefManager.logLevelValue

    /** 这个模块现在要不要记 */
    fun isModuleEnabled(module: LogModule): Boolean = PrefManager.isLogModuleEnabled(module.key)

    fun v(module: LogModule, sub: String, msg: String) = log(V, module, sub, msg, null)
    fun d(module: LogModule, sub: String, msg: String) = log(D, module, sub, msg, null)
    fun i(module: LogModule, sub: String, msg: String) = log(I, module, sub, msg, null)
    fun w(module: LogModule, sub: String, msg: String, tr: Throwable? = null) =
        log(W, module, sub, msg, tr)

    fun e(module: LogModule, sub: String, msg: String, tr: Throwable? = null) =
        log(E, module, sub, msg, tr)

    /** 带异常、消息用 lambda 的版本（级别关闭时连字符串都不拼） */
    fun w(module: LogModule, sub: String, tr: Throwable?, msg: () -> String) {
        if (!shouldLog(W, module)) return
        log(W, module, sub, msg(), tr)
    }

    fun e(module: LogModule, sub: String, tr: Throwable?, msg: () -> String) {
        if (!shouldLog(E, module)) return
        log(E, module, sub, msg(), tr)
    }

    fun d(module: LogModule, sub: String, msg: () -> String) {
        if (!shouldLog(D, module)) return
        log(D, module, sub, msg(), null)
    }

    fun i(module: LogModule, sub: String, msg: () -> String) {
        if (!shouldLog(I, module)) return
        log(I, module, sub, msg(), null)
    }

    /** 便宜的级别判断，给「消息本身很贵」的调用点用 */
    fun shouldLog(level: Int, module: LogModule): Boolean =
        level >= minLevel() && isModuleEnabled(module)

    private fun log(level: Int, module: LogModule, sub: String, raw: String, tr: Throwable?) {
        if (!shouldLog(level, module)) return
        val message = if (raw.length > MAX_MESSAGE) raw.take(MAX_MESSAGE) + "…(截断)" else raw
        val entry = Entry(System.currentTimeMillis(), level, module, sub, message, tr)

        // ① 内存环形缓冲：常驻，崩溃现场靠它
        synchronized(buffer) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
        }

        // ② logcat
        val tag = TAG_PREFIX + module.key + "/" + sub
        val text = if (tr == null) message else "$message\n${Log.getStackTraceString(tr)}"
        runCatching { Log.println(level, tag, text) }

        // ③ 文件 sink（B 步挂上；异常不能反过来影响业务）
        fileSink?.let { sink -> runCatching { sink(entry) } }
    }

    private fun levelName(level: Int): String = when (level) {
        V -> "V"
        D -> "D"
        I -> "I"
        W -> "W"
        E -> "E"
        else -> "?"
    }
}
