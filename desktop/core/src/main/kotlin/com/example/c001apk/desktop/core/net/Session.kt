package com.example.c001apk.desktop.core.net

import com.example.c001apk.desktop.core.auth.V3Token

/**
 * 酷安客户端的常量与请求头组装。
 *
 * 对应 Android 端：
 *   `Constants.kt`               —— 常量
 *   `util/AddCookiesInterceptor.kt` —— 请求头（14 个）+ Cookie
 *
 * 请求头顺序与语义与 Android 端保持一致；已实测服务端会校验的关键项：
 *   * `X-App-Token`   —— 严格校验（伪造/缺失 → 请求未验证；与 X-App-Code 不一致 → HTTP 403）
 *   * `X-App-Code`    —— 必须与签名用的 versionCode 一致
 *   * `X-App-Device`  —— 必须与签名用的 device 一致；首字段 szlmId 会被校验（无效 → -419）
 */
object Protocol {

    const val APP_ID = "com.coolapk.market"
    const val REQUEST_WITH = "XMLHttpRequest"
    const val API_VERSION = "16"
    const val CHANNEL = "coolapk"
    const val MODE = "universal"
    const val DARK_MODE = "0"
    const val LOCALE = "zh-CN"
    const val SDK_INT = "36"
    const val VERSION_NAME = "16.6.4"
    const val VERSION_CODE = "2609291"

    /** api1：大部分业务接口 */
    const val BASE_URL_API = "https://api.coolapk.com"

    /** api2：详情 / 评论等走这个（见 Android 端 NetworkModule.API2_BASE_URL） */
    const val BASE_URL_API2 = "https://api2.coolapk.com"

    /** 与 Android 端 Constants.USER_AGENT 一致 */
    const val USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 16; PJZ110 PJZ110_16.0.5.703(CN01)) " +
            "(#Build; OnePlus; PJZ110; PJZ110_16.0.5.703(CN01); 16) " +
            "+CoolMarket/$VERSION_NAME-$VERSION_CODE-$MODE"

    const val REFERER = "https://api.coolapk.com/?ref=CoolMarket/$VERSION_NAME-$VERSION_CODE-$MODE"
}

/**
 * 会话状态：设备串 + 登录凭据。
 *
 * 线程安全（`@Volatile` + 不可变快照）：拦截器每请求读取一次。
 *
 * 登录凭据共两个，字段语义与 OAuth 相反（详见 _rev/COOLAPK_CREDENTIAL_MODEL.md）：
 *   * `token`        —— 长期凭据，同时是「凭据生成器」，可换取新的 refreshToken
 *   * `refreshToken` —— 业务凭据，用于业务接口
 * 实测二者在业务接口上表现一致，均可直接放进 `Cookie: token=`。
 *
 * ⚠️ 本对象持有敏感凭据，不要打日志。
 */
class Session(
    deviceCode: String = DEFAULT_DEVICE_CODE,
    uid: String? = null,
    username: String? = null,
    token: String? = null,
) {
    @Volatile
    var deviceCode: String = deviceCode
        private set

    @Volatile
    var uid: String? = uid
        private set

    /** 已 URL 编码的 username（服务端要求编码形式，见 Android 端 PrefManager.username） */
    @Volatile
    var username: String? = username
        private set

    @Volatile
    var token: String? = token
        private set

    val isLogin: Boolean get() = !uid.isNullOrEmpty() && !token.isNullOrEmpty()

    fun login(uid: String, username: String?, token: String) {
        this.uid = uid
        this.username = username
        this.token = token
    }

    fun logout() {
        uid = null
        username = null
        token = null
    }

    fun updateDeviceCode(code: String) {
        deviceCode = code
    }

    /** 组装 Cookie。登录态三字段；游客态不带（或由调用方传 SESSID）。 */
    fun cookie(sessId: String? = null): String? {
        if (isLogin) {
            return buildString {
                append("uid=").append(uid)
                append("; username=").append(username ?: "")
                append("; token=").append(token)
            }
        }
        return sessId?.takeIf { it.isNotBlank() }?.let { "SESSID=$it" }
    }

    /**
     * 组装全部请求头。与 Android 端 `AddCookiesInterceptor` 逐项一致。
     *
     * @param versionCode 必须与 `X-App-Token` 的签名参数一致
     */
    fun headers(
        versionCode: String = Protocol.VERSION_CODE,
        versionName: String = Protocol.VERSION_NAME,
        deviceCode: String = this.deviceCode,
        sessId: String? = null,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
    ): Map<String, String> {
        val cookie = cookie(sessId)
        val h = linkedMapOf(
            "User-Agent" to Protocol.USER_AGENT,
            "X-Requested-With" to Protocol.REQUEST_WITH,
            "X-Sdk-Int" to Protocol.SDK_INT,
            "X-Sdk-Locale" to Protocol.LOCALE,
            "X-App-Id" to Protocol.APP_ID,
            "X-App-Token" to V3Token.gen(deviceCode, timestampSeconds, versionCode.toLong()),
            "X-App-Version" to versionName,
            "X-App-Code" to versionCode,
            "X-Api-Version" to Protocol.API_VERSION,
            "X-App-Device" to deviceCode,
            "X-Dark-Mode" to Protocol.DARK_MODE,
            "X-App-Channel" to Protocol.CHANNEL,
            "X-App-Mode" to Protocol.MODE,
            "X-App-Supported" to versionCode,
            "Referer" to Protocol.REFERER,
        )
        if (cookie != null) h["Cookie"] = cookie
        return h
    }

    companion object {
        /**
         * 默认设备串（`X-App-Device`，`DeviceCode.encode` 的输出）。
         *
         * 结构（解码后 9 段，分号分隔）：
         *   [0] szlmId  [1..3] 保留空段  [4] 厂商  [5] 品牌  [6] 型号  [7] 版本号  [8] 尾部 65 位 hex
         *
         * 注意：szlmId 会被服务端校验。此处为已注册可用的值；
         * 桌面端上线前应改为自有设备注册，或由用户配置（见 README 的「设备串」一节）。
         */
        const val DEFAULT_DEVICE_CODE =
            "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4Ay" +
                "OpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyOidGRoJ0ZiZk" +
                "YrFWbmlkWLF2dJZ3QZhlQidXT6Fjd0NFOsVFR"
    }
}
