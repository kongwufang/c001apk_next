package com.example.c001apk.util

/** Canonical public links. Do not forward tracking parameters or mirror URLs. */
internal object CoolapkLinks {
    private const val BASE = "https://www.coolapk.com"

    fun feed(id: String?): String? = build("feed", id)
    fun user(uid: String?): String? = build("u", uid)

    private fun build(path: String, id: String?): String? {
        val value = id?.trim() ?: return null
        if (value.isEmpty() || value.any { it !in '0'..'9' } || value.all { it == '0' }) return null
        return "$BASE/$path/$value"
    }
}
