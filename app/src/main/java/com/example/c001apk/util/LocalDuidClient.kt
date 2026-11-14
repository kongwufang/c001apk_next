package com.example.c001apk.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.c001apk.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Isolated upstream client: no Coolapk cookies, account headers, cloud API, or request body logging. */
internal object LocalDuidClient {
    private val client by lazy {
        SslVerify.apply(OkHttpClient.Builder())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    suspend fun fetch(context: Context): String = withContext(Dispatchers.IO) {
        val ssaid = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val template = context.resources.openRawResource(R.raw.szlm_template).bufferedReader().use { it.readText() }
        val body = LocalDuidProtocol.encode(LocalDuidProtocol.payload(template, ssaid))
        val request = Request.Builder()
            .url(LocalDuidProtocol.URL)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID})")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val id = response.use {
                            if (!it.isSuccessful) throw IOException("取号 HTTP ${it.code}")
                            val input = it.body?.byteStream() ?: throw IOException("取号响应为空")
                            LocalDuidProtocol.decode(LocalDuidProtocol.readBounded(input))
                        }
                        continuation.resume(id)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}
