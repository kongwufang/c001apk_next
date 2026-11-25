package com.example.c001apk.util

import com.example.c001apk.constant.Constants
import okhttp3.Call
import java.io.IOException

/** Immutable snapshot: signing fields and Cookie always come from the same account. */
internal class AccountRequestIdentity(
    val generation: Long,
    val device: String,
    val ua: String,
    val sdk: String,
    val versionName: String,
    val versionCode: String,
    val apiVersion: String,
    val cookie: String,
) {
    fun requireCurrent() {
        if (generation != PrefManager.messageAccountGeneration) throw IOException("Account changed")
    }

    companion object {
        fun capture(pending: AccountIdentity? = null): AccountRequestIdentity = synchronized(PrefManager) {
            val device = pending?.overrideDeviceCode ?: TokenDeviceUtils.getLastingDeviceCode()
            AccountRequestIdentity(
                PrefManager.messageAccountGeneration,
                device,
                (pending?.deviceSettings?.get("USER_AGENT") as? String ?: PrefManager.USER_AGENT).ifEmpty { Constants.USER_AGENT },
                pending?.deviceSettings?.get("SDK_INT") as? String ?: PrefManager.SDK_INT,
                (pending?.deviceSettings?.get("VERSION_NAME") as? String ?: PrefManager.VERSION_NAME).ifEmpty { Constants.VERSION_NAME },
                (pending?.deviceSettings?.get("VERSION_CODE") as? String ?: PrefManager.VERSION_CODE).ifEmpty { Constants.VERSION_CODE },
                (pending?.deviceSettings?.get("API_VERSION") as? String ?: PrefManager.API_VERSION).ifEmpty { Constants.API_VERSION },
                if (pending != null) "" else PrefManager.accountSnapshot().session
                    ?.takeIf { it.isValid }?.cookie() ?: CookieUtil.SESSID,
            )
        }

        // Retrofit creates the call before OkHttp queues it. A queued call must not adopt a newer session.
        fun bind(client: Call.Factory): Call.Factory = Call.Factory { request ->
            client.newCall(request.newBuilder().tag(AccountRequestIdentity::class.java, capture()).build())
        }
    }
}
