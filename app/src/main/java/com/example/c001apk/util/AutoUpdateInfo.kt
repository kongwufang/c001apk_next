package com.example.c001apk.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal data class AutoUpdateInfo(
    val update: UpdateChecker.UpdateInfo,
    val url: HttpUrl,
    val sha256: String,
    val sizeBytes: Long,
) {
    val fileName get() = "${update.versionCode}-$sha256.apk"

    companion object {
        const val ENDPOINT = "https://service.houlangs.cn/c001apk/antoupdate/"
        private val digest = Regex("[a-f0-9]{64}")
        const val MAX_APK_BYTES = 512L * 1024 * 1024

        fun parse(json: String): AutoUpdateInfo? = runCatching {
            val response = JSONObject(json)
            if (response.integer("code") != 0L) return null
            val stable = response.getJSONObject("stable")
            val code = stable.integer("versionCode") ?: return null
            val name = (stable.opt("versionName") as? String)?.trim() ?: return null
            val url = (stable.opt("downloadUrl") as? String)?.toHttpUrlOrNull()
            val sha = (stable.opt("sha256") as? String)?.lowercase() ?: return null
            val bytes = stable.integer("sizeBytes") ?: return null
            val changelog = stable.opt("changelog") as? String ?: return null
            if (code <= 0 || name.isBlank() || url == null || !url.isHttps ||
                url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.fragment != null || !digest.matches(sha) || bytes !in 1L..MAX_APK_BYTES
            ) return null
            AutoUpdateInfo(
                UpdateChecker.UpdateInfo(name, code, changelog, emptyList()),
                url, sha, bytes,
            )
        }.getOrNull()

        private fun JSONObject.integer(name: String): Long? = when (val value = opt(name)) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }
    }
}
