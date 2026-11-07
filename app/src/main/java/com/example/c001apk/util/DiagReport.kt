package com.example.c001apk.util

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.example.c001apk.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 诊断上报（心跳 `log` 事件触发 ✓ 2026-11-07）
 *
 * ## 三层内容（用户点名要 A+B+C）
 *
 * **A 设备/环境快照** —— 不含任何标识 ✓
 * ```
 * app:    ver / code / api / sig 前 12 位 / debug / channel / installer
 * device: brand / model / build / android / sdk / abi / density / fontScale /
 *         screen / locale / timezone / isTablet / isNight
 * state:  darkTheme / followSystemAccent / themeColor / blackDarkTheme / fontScale /
 *         isLogin / uptimeSec / memUsedMB / memTotalMB
 * ```
 * ⇒ 「我这里显示不对」90% 看这四个就够：**ver + sig + model + theme** ✓
 *
 * **B 崩溃摘要** —— 最近 5 个未捕获异常的**栈顶 5 帧** ✓（不传完整堆栈 ✗）
 *
 * **C 请求日志聚合** —— **只有用户开了「记录 HTTP 请求日志」才有** ✓
 * ```
 * http: { enabled, days, count, slowest[5], failures[10] }
 * ```
 * ⇒ 只传**聚合** ✓ 不传原始日志行 ✗（原始行可能含内容 ✗ 也太大了 ✗）
 *
 * ## 明确**不传**的东西（用户原则：不是隐私收集癖）
 * ```
 * ❌ ssaid / uid / CID / MAC / IMEI / OAID（服务器本来就有 ssaid 头 ✓）
 * ❌ 手机号 / 昵称 / 头像 / 关注列表
 * ❌ Cookie / token / 密码
 * ❌ 帖子正文 / 评论 / 搜索词
 * ❌ 通讯录 / 已安装应用列表
 * ❌ 精确位置（IP 由服务器侧记 ✓ 不重复传 ✓）
 * ```
 *
 * ## 上传
 * `POST https://service.houlangs.cn/c001apk/diag/` ✓ gzip 后一般 2~5 KB ✓
 * 服务器落 `data/diag/<ssaid前2位>/<ssaid>-<时间>.json` ✓ 保留 7 天 ✓
 */
object DiagReport {

    private const val URL = "https://service.houlangs.cn/c001apk/diag/"

    /** 崩溃摘要最多几条 / 每条几帧 */
    private const val MAX_ERRORS = 5
    private const val MAX_FRAMES = 5

    // ---------------- 崩溃收集（挂在 Application 里 ✓ 只存内存 ✗ 不落盘 ✗） ----------------

    private data class Err(
        val t: String,
        val type: String,
        val msg: String,
        val top: List<String>,
        var count: Int = 1,
    )

    private val errors = ArrayList<Err>()

