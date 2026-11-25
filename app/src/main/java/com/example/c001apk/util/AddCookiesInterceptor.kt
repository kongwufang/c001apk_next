package com.example.c001apk.util

import com.example.c001apk.constant.Constants.APP_ID
import com.example.c001apk.constant.Constants.CHANNEL
import com.example.c001apk.constant.Constants.DARK_MODE
import com.example.c001apk.constant.Constants.LOCALE
import com.example.c001apk.constant.Constants.MODE
import com.example.c001apk.constant.Constants.REQUEST_WITH
import com.example.c001apk.util.TokenDeviceUtils.getTokenV3
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object AddCookiesInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response = send(chain, useSavedCookies = true, form = true)

    internal fun intercept(chain: Interceptor.Chain, useSavedCookies: Boolean): Response =
        send(chain, useSavedCookies, form = true)

    internal fun send(chain: Interceptor.Chain, useSavedCookies: Boolean, form: Boolean): Response {
        val request = chain.request()
        val identity = request.tag(AccountRequestIdentity::class.java) ?: AccountRequestIdentity.capture()
        identity.requireCurrent()
        val builder = request.newBuilder()
            .header("User-Agent", identity.ua)
            .header("X-Requested-With", REQUEST_WITH)
            .header("X-Sdk-Int", identity.sdk)
            .header("X-Sdk-Locale", LOCALE)
            .header("X-App-Id", APP_ID)
            .header("X-App-Token", identity.device.getTokenV3(identity.versionCode))
            .header("X-App-Version", identity.versionName)
            .header("X-App-Code", identity.versionCode)
            .header("X-Api-Version", identity.apiVersion)
            .header("X-App-Device", identity.device)
            .header("X-Dark-Mode", DARK_MODE)
            .header("X-App-Channel", CHANNEL)
            .header("X-App-Mode", MODE)
            .header("X-App-Supported", identity.versionCode)
            .header("Referer", "https://api.coolapk.com/?ref=CoolMarket/${identity.versionName}-${identity.versionCode}-$MODE")
        if (form) builder.addHeader("Content-Type", "application/x-www-form-urlencoded")
        if (useSavedCookies && identity.cookie.isNotBlank()) builder.header("Cookie", identity.cookie)
        identity.requireCurrent()
        val response = chain.proceed(builder.build())
        if (identity.generation != PrefManager.messageAccountGeneration) {
            response.close()
            throw IOException("Account changed")
        }
        return response
    }
}

/** Anonymous Coolapk probes use the same signing fields without Cookie. */
object AddCookiesInterceptorNoCookie : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response =
        AddCookiesInterceptor.send(chain, useSavedCookies = false, form = false)
}
