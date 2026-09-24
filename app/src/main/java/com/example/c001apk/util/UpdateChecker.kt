package com.example.c001apk.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.c001apk.BuildConfig
import com.example.c001apk.MyApplication
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本应用自更新检查。
 *
 * 升级信息走自建接口（顺带给服务端做匿名访问统计）：
 *
 *   GET https://service.houlangs.cn/c001apk/
 *   请求头 X-Union-Id：本机随机生成并落盘的匿名统计 ID（见 [PrefManager.updateUnionId]）
 *
 * 一次请求同时返回三段（服务端：仓库 _rev/update_files/index.php）：
 * {
 *   "code": 0, "message": "ok", "time": 1789806080,
 *   "stable": { 正式版 }, "beta": { Beta 版 }, "org": { 关于页按钮 }
 * }
 *
 * 单段 JSON 格式：
 * {
 *   "versionName": "1.0.1",
 *   "versionCode": 468,
 *   "changelog": "## 更新日志\n- xxx（markdown 文本）",
 *   "download": [
 *     {"name": "线路一", "url": "https://..."},
 *     {"name": "线路二", "url": "https://..."}
 *   ]
 * }
 *
 * versionCode 大于当前 BuildConfig.VERSION_CODE 才算有更新；
 * 下载一律跳转外部浏览器打开，不在应用内下载。
 */
object UpdateChecker {

    /** 自建更新接口；stable / beta / org 都在同一次响应里，不用分别请求 */
    const val BASE_URL = "https://service.houlangs.cn/c001apk/"

    /**
     * 关于页「组织」按钮配置（响应里的 org 段），随时可下发/改名/换链接：
     *
     *   {"buttons": [{"name": "官方群组", "url": "https://..."}]}
     * 也兼容 {"name": "...", "url": "..."} 和裸数组 [{"name": "...", "url": "..."}]
     *
     * 点击后强制跳外部浏览器打开。
     */

    /** 每个进程只做一次启动时自动检查（Activity 重建不会重复弹窗） */
    var checkedThisSession = false

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_BETA = "beta"

