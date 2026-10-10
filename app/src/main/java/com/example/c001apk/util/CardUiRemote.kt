package com.example.c001apk.util

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/**
 * 卡片渲染规则表的热更新链路：**地址由服务端给**（以后挪到 OSS/CDN 只改服务端，App 不用发版）。
 *
 * 两跳：
 *   1. `GET .../c001apk/cardui/` → 清单
 *      `{"code":0,"version":3,"sha256":"...","url":"https://.../cardui.json","hosts":["oss.houlangs.cn"]}`
 *   2. 下载 `url` → 校验 sha256 → 存 [PrefManager.cardUiConfig] + 版本号 → [CardUi.load]
 *
 * 任何一步失败都只是「这次没更新」：继续用上次缓存的表，没缓存就用 App 内置默认表。
 * 版本号没涨就不重复下载。
 */
object CardUiRemote {

    private const val TAG = "CardUiRemote"

    /** 清单接口（跟随升级接口那套会话头，复用 UpdateChecker 的 client / UA） */
    const val INDEX_URL = "https://service.houlangs.cn/c001apk/cardui/"

    /** 规则表大小上限（正常几 KB；防呆，别被写爆内存/磁盘） */
    private const val MAX_BYTES = 512 * 1024

    fun initFromCache() {
        CardUi.load(PrefManager.cardUiConfig)
    }

    fun refreshAsync() {
        Thread {
            runCatching { runBlocking { refresh() } }
                .onFailure { Log.w(TAG, "拉取卡片规则表失败", it) }
        }.apply {
            name = "card-ui"
            isDaemon = true
        }.start()
    }

    /** 拉清单 →（版本涨了才）下载 → 校验 → 落盘。返回这次是否真的更新了 */
    suspend fun refresh(): Boolean {
        val index = UpdateChecker.fetchConfig(INDEX_URL) ?: return false
        val obj = runCatching { JSONObject(index) }.getOrNull() ?: return false
        val version = obj.optInt("version", 0)
        val url = obj.optString("url").takeIf { it.isNotBlank() } ?: return false
        if (version <= PrefManager.cardUiVersion) return false
        if (!isAllowed(url, obj)) {
            Log.w(TAG, "规则表下载地址不在白名单内：$url")
            return false
        }

        val body = UpdateChecker.fetchConfig(url) ?: return false
        if (body.length > MAX_BYTES) return false
        obj.optString("sha256").takeIf { it.isNotBlank() }?.let { expect ->
            if (!expect.equals(sha256(body), ignoreCase = true)) {
                Log.w(TAG, "规则表 sha256 不匹配，丢弃这次更新")
                return false
            }
        }
        // 解析不出 cards 就当没更新（CardUi.load 里还会再挡一层）
        if (runCatching { JSONObject(body).optJSONObject("cards") }.getOrNull() == null) return false

        PrefManager.cardUiConfig = body
        PrefManager.cardUiVersion = version
        CardUi.load(body)
        Log.i(TAG, "卡片规则表更新到 v$version，共 ${CardUi.remoteSize()} 条模板规则")
        return true
    }

    /**
     * 下载地址白名单：清单里给 `hosts`（换 OSS 时把新域名加上即可），没给就只认清单同域。
     * 目的是清单万一被改，也不能把规则表指向任意域名。
     */
    private fun isAllowed(url: String, index: JSONObject): Boolean {
        if (!url.startsWith("https://")) return false
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        val indexHost = runCatching { URI(INDEX_URL).host }.getOrNull()?.lowercase()
        val allowed = index.optJSONArray("hosts")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { h -> h.isNotBlank() }?.lowercase()
            }
        }.orEmpty()
        return host == indexHost || allowed.any {
            it == host || (it.startsWith("*.") && host.endsWith(it.removePrefix("*")))
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
