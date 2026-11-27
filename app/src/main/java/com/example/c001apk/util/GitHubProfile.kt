package com.example.c001apk.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 拉 GitHub 用户资料，目前只取 bio —— 关于页「维护者」那一条下面的一行小字。
 *
 * 走公开的 `api.github.com/users/{user}`，不需要 token。注意**未认证时限流按 IP 算**
 * （每小时 60 次），所以成功过的结果就缓存在内存里，之后直接复用：
 * 关于页是低频入口，这样够用，也不会出现「这次有网就显示、下次限流就消失」的闪烁。
 */
object GitHubProfile {

    private const val BASE_URL = "https://api.github.com/users/"

    /** 独立客户端：请求发往 GitHub，不该挂酷安的 Cookie / 签名 / 日志拦截器 */
    private val client by lazy {
        NetworkHealth.clientBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 用户名 → bio（空串表示确认过 TA 没写 bio，同样不再重复请求） */
    private val cache = ConcurrentHashMap<String, String>()

    /** 拉取 bio；网络失败 / 限流 / 用户没填 bio 一律返回 null，调用方把那行小字藏起来 */
    suspend fun fetchBio(user: String): String? = withContext(Dispatchers.IO) {
        cache[user]?.let { return@withContext it.takeIf { bio -> bio.isNotEmpty() } }
        val bio = runCatching {
            val request = Request.Builder()
                .url("$BASE_URL$user")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string().orEmpty()).optString("bio").trim()
            }
        }.getOrNull() ?: return@withContext null
        cache[user] = bio
        bio.takeIf { it.isNotEmpty() }
    }
}
