package com.example.c001apk.util

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.example.c001apk.MyApplication.Companion.context
import com.example.c001apk.constant.Constants

object PrefManager {

    private const val PREF_DARK_THEME = "dark_theme"
    private const val PREF_BLACK_DARK_THEME = "black_dark_theme"
    private const val PREF_FOLLOW_SYSTEM_ACCENT = "follow_system_accent"
    private const val PREF_THEME_COLOR = "theme_color"
    private const val SHOW_EMOJI = "show_emoji"
    private const val UID = "uid"
    private const val NAME = "name"
    private const val TOKEN = "token"

    private val pref = context.getSharedPreferences("settings", MODE_PRIVATE)

    var darkTheme: Int
        get() = pref.getInt(PREF_DARK_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = pref.edit().putInt(PREF_DARK_THEME, value).apply()

    var blackDarkTheme: Boolean
        get() = pref.getBoolean(PREF_BLACK_DARK_THEME, false)
        set(value) = pref.edit().putBoolean(PREF_BLACK_DARK_THEME, value).apply()

    var followSystemAccent: Boolean
        get() = pref.getBoolean(PREF_FOLLOW_SYSTEM_ACCENT, true)
        set(value) = pref.edit().putBoolean(PREF_FOLLOW_SYSTEM_ACCENT, value).apply()

    var themeColor: String
        get() = pref.getString(PREF_THEME_COLOR, "MATERIAL_DEFAULT")!!
        set(value) = pref.edit().putString(PREF_THEME_COLOR, value).apply()

    var showEmoji: Boolean
        get() = pref.getBoolean(SHOW_EMOJI, true)
        set(value) = pref.edit().putBoolean(SHOW_EMOJI, value).apply()

    var isLogin: Boolean
        get() = pref.getBoolean("isLogin", false)
        set(value) = pref.edit().putBoolean("isLogin", value).apply()

    var uid: String
        get() = pref.getString(UID, "")!!
        set(value) = pref.edit().putString(UID, value).apply()

    var username: String
        get() = pref.getString(NAME, "")!!
        set(value) = pref.edit().putString(NAME, value).apply()

    var token: String
        get() = pref.getString(TOKEN, "")!!
        set(value) = pref.edit().putString(TOKEN, value).apply()

    var userAvatar: String
        get() = pref.getString("userAvatar", "")!!
        set(value) = pref.edit().putString("userAvatar", value).apply()

    var level: String
        get() = pref.getString("level", "")!!
        set(value) = pref.edit().putString("level", value).apply()

    var experience: String
        get() = pref.getString("experience", "")!!
        set(value) = pref.edit().putString("experience", value).apply()

    var nextLevelExperience: String
        get() = pref.getString("nextLevelExperience", "")!!
        set(value) = pref.edit().putString("nextLevelExperience", value).apply()

    var xAppToken: String
        get() = pref.getString("xAppToken", "")!!
        set(value) = pref.edit().putString("xAppToken", value).apply()

    /** 设备指纹版本：低于 TokenDeviceUtils.FINGERPRINT_VERSION 时会被强制重置为默认指纹 */
    var DEVICE_FINGERPRINT_VERSION: Int
        get() = pref.getInt("DEVICE_FINGERPRINT_VERSION", 0)
        set(value) = pref.edit().putInt("DEVICE_FINGERPRINT_VERSION", value).apply()

    /**
     * 用户是否**显式**指定过自定义 X-App-Device。
     *
     * 只有「设置 - 参数 - X-App-Device」里手填/重新生成才会置位；
     * 未置位时 [com.example.c001apk.util.TokenDeviceUtils.getLastingDeviceCode] 会把设备串
     * 强制拉回官方认可的那一组（否则详情页会被风控要求验证码）。
     */
    var customFingerprint: Boolean
        get() = pref.getBoolean("customFingerprint", false)
        set(value) = pref.edit().putBoolean("customFingerprint", value).apply()

    /**
     * 是否把**本机真实机型**上报给服务器（默认开）。
     *
     * 服务端用 `X-App-Device` 里的「厂商/品牌/型号」认机型，决定帖子/回复下方那行
     * 「来自 xxx」。开启后每台手机上报自己的型号，不会再人人都是「一加13」；
     * 关掉则回落到 [com.example.c001apk.constant.Constants.DEFAULT_DEVICE_CODE]。
     */
    var reportRealDevice: Boolean
        get() = pref.getBoolean("reportRealDevice", true)
        set(value) = pref.edit().putBoolean("reportRealDevice", value).apply()

    var xAppDevice: String
        get() = pref.getString("xAppDevice", "")!!
        set(value) = pref.edit().putString("xAppDevice", value).apply()

    var customToken: Boolean
        get() = pref.getBoolean("customToken", false)
        set(value) = pref.edit().putBoolean("customToken", value).apply()

    var VERSION_NAME: String
        get() = pref.getString("VERSION_NAME", Constants.VERSION_NAME)!!
        set(value) = pref.edit().putString("VERSION_NAME", value).apply()

    var API_VERSION: String
        get() = pref.getString("API_VERSION", Constants.API_VERSION)!!
        set(value) = pref.edit().putString("API_VERSION", value).apply()

    var VERSION_CODE: String
        get() = pref.getString("VERSION_CODE", Constants.VERSION_CODE)!!
        set(value) = pref.edit().putString("VERSION_CODE", value).apply()

    var MANUFACTURER: String
        get() = pref.getString("MANUFACTURER", "")!!
        set(value) = pref.edit().putString("MANUFACTURER", value).apply()

    var BRAND: String
        get() = pref.getString("BRAND", "")!!
        set(value) = pref.edit().putString("BRAND", value).apply()

    var MODEL: String
        get() = pref.getString("MODEL", "")!!
        set(value) = pref.edit().putString("MODEL", value).apply()

    var BUILDNUMBER: String
        get() = pref.getString("BUILDNUMBER", "")!!
        set(value) = pref.edit().putString("BUILDNUMBER", value).apply()

    var SDK_INT: String
        get() = pref.getString("SDK_INT", "")!!
        set(value) = pref.edit().putString("SDK_INT", value).apply()

    var ANDROID_VERSION: String
        get() = pref.getString("ANDROID_VERSION", "")!!
        set(value) = pref.edit().putString("ANDROID_VERSION", value).apply()

    var USER_AGENT: String
        get() = pref.getString("USER_AGENT", "")!!
        set(value) = pref.edit().putString("USER_AGENT", value).apply()

    var SZLMID: String
        get() = pref.getString("SZLMID", "")!!
        set(value) = pref.edit().putString("SZLMID", value).apply()

    var isRecordHistory: Boolean
        get() = pref.getBoolean("isRecordHistory", true)
        set(value) = pref.edit().putBoolean("isRecordHistory", value).apply()

    var FONTSCALE: String
        get() = pref.getString("FONTSCALE", "1.00")!!
        set(value) = pref.edit().putString("FONTSCALE", value).apply()

    var isIconMiniCard: Boolean
        get() = pref.getBoolean("isIconMiniCard", true)
        set(value) = pref.edit().putBoolean("isIconMiniCard", value).apply()

    var isOpenLinkOutside: Boolean
        get() = pref.getBoolean("isOpenLinkOutside", false)
        set(value) = pref.edit().putBoolean("isOpenLinkOutside", value).apply()

    var FOLLOWTYPE: String
        get() = pref.getString("FOLLOWTYPE", "all")!!
        set(value) = pref.edit().putString("FOLLOWTYPE", value).apply()

    var imageQuality: String
        get() = pref.getString("imageQuality", "auto")!!
        set(value) = pref.edit().putString("imageQuality", value).apply()

    var isColorFilter: Boolean
        get() = pref.getBoolean("isColorFilter", true)
        set(value) = pref.edit().putBoolean("isColorFilter", value).apply()

    /** 启动时检查本应用正式版更新（升级信息走自建接口，默认开） */
    var isCheckUpdateStable: Boolean
        get() = pref.getBoolean("isCheckUpdateStable", true)
        set(value) = pref.edit().putBoolean("isCheckUpdateStable", value).apply()

    /**
     * 自更新接口的匿名统计 ID（`X-Union-Id` 请求头）。
     *
     * 安装后首次读取时随机生成 32 位 hex 并落盘，之后不再变化；
     * 只用于服务端统计「多少台设备在查更新」，不含任何用户/设备信息，
     * 与设备串（xAppDevice / szlmId）完全无关。
     */
    var updateUnionId: String
        get() {
            pref.getString("updateUnionId", null)?.takeIf { it.isNotEmpty() }?.let { return it }
            val id = java.util.UUID.randomUUID().toString().replace("-", "")
            pref.edit().putString("updateUnionId", id).apply()
            return id
        }
        set(value) = pref.edit().putString("updateUnionId", value).apply()

    /** 启动时检查本应用 Beta 版更新（默认关） */
    var isCheckUpdateBeta: Boolean
        get() = pref.getBoolean("isCheckUpdateBeta", false)
        set(value) = pref.edit().putBoolean("isCheckUpdateBeta", value).apply()

    /**
     * SSL 证书校验（默认开）。
     *
     * 开启时走 [SslVerify] 的双重校验（平台 PKIX + 信任锚必须落在内置 CA 库），
     * 可挡住抓包工具/厂商私有根证书做中间人；关闭则回落成系统默认校验。
     */
    var isVerifySsl: Boolean
        get() = pref.getBoolean("verifySsl", true)
        set(value) = pref.edit().putBoolean("verifySsl", value).apply()

    /**
     * 网络传输调试模式（默认关，仅供抓包调试）。
     *
     * 开启后 [SslVerify] 彻底跳过 SSL 校验：既不校验证书链也不校验域名，
     * 系统校验一并绕过（OkHttp / WebView / HttpsURLConnection 三层都放开）。
     *
     * 与 [isVerifySsl] 互斥：`校验 SSL 证书` 开着时本项被强制关闭；
     * 且开关修改后需**重启应用**才生效（客户端实例在启动时构建）。
     */
    var isSslDebug: Boolean
        get() = pref.getBoolean("sslDebug", false)
        set(value) = pref.edit().putBoolean("sslDebug", value).apply()

    /** 其他屏蔽项（关键字/用户/节点）的服务端配置缓存，离线也能先过滤 */
    var spamConfig: String
        get() = pref.getString("spamConfig", "")!!
        set(value) = pref.edit().putString("spamConfig", value).apply()

    var recentIds: String
        get() = pref.getString("recentIds", "")!!
        set(value) = pref.edit().putString("recentIds", value).apply()

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.unregisterOnSharedPreferenceChangeListener(listener)
    }
}