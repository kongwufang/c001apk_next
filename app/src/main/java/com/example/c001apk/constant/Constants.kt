package com.example.c001apk.constant

import com.example.c001apk.MyApplication.Companion.context
import com.example.c001apk.util.PrefManager
import rikka.core.util.ResourceUtils

object Constants {
    const val REQUEST_WITH = "XMLHttpRequest"
    const val LOCALE = "zh-CN"
    const val APP_ID = "com.coolapk.market"
    var DARK_MODE =
        if (ResourceUtils.isNightMode(context.resources.configuration)) "1"
        else "0"
    const val CHANNEL = "coolapk"
    const val MODE = "universal"
    const val APP_LABEL = "token://com.coolapk.market/dcf01e569c1e3db93a3d0fcf191a622c"
    const val VERSION_NAME = "16.4.0"
    const val API_VERSION = "16"
    const val VERSION_CODE = "2607021"
    val USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android ${PrefManager.ANDROID_VERSION}; ${PrefManager.MODEL} ${PrefManager.BUILDNUMBER}) (#Build; ${PrefManager.BRAND}; ${PrefManager.MODEL}; ${PrefManager.BUILDNUMBER}; ${PrefManager.ANDROID_VERSION}) +CoolMarket/${PrefManager.VERSION_NAME}-${PrefManager.VERSION_CODE}-${MODE}"

    // "${System.getProperty("http.agent")} (#Build; ${android.os.Build.BRAND}; ${android.os.Build.MODEL}; ${android.os.Build.DISPLAY}; ${android.os.Build.VERSION.RELEASE}) +CoolMarket/${VERSION_NAME}-${VERSION_CODE}-${MODE}"
    /**
     * 默认设备串的骨架：解出来是 `; ; ; ; 厂商; 品牌; 型号; 版本号; 尾部64hex`。
     *
     * ⚠️ **首字段 szlmId 在这里是空的**，这是有意的：它由 [PrefManager.SZLMID] 在
     * `TokenDeviceUtils.buildDeviceCode()` 里注入，而那份 ID **不进源码**——本机编译时
     * 从 `local.properties` 的 `SZLM_ID` 注入 `BuildConfig.SZLM_ID`，发布版默认空串。
     * 历史版本把某台真实设备的 szlmId 写死在这里分发出去，导致大量真实账号被服务端
     * 算到「同一台设备」上，触发 `-415 账号过多`。
     *
     * 这里只保留服务端认可的那几个结构性字段（机型 / 尾部 64hex），
     * 因为实测（`_rev/diff_headers.py` / `_rev/test_device_format.py`）：
     * 保留这些字段、只换 szlmId 时，`main/indexV8`、`user/profile`、`search`、
     * `feed/replyList` 都正常；而连机型/尾字段一起新造的整串会被风控
     * 要求人机验证（`err_request_captcha_v2`），详情页等接口直接加载失败。
     *
     * device 与 UA 必须配套，见 TokenDeviceUtils.applyDefaultFingerprint()。
     */
    const val DEFAULT_DEVICE_CODE =
        "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO"
    const val DEFAULT_MANUFACTURER = "OnePlus"
    const val DEFAULT_BRAND = "OnePlus"
    const val DEFAULT_MODEL = "PJZ110"
    const val DEFAULT_BUILDNUMBER = "PJZ110_16.0.5.703(CN01)"
    const val DEFAULT_ANDROID_VERSION = "16"
    const val DEFAULT_SDK_INT = "36"

    const val LOADING_FAILED = "加载失败"
    const val LOADING_EMPTY = "什么也没有"
    const val LOADING_END = "没有更多了"
}