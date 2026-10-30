package com.example.c001apk.util

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 服务器下发的 Cookie（`Set-Cookie`），**所有上报接口统一带上**。
 *
 * ## 为什么要这个
 *
 * 用户 2026-10-31 原话：
 * > 「这里面所有请求我服务器的接口，我服务器都可以返回 setcookies 指令，
 * >   收到后下一次上报的时候在所有地方带上这个」
 *
 * ⇒ 服务器可以在任何接口里 `Set-Cookie`（比如「这台设备已被标记」「需要二次验证」
 *   「本次会话的令牌」），客户端记住之后，**下一次任何上报都带上** ✓
 *
 * ## 作用域
 *
 * ⚠️ **只发给自家域名**（`*.houlangs.cn`）✓
 * 绝不发给酷安 ✗ —— 那是别人的服务器，会把我们的标记泄漏出去 ✗
 *
 * ## 存多久
 *
 * 持久化到 [PrefManager] ✓ 进程重启也还在 ✓
 * `Max-Age=0` / 过期时间已过 ⇒ 立刻删掉 ✓
 *
 * ## 为什么不用 OkHttp 自带的 `CookieJar`
 *
 * 自带的 `CookieJar` 是按 RFC 6265 存所有域名的 cookie ✓ 但：
 * ① 我们**只**关心自家域名 ✓
 * ② 要能持久化（APP 重启后还带）✓ 自带的内存实现不持久化 ✗
 * ③ 要能一眼看出「服务器给了什么」✓ 方便排查 ✓
 * ⇒ 自己写一个小的，逻辑简单可控 ✓
 */
object ServerCookieStore {

    private const val TAG = "ServerCookie"
    private const val PREF_KEY = "server_cookies"

    /** 自家域名后缀（只对这些 host 生效 ✓） */
    private val HOST_SUFFIX = listOf("houlangs.cn", "houlangs.com")

    /**
     * 内存缓存：`name=value; name2=value2`（按 host 分不必要 —— 只有自家域名 ✓）
     */
    @Volatile
    private var cached: String? = null

    private fun isOurHost(host: String): Boolean {
        val h = host.lowercase()
        return HOST_SUFFIX.any { h == it || h.endsWith(".$it") }
    }

    /** 读当前该带上的 Cookie 串（没有就返回空串） */
    @Synchronized
    fun current(): String {
        cached?.let { return it }
        val saved = runCatching { PrefManager.serverCookies }.getOrDefault("")
        cached = saved
        return saved
    }

    /** 清空（调试用） */
    @Synchronized
    fun clear() {
        cached = ""
        runCatching { PrefManager.serverCookies = "" }
        Log.i(TAG, "已清空")
    }

    /**
     * 处理一条 `Set-Cookie`。
     *
     * 只认 `name=value` 与 `Max-Age` /
     * `Expires`（判断是否要删），其余属性（Domain/Path/Secure/HttpOnly/SameSite）忽略 ✓
     */
    @Synchronized
    fun onSetCookie(raw: String) {
        if (raw.isBlank()) return
        val parts = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val nv = parts[0]
        val eq = nv.indexOf('=')
        if (eq <= 0) return
        val name = nv.substring(0, eq).trim()
        val value = nv.substring(eq + 1).trim()
        if (name.isEmpty()) return

        // 判断是否要删除
        var expired = false
        var maxAgeSeen = false
        for (p in parts.drop(1)) {
            val lp = p.lowercase()
            when {
                lp.startsWith("max-age=") -> {
                    maxAgeSeen = true
                    val v = lp.removePrefix("max-age=").trim().toIntOrNull() ?: 0
                    if (v <= 0) expired = true
                }
                lp.startsWith("expires=") && !maxAgeSeen -> {
                    // 只做一个宽松判断：1970 年 ⇒ 已过期
                    if (lp.contains("1970") || lp.contains("1969")) expired = true
                }
            }
        }

        val map = parse(current())
        if (expired || value.isEmpty()) {
            map.remove(name)
            Log.i(TAG, "服务器删除 cookie: $name")
        } else {
            map[name] = value
            Log.i(TAG, "服务器下发 cookie: $name=${value.take(24)}…（共 ${map.size} 个）")
        }
        val joined = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        cached = joined
        runCatching { PrefManager.serverCookies = joined }
    }

    private fun parse(s: String): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        if (s.isBlank()) return m
        for (kv in s.split(';')) {
            val t = kv.trim()
            val i = t.indexOf('=')
            if (i > 0) m[t.substring(0, i).trim()] = t.substring(i + 1).trim()
        }
        return m
    }

    /**
     * 挂到所有打自家服务器的 OkHttp 客户端上。
     *
     * 用法：`OkHttpClient.Builder().addInterceptor(ServerCookieStore.interceptor())`
     */
    fun interceptor(): Interceptor = Interceptor { chain ->
        val req = chain.request()
        val host = req.url.host
        val ours = isOurHost(host)

        // ① 请求前：把记住的 cookie 带上（只对自家域名 ✓）
        val outReq = if (ours) {
            val c = current()
            if (c.isNotEmpty()) {
                req.newBuilder().header("Cookie", c).build()
            } else {
                req
            }
        } else {
            req
        }

        val resp: Response = chain.proceed(outReq)

        // ② 响应后：收下 Set-Cookie（只对自家域名 ✓）
        if (ours) {
            val sets = resp.headers("Set-Cookie")
            if (sets.isNotEmpty()) {
                for (sc in sets) onSetCookie(sc)
            }
        }
        resp
    }
}