    data class DownloadLine(val name: String, val url: String)

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Long,
        val changelog: String,
        val lines: List<DownloadLine>,
    ) {
        val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
    }

    /**
     * 独立客户端：不挂 Cookie / 日志拦截器 —— 更新接口是自建服务，
     * 不该把酷安的登录凭证带过去。
     *
     * 也不套 [SslVerify.apply]：那条路径在「校验 SSL 证书」开启时要求证书链锚定在
     * 随包内置的 Mozilla CA 库里，万一自建服务器的证书链不合规，更新检查会在握手阶段
     * 静默失败（下面的 runCatching 会把异常吞掉，用户只看到「没有更新」），代价太大。
     * 常规校验交给 network_security_config（信任锚 = 仅内置 Mozilla CA，
     * 用户安装的抓包证书一样会被拒），这里只在「网络传输调试模式」下显式放开，
     * 否则裸 OkHttpClient 走平台默认校验，抓包证书永远过不去。
     */
    private val client by lazy {
        if (PrefManager.isSslDebug) SslVerify.applyDebug(OkHttpClient.Builder()).build()
        else OkHttpClient()
    }

    private val userAgent: String by lazy {
        val d = TokenDeviceUtils.detectRealDevice()
        "Dalvik/2.1.0 (Linux; U; Android ${d.androidVersion}; ${d.model} ${d.buildNumber}) " +
            "(#Build; ${d.brand}; ${d.model}; ${d.buildNumber}; ${d.androidVersion}) okhttp/4.12.0"
    }

    private fun installHeaders(): List<Pair<String, String>> = runCatching {
        val ctx = MyApplication.context
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        val (installer, initiator) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val src = pm.getInstallSourceInfo(pkg)
            src.installingPackageName to src.initiatingPackageName
        } else {
            @Suppress("DEPRECATION")
            val legacy = pm.getInstallerPackageName(pkg)
            legacy to null
        }
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(pkg, 0)
        listOf(
            "X-Install-Source" to installer.orEmpty(),
            "X-Install-Origin" to initiator.orEmpty(),
            "X-Install-Time" to info.firstInstallTime.toString(),
            "X-Update-Time" to info.lastUpdateTime.toString(),
            "X-Client-Version" to BuildConfig.VERSION_NAME,
            "X-Client-Code" to BuildConfig.VERSION_CODE.toString(),
        ).filter { it.second.isNotEmpty() }
    }.getOrDefault(emptyList())

    /** 自建接口一次响应里的三段 JSON（原样留着，按渠道各取所需） */
    private class Snapshot(
        val stable: String?,
        val beta: String?,
        val org: String?,
        val at: Long,
    )

    private const val CACHE_TTL = 60_000L

    /** 同一份响应 60 秒内复用：启动时先查正式版再查 Beta，只会打一次接口 */
    @Volatile
    private var cached: Snapshot? = null

    /**
     * 拉取自建接口并拆成 stable / beta / org 三段；失败返回 null
     * （调用方按「没有更新」「没有按钮」处理，不弹错误框）。
     *
     * X-Union-Id 传 [PrefManager.updateUnionId]（本机随机 32 位 hex），服务端据此统计设备数。
     */
    private suspend fun loadSnapshot(): Snapshot? = withContext(Dispatchers.IO) {
        cached?.takeIf { System.currentTimeMillis() - it.at < CACHE_TTL }
            ?.let { return@withContext it }
        runCatching {
            val builder = Request.Builder()
                .url(BASE_URL)
                .header("X-Union-Id", PrefManager.updateUnionId)
                .header("User-Agent", userAgent)
            installHeaders().forEach { (name, value) -> builder.header(name, value) }
            val request = builder.build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            } ?: return@runCatching null
            val obj = JSONObject(body)
            Snapshot(
                stable = obj.optJSONObject("stable")?.toString(),
                beta = obj.optJSONObject("beta")?.toString(),
                org = obj.optJSONObject("org")?.toString(),
                at = System.currentTimeMillis(),
            ).also { cached = it }
        }.getOrNull()
    }

    /**
     * 查某个渠道（[CHANNEL_STABLE] / [CHANNEL_BETA]）的更新信息。
     * 接口不通或该段缺失时返回 null。
     */
    suspend fun fetchUpdate(channel: String): UpdateInfo? {
        val snapshot = loadSnapshot() ?: return null
        val json = if (channel == CHANNEL_BETA) snapshot.beta else snapshot.stable
        return json?.let { parse(it) }
    }

    fun parse(json: String): UpdateInfo? = runCatching {
        val obj = JSONObject(json)
        val lines = obj.optJSONArray("download")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val u = o.optString("url")
                if (u.isBlank()) null
                else DownloadLine(o.optString("name").ifBlank { "线路${i + 1}" }, u)
            }
        }.orEmpty()
        UpdateInfo(
            versionName = obj.optString("versionName"),
            versionCode = obj.optLong("versionCode", -1L),
            changelog = obj.optString("changelog"),
            lines = lines,
        ).takeIf { it.versionCode > 0 && it.lines.isNotEmpty() }
    }.getOrNull()

    data class OrgLink(val name: String, val url: String)

    /** 关于页「组织」按钮配置；失败 / 缺失返回空表（不显示按钮） */
    suspend fun fetchOrgLinks(): List<OrgLink> {
        val json = loadSnapshot()?.org ?: return emptyList()
        return parseOrgLinks(json)
    }

    fun parseOrgLinks(json: String): List<OrgLink> = runCatching {
        val text = json.trim()
        val arr = if (text.startsWith("[")) {
            JSONArray(text)
        } else {
            val obj = JSONObject(text)
            obj.optJSONArray("buttons") ?: obj.optJSONArray("groups")
            ?: return@runCatching listOfNotNull(parseOrgLink(obj))
        }
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseOrgLink(it) } }
    }.getOrDefault(emptyList())

    private fun parseOrgLink(o: JSONObject): OrgLink? {
        val url = o.optString("url")
        if (url.isBlank()) return null
        return OrgLink(o.optString("name").ifBlank { "加入群组" }, url)
    }

    /** 一律跳外部浏览器打开（下载、反馈群组都用它） */
    fun openExternal(context: Context, url: String, failTip: String = "无法打开链接") {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, failTip, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新弹窗：展示更新日志，多线路时可选择线路后跳外部浏览器下载。
     *
     * @param channel [CHANNEL_STABLE] / [CHANNEL_BETA]，「不再提示」会关掉对应开关
     * @param onIgnored 「不再提示」后的回调（用来刷新界面上的开关状态，可空）
     */
    fun showUpdateDialog(
        context: Context,
        info: UpdateInfo,
        channel: String,
        onIgnored: (() -> Unit)? = null
    ) {
        val isBeta = channel == CHANNEL_BETA
        val scrollView = ScrollView(context)
        val textView = TextView(context).apply {
            text = info.changelog.ifBlank { "无更新日志" }
            setTextIsSelectable(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        scrollView.addView(textView)

        MaterialAlertDialogBuilder(context).apply {
            setTitle("发现新版本${if (isBeta) "（Beta）" else ""} ${info.versionName}")
            setView(scrollView)
            setNegativeButton(android.R.string.cancel, null)
            setNeutralButton("不再提示") { _, _ ->
                if (isBeta) PrefManager.isCheckUpdateBeta = false
                else PrefManager.isCheckUpdateStable = false
                onIgnored?.invoke()
            }
            setPositiveButton("下载") { _, _ ->
                if (info.lines.size == 1) {
                    openExternal(context, info.lines[0].url, "打开下载链接失败")
                } else {
                    val names = info.lines.map { it.name }.toTypedArray()
                    MaterialAlertDialogBuilder(context).apply {
                        setTitle("选择下载线路")
                        setItems(names) { d, which ->
                            d.dismiss()
                            openExternal(context, info.lines[which].url, "打开下载链接失败")
                        }
                        setNegativeButton(android.R.string.cancel, null)
                        show()
                    }
                }
            }
            show()
        }
    }
}
