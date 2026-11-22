package com.example.c001apk.util.emoji

import org.json.JSONObject

/** Release metadata served by the project server; it names the immutable pack on the bucket. */
internal data class EmojiPackRelease(
    val version: Int,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val count: Int,
) {
    companion object {
        private val SHA = Regex("[0-9a-fA-F]{64}")
        private val MAX_BYTES = 8L * 1024 * 1024

        fun parse(json: String): EmojiPackRelease? = runCatching {
            val root = JSONObject(json)
            require(root.optInt("schema", 1) == 1)
            val version = root.getInt("version")
            require(version > 0)
            val url = root.getString("url")
            require(url.startsWith("https://") && url.length <= 512)
            require(!url.contains('@') && !url.contains('#') && !url.contains(' '))
            val sha = root.getString("sha256")
            require(SHA.matches(sha))
            val size = root.getLong("sizeBytes")
            require(size in 1..MAX_BYTES)
            val count = root.optInt("count")
            require(count in 1..1024)
            EmojiPackRelease(version, url, sha.lowercase(), size, count)
        }.getOrNull()
    }
}
