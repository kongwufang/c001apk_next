package com.example.c001apk.logic.model

import java.net.URLDecoder
import java.net.URLEncoder

/** Username is stored in its Cookie-encoded representation; never log the session. */
internal class AccountSession(
    val uid: String,
    val username: String,
    val token: String,
    val refreshToken: String = "",
    val avatar: String = "",
    val adminType: Int? = null,
) {
    val effectiveToken: String get() = refreshToken.ifBlank { token }
    val isValid: Boolean get() = validUid(uid) && username.isNotBlank() && effectiveToken.isNotBlank()

    fun cookie(): String = "uid=$uid; username=$username; token=$effectiveToken"

    companion object {
        fun validUid(uid: String): Boolean = uid.isNotBlank() && uid != "0"
        fun encodeName(name: String): String = URLEncoder.encode(name, "UTF-8")
        fun decodeCookieName(name: String): String = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)

        fun fromResponse(data: CheckResponse.Data): AccountSession = AccountSession(
            uid = data.uid.orEmpty(),
            username = encodeName(data.username.orEmpty()),
            token = data.token.orEmpty(),
            refreshToken = data.refreshToken.orEmpty(),
            avatar = data.userAvatar.orEmpty(),
            adminType = data.adminType,
        )

        /** Do not combine pieces of different sessions from different URLs or Cookie paths. */
        fun fromWebCookie(raw: String): AccountSession? {
            val fields = mutableMapOf<String, String>()
            for (pair in raw.split(';')) {
                val key = pair.substringBefore('=').trim()
                if (key !in setOf("uid", "username", "token") || '=' !in pair) continue
                val value = pair.substringAfter('=').trim()
                if (!value.all { it.code in 33..126 } || fields.put(key, value)?.let { it != value } == true) return null
            }
            val uid = fields["uid"].orEmpty()
            val token = fields["token"].orEmpty()
            val name = encodeName(decodeCookieName(fields["username"].orEmpty()))
            // The authenticated check supplies the canonical username if the website omits it.
            return if (validUid(uid) && token.isNotBlank()) AccountSession(uid, name, token) else null
        }
    }
}
