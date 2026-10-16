package com.example.c001apk.desktop.core.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 给每个请求加上酷安的请求头与 Cookie。
 *
 * 对应 Android 端 `util/AddCookiesInterceptor.kt`（原实现继承 Interceptor 并全局读 PrefManager；
 * 这里改为注入 [Session]，去掉了 Android 的 SharedPreferences 依赖，行为一致）。
 *
 * 注意：`X-App-Token` 是**带时间戳**的签名，必须每请求重算（不能在客户端建时算一次），
 * 因此这里每次 `intercept` 都调用 `Session.headers()`。
 */
class AddCookiesInterceptor(
    private val session: Session,
    private val versionCode: String = Protocol.VERSION_CODE,
    private val versionName: String = Protocol.VERSION_NAME,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        session.headers(
            versionCode = versionCode,
            versionName = versionName,
        ).forEach { (k, v) -> builder.header(k, v) }

        // 有 body 的请求按 Android 端口径补 Content-Type
        if (original.body != null && original.header("Content-Type") == null) {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
        }

        return chain.proceed(builder.build())
    }
}
