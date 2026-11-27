package com.example.c001apk.util

import com.example.c001apk.constant.Constants
import okhttp3.Call
import java.io.IOException

/** Immutable snapshot: signing fields and Cookie always come from the same account. */
internal class AccountRequestIdentity(
    val generation: Long,
    val szlmId: String,
    val device: String,
    val ua: String,
    val sdk: String,
    val versionName: String,
    val versionCode: String,
    val apiVersion: String,
    val cookie: String,
) {
    fun currentLoginContext(): Boolean = synchronized(PrefManager) {
        generation == PrefManager.messageAccountGeneration && szlmId == PrefManager.SZLMID
    }

    fun requireCurrent() {
        if (!currentLoginContext()) throw IOException("Account or device ID changed")
    }

    companion object {
        fun capture(includeSession: Boolean = true): AccountRequestIdentity = synchronized(PrefManager) {
            val device = TokenDeviceUtils.getLastingDeviceCode()
            AccountRequestIdentity(
                PrefManager.messageAccountGeneration,
                PrefManager.SZLMID,
                device,
                PrefManager.USER_AGENT.ifEmpty { Constants.USER_AGENT },
                PrefManager.SDK_INT,
                PrefManager.VERSION_NAME.ifEmpty { Constants.VERSION_NAME },
                PrefManager.VERSION_CODE.ifEmpty { Constants.VERSION_CODE },
                PrefManager.API_VERSION.ifEmpty { Constants.API_VERSION },
                if (!includeSession) "" else PrefManager.accountSnapshot().session
                    ?.takeIf { it.isValid }?.cookie() ?: CookieUtil.SESSID,
            )
        }

        // Retrofit creates the call before OkHttp queues it. A queued call must not adopt a newer session.
        fun bind(client: Call.Factory): Call.Factory = Call.Factory { request ->
            client.newCall(request.newBuilder().tag(AccountRequestIdentity::class.java, capture()).build())
        }
    }
}
