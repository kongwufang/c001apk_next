package com.example.c001apk.desktop.core.net

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 桌面端的 Retrofit 客户端。对应 Android 端 `di/NetworkModule.kt`。
 *
 * 与 Android 端的差异（都是有意为之）：
 *   * 去掉了 Hilt/Dagger、`SslVerify`（SSL 校验绕过）、`RiskControlInterceptor`（弹窗提示）
 *     —— 前两者是 Android 端为兼容自签证书/风控做的处理，桌面端先不引入；
 *   * 保留 `AddCookiesInterceptor`（请求头 + Cookie）与可选日志。
 *
 * ★ 域名分流：api1（`api.coolapk.com`）给大部分接口；
 *   api2（`api2.coolapk.com`）给详情 / 评论（见 [ApiService] 注释）。
 */
class Client(
    val session: Session = Session(),
    versionCode: String = Protocol.VERSION_CODE,
    versionName: String = Protocol.VERSION_NAME,
    /** 打开会打印完整请求头（含凭据）——只在本地调试用 */
    debugLogging: Boolean = false,
    connectTimeoutSeconds: Long = 20,
    readTimeoutSeconds: Long = 30,
) {
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AddCookiesInterceptor(session, versionCode, versionName))
        .apply {
            if (debugLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
                )
            }
        }
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder().create()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    /** 大部分业务接口 */
    val api: ApiService = retrofit(Protocol.BASE_URL_API).create(ApiService::class.java)

    /** 详情 / 评论 */
    val api2: ApiService = retrofit(Protocol.BASE_URL_API2).create(ApiService::class.java)
}

/** 统一错误：把服务端的 `status/message` 暴露出来，便于上层识别限流（5902）与设备异常（-419）。 */
class ApiException(val status: Int, override val message: String) : RuntimeException(message)

/**
 * 同步执行 Retrofit Call（桌面端先用阻塞式；UI 层放到 IO 协程里调用）。
 *
 * 定义为**顶层扩展函数**（而非 Client 的成员），这样任何拿到 `Call` 的地方都能直接用。
 */
fun <T> Call<T>.executeSync(): T {
    val r = execute()
    if (!r.isSuccessful) {
        throw ApiException(r.code(), "HTTP ${r.code()} ${r.message()}")
    }
    return r.body() ?: throw ApiException(r.code(), "响应体为空")
}

/** `Resp<T>` 取数据：status 非 0 时抛出 [ApiException] */
fun <T> com.example.c001apk.desktop.core.model.Resp<T>.orThrow(): T {
    val st = status ?: 0
    if (st != 0) throw ApiException(st, message ?: error ?: "status=$st")
    return data ?: throw ApiException(st, "data 为空（message=${message ?: "-"}）")
}
