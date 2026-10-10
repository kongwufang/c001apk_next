package com.example.c001apk.util

import android.util.Log
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/**
 * 「清单 → 下载 → 校验」这两跳的公共实现：卡片规则表（cardui）与界面规格表（uiskin）共用。
 *
 * 为什么要分两跳：表本体以后可能挪到 OSS / CDN，那时候只改服务端清单里的 url，App 不用发版。
 *
 *   1. `GET [indexUrl]` → 清单
 *      `{"version":3,"sha256":"...","url":"https://.../x.json","hosts":["oss.example.cn"]}`
 *   2. 版本涨了才下载 `url` → 校验 sha256 → 把本体交回调用方
 *
 * 任何一步失败都返回 null＝「这次没更新」：调用方继续用上次缓存，没缓存就用 App 内置的那套。
 * 所以配置写坏 / 网络不通都不会把界面搞空白。
 */
internal object RemoteTwoHop {

    /** 拉取成功：清单里的新版本号 + 通过校验的表本体 */
    data class Hit(val version: Int, val body: String)

    /**
     * @param indexUrl       清单地址
     * @param currentVersion 本地已有的版本号，清单没涨就直接返回 null（不重复下载）
     * @param bodyKey        本体 JSON 里必须存在的对象键，用来挡「下到了但内容不对」；null 表示只靠 sha256 兜
     * @param maxBytes       本体大小上限（防呆，别被写爆内存 / 磁盘）
     * @param tag            日志标签
     */
    suspend fun fetch(
        indexUrl: String,
        currentVersion: Int,
        bodyKey: String?,
        maxBytes: Int,
        tag: String
    ): Hit? {
        val index = UpdateChecker.fetchConfig(indexUrl) ?: return null
        val meta = runCatching { JSONObject(index) }.getOrNull() ?: return null
        val version = meta.optInt("version", 0)
        val url = meta.optString("url").takeIf { it.isNotBlank() } ?: return null
        if (version <= currentVersion) return null
        if (!isAllowed(url, indexUrl, meta)) {
            Log.w(tag, "下载地址不在白名单内：$url")
            return null
        }

        val body = UpdateChecker.fetchConfig(url) ?: return null
        if (body.length > maxBytes) return null
        meta.optString("sha256").takeIf { it.isNotBlank() }?.let { expect ->
            if (!expect.equals(sha256(body), ignoreCase = true)) {
                Log.w(tag, "sha256 不匹配，丢弃这次更新")
                return null
            }
        }
        // 内容形状再挡一道：sha256 只保证「和清单说的是同一份」，不保证那份本身可用
        if (bodyKey != null && runCatching { JSONObject(body).optJSONObject(bodyKey) }.getOrNull() == null) {
            return null
        }
        return Hit(version, body)
    }

    /**
     * 下载地址白名单：清单里给 `hosts`（换 OSS 时把新域名加上即可），没给就只认清单同域。
     * 目的是清单万一被改，也不能把表本体指向任意域名。
     */
    private fun isAllowed(url: String, indexUrl: String, meta: JSONObject): Boolean {
        if (!url.startsWith("https://")) return false
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        val indexHost = runCatching { URI(indexUrl).host }.getOrNull()?.lowercase()
        val allowed = meta.optJSONArray("hosts")?.let { arr ->
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