    /** 在 Application.onCreate 里调一次 ✓ 幂等 ✓ */
    fun installCrashCollector() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Collecting) return
        Thread.setDefaultUncaughtExceptionHandler(Collecting(prev))
    }

    private class Collecting(private val prev: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val top = e.stackTrace.take(MAX_FRAMES).map {
                    "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
                }
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                val type = e.javaClass.name
                val msg = (e.message ?: "").take(300)
                // 同样的异常只累加计数 ✓ 别把列表塞满 ✓
                val same = errors.firstOrNull { it.type == type && it.msg == msg }
                if (same != null) {
                    same.count += 1
                } else {
                    errors.add(Err(now, type, msg, top))
                    if (errors.size > MAX_ERRORS) errors.removeAt(0)
                }
            }
            prev?.uncaughtException(t, e)
        }
    }

    // ---------------- 上报 ----------------

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 组包并上传 ✓ 全静默 ✓ */
    fun upload(ctx: Context) {
        val payload = runCatching { build(ctx) }.getOrNull() ?: return
        val body = runCatching {
            val json = payload.toString()
            val gz = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            gz.toByteArray()
        }.getOrNull() ?: return

        val req = Request.Builder()
            .url(URL)
            .header("User-Agent", "c001apk-diag")
            .header(ServerIdentity.HEADER_API, ServerIdentity.API_VERSION.toString())
            .header("X-Client-SSaid", PrefManager.ssaid)
            .header("X-Client-Version", BuildConfig.VERSION_NAME)
            .header("X-Client-Code", BuildConfig.VERSION_CODE.toString())
            .header("Content-Encoding", "gzip")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        runCatching { client.newCall(req).execute().close() }
    }

    private fun build(ctx: Context): JSONObject {
        val root = JSONObject()

        // ---------- A. 设备 / 环境 / 状态 ----------
        root.put("schema", 1)
        root.put("time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))

        val app = JSONObject()
            .put("ver", BuildConfig.VERSION_NAME)
            .put("code", BuildConfig.VERSION_CODE)
            .put("api", ServerIdentity.API_VERSION)
            .put("debug", BuildConfig.DEBUG)
            .put("pkg", ctx.packageName)
        runCatching {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(ctx.packageName, 0)
            val sigs = @Suppress("DEPRECATION") pm.getPackageInfo(
                ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES
            ).signatures
            val first = sigs?.firstOrNull()
            if (first != null) {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.update(first.toByteArray())
                app.put("sig", md.digest().joinToString("") { "%02x".format(it) }.take(12))
            }
            app.put("firstInstall", info.firstInstallTime / 1000)
            app.put("lastUpdate", info.lastUpdateTime / 1000)
        }
        root.put("app", app)

        val device = JSONObject()
            .put("brand", Build.BRAND)
            .put("model", Build.MODEL)
            .put("build", Build.DISPLAY)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("abi", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("isTablet", ctx.resources.configuration.smallestScreenWidthDp >= 600)
            .put("isNight", rikka.core.util.ResourceUtils.isNightMode(ctx.resources.configuration))
        runCatching {
            val dm = ctx.resources.displayMetrics
            device.put("density", dm.densityDpi)
            device.put("fontScale", ctx.resources.configuration.fontScale)
            device.put("screen", "${dm.widthPixels}x${dm.heightPixels}")
            device.put("locale", Locale.getDefault().toString())
            device.put("timezone", java.util.TimeZone.getDefault().id)
        }
        root.put("device", device)

        val state = JSONObject()
            .put("darkTheme", PrefManager.darkTheme)
            .put("followSystemAccent", PrefManager.followSystemAccent)
            .put("themeColor", ThemeUtils.colorTheme)
            .put("blackDarkTheme", PrefManager.blackDarkTheme)
            .put("fontScalePref", PrefManager.FONTSCALE)
            .put("isLogin", PrefManager.isLogin)
            .put("uptimeSec", SystemClock.elapsedRealtime() / 1000)
        runCatching {
            val rt = Runtime.getRuntime()
            state.put("memUsedMB", (rt.totalMemory() - rt.freeMemory()) / 1048576)
            state.put("memTotalMB", rt.maxMemory() / 1048576)
        }
        root.put("state", state)

        // ---------- B. 崩溃摘要 ----------
        val arr = JSONArray()
        synchronized(errors) {
            errors.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("t", e.t)
                        .put("type", e.type)
                        .put("msg", e.msg)
                        .put("top", JSONArray(e.top))
                        .put("count", e.count)
                )
            }
        }
        root.put("errors", arr)

        // ---------- C. 请求日志聚合（只有用户开了才有） ----------
        root.put("http", httpSummary(ctx))

        return root
    }

    private fun httpSummary(ctx: Context): JSONObject {
        val out = JSONObject().put("enabled", PrefManager.recordHttpLog)
        if (!PrefManager.recordHttpLog) return out

        val dir = File(ctx.filesDir, "http_logs")
        if (!dir.isDirectory) return out
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: return out
        if (files.isEmpty()) return out

        val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
        val slow = ArrayList<Triple<String, Long, Int>>()
        val fails = HashMap<String, Int>()
        var count = 0

        files.forEach { f ->
            runCatching {
                f.forEachLine { line ->
                    if (line.length < 10) return@forEachLine
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                    val ts = o.optLong("ts", 0L) * 1000
                    if (ts < cutoff) return@forEachLine
                    count += 1
                    val url = o.optString("url")
                    val ms = o.optLong("ms", 0)
                    val st = o.optInt("status", 0)
                    if (ms > 0) {
                        slow.add(Triple(url, ms, st))
                        if (slow.size > 200) slow.sortByDescending { it.second }.let { slow.subList(60, 200).clear() }
                    }
                    val err = o.optString("err")
                    if (st == 0 || st >= 400 || err.isNotEmpty()) {
                        val key = if (st == 0) "${url.take(80)}|${err.take(20)}" else "${url.take(80)}|$st"
                        fails[key] = (fails[key] ?: 0) + 1
                    }
                }
            }
        }

        out.put("days", 1)
        out.put("count", count)

        val slowArr = JSONArray()
        slow.sortedByDescending { it.second }.take(5).forEach { (u, ms, st) ->
            slowArr.put(JSONObject().put("url", u.take(120)).put("ms", ms).put("status", st))
        }
        out.put("slowest", slowArr)

        val failArr = JSONArray()
        fails.entries.sortedByDescending { it.value }.take(10).forEach { (k, n) ->
            val parts = k.split("|")
            failArr.put(
                JSONObject()
                    .put("url", parts.getOrElse(0) { "" })
                    .put("status", parts.getOrElse(1) { "" })
                    .put("n", n)
            )
        }
        out.put("failures", failArr)
        return out
    }
}
