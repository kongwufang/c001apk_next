package com.example.c001apk.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.webkit.WebSettings
import com.example.c001apk.BuildConfig
import com.example.c001apk.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 数字联盟 ID（DUID）获取接口客户端。
 *
 *   GET https://service.houlangs.cn/c001apk/szlmid/request_api.php
 *   X-App-reallyUA:   app 真实 UA
 *   X-App-deviceinfo: 本机真实机型参数 + 安卓唯一标识 SSAID
 *   X-App-info:       本应用真实版本信息
 *
 * 服务端按 `X-App-deviceinfo` 的**原文**做缓存键（永不过期、命中缓存不计限流），
 * 所以三段内容必须稳定：同一台机器每次生成要逐字节一致，不能掺时间戳 / 随机数 /
 * 用户可改的伪装值。见各私有函数注释。
 *
 * 与酷安 API 隔离：独立 [OkHttpClient]，不挂 Cookie / 签名 / 日志拦截器。
 */
object SzlmIdApi {

    const val ENDPOINT = "https://service.houlangs.cn/c001apk/szlmid/request_api.php"

    /** 三个必填业务参数，服务端规定走请求头（缺一即 400） */
    const val HEADER_REAL_UA = "X-App-reallyUA"
    const val HEADER_DEVICE_INFO = "X-App-deviceinfo"
    const val HEADER_APP_INFO = "X-App-info"
    private const val HEADER_USER_AGENT = "User-Agent"

    data class Result(val duid: String, val fromCache: Boolean)

    /** 独立客户端；只在网络传输调试模式下放开 SSL 校验 */
    private val client by lazy {
        if (PrefManager.isSslDebug) SslVerify.applyDebug(OkHttpClient.Builder()).build()
        else OkHttpClient()
    }

    /** 取一个 DUID；失败抛异常，由调用方提示 */
    suspend fun fetch(): Result {
        val ctx = MyApplication.context
        // WebView 默认 UA 必须在主线程取，先拿到再进 IO
        val ua = withContext(Dispatchers.Main) { realUserAgent(ctx) }

        val body = withContext(Dispatchers.IO) {
            val deviceInfo = deviceInfoJson(ctx)
            val appInfo = appInfoJson(ctx)
            val request = Request.Builder()
                .url(ENDPOINT)
                .header(HEADER_REAL_UA, ua)
                .header(HEADER_DEVICE_INFO, deviceInfo)
                .header(HEADER_APP_INFO, appInfo)
                // 显式覆盖：否则 OkHttp 自带 `User-Agent: okhttp/4.x`，那不是 app 真实 UA
                .header(HEADER_USER_AGENT, ua)
                .get()
                .build()
            client.newCall(request).execute().use { resp -> resp.body?.string().orEmpty() }
        }

        val json = runCatching { JSONObject(body) }.getOrElse {
            error("响应不是 JSON：${body.take(120)}")
        }
        if (json.optInt("code", -1) != 0) {
            error("${json.optInt("code")}: ${json.optString("message").ifBlank { "获取失败" }}")
        }
        return Result(
            duid = json.getJSONObject("data").getString("duid"),
            fromCache = json.optString("state") == "cache",
        )
    }

    /**
     * `X-App-deviceinfo`：**本机真实**机型参数 + SSAID。
     *
     * 刻意绕开 [PrefManager.MANUFACTURER] / [PrefManager.MODEL] 等字段：那些会被
     * 「设置 - 机型参数」改写成伪装值（甚至随机值），而服务端拿这个 JSON 当缓存键，
     * 一旦被改就会每次换一个新 DUID、白耗取号额度。这里直接读 [Build]
     * （走 [TokenDeviceUtils.detectRealDevice]），再加 SSAID，保证原文稳定。
     *
     * SSAID（`Settings.Secure.ANDROID_ID`）只用于让服务端唯一认出这台设备。
     */
    private fun deviceInfoJson(ctx: Context): String {
        val d = TokenDeviceUtils.detectRealDevice()
        val ssaid = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        // 手拼而非 JSONObject：服务端按原文比对，键顺序必须固定
        return buildString {
            append('{')
            append("\"manufacturer\":").append(JSONObject.quote(d.manufacturer)).append(',')
            append("\"brand\":").append(JSONObject.quote(d.brand)).append(',')
            append("\"model\":").append(JSONObject.quote(d.model)).append(',')
            append("\"build\":").append(JSONObject.quote(d.buildNumber)).append(',')
            append("\"android\":").append(JSONObject.quote(d.androidVersion)).append(',')
            append("\"sdk\":").append(JSONObject.quote(d.sdkInt)).append(',')
            append("\"ssaid\":").append(JSONObject.quote(ssaid))
            append('}')
        }
    }

    /**
     * `X-App-info`：本应用**真实**版本信息（服务端只记日志，不参与业务）。
     *
     * 用 [BuildConfig] 而非 [PrefManager.VERSION_NAME] —— 后者可能被伪装成酷安版本号，
     * 这里要的是这个客户端自己的版本。
     */
    private fun appInfoJson(ctx: Context): String = buildString {
        append('{')
        append("\"ver\":").append(JSONObject.quote(BuildConfig.VERSION_NAME)).append(',')
        append("\"code\":").append(BuildConfig.VERSION_CODE).append(',')
        append("\"pkg\":").append(JSONObject.quote(ctx.packageName)).append(',')
        append("\"debug\":").append(BuildConfig.DEBUG)
        append('}')
    }

    /**
     * app 真实 UA：取系统 WebView 默认 UA（`Mozilla/5.0 (Linux; Android …) Chrome/…`）。
     *
     * 明确不用两种假 UA：OkHttp 自动附加的 `okhttp/4.x`，以及给酷安接口伪装用的
     * `Dalvik/… +CoolMarket/…`（[Constants.USER_AGENT]）。
     * 主线程/WebView 不可用时回落到自报名格式，宁可信息少也不给假 UA。
     */
    private fun realUserAgent(ctx: Context): String =
        runCatching { WebSettings.getDefaultUserAgent(ctx) }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "c001apk_next/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
}
