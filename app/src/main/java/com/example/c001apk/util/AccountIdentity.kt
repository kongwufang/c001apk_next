package com.example.c001apk.util

/** The upstream-issued ID is account-local; Android's real SSAID is not. */
internal class AccountIdentity(
    val szlmId: String,
    val overrideDeviceCode: String = "",
    val obtainedAt: Long = 0,
    val noticed: Boolean = false,
    val pairedManually: Boolean = false,
    val deviceSettings: Map<String, Any> = emptyMap(),
) {
    companion object {
        fun acceptsNewId(id: String): Boolean =
            id.matches(Regex("DU[A-Za-z0-9_-]{34}")) && id.drop(2).any { it != '0' }
    }
}
