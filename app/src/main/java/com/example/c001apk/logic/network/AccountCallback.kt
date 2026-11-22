package com.example.c001apk.logic.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object AccountCallback {
    fun codeFrom(url: String): String? {
        val uri = url.toHttpUrlOrNull() ?: return null
        if (!uri.isHttps || uri.host != "account.coolapk.com" || uri.port != 443 ||
            uri.username.isNotEmpty() || uri.password.isNotEmpty() || uri.encodedPath != "/auth/callback") return null
        if (uri.queryParameterValues("ac").singleOrNull() != "access_token") return null
        return uri.queryParameterValues("code").singleOrNull()?.takeIf {
            it.isNotBlank() && it.length <= 4096 && it.none(Char::isISOControl)
        }
    }
}
