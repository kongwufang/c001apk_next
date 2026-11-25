package com.example.c001apk.util

import android.app.Application
import com.example.c001apk.MyApplication
import com.example.c001apk.logic.model.AccountSession
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountIdentityFactoryTest {
    private val a = AccountSession("100", "Account+A", "token-a")
    private val b = AccountSession("200", "Account+B", "token-b", "refresh-b")
    private val idA = "DU" + "a".repeat(34)
    private val idB = "DU" + "b".repeat(34)

    @Before fun reset() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        MyApplication.context.getSharedPreferences("settings", 0).edit().clear().commit()
        CookieUtil.clearAccountState()
        PrefManager.ssaid = "1234567890abcdef"
        PrefManager.userRandomId = "local-installation"
        assertTrue(PrefManager.replaceAccount(0L, a))
        assertTrue(PrefManager.saveSzlmId(idA, PrefManager.messageAccountGeneration))
    }

    @Test fun switchesCredentialsAndMatchingDeviceTogetherWithoutChangingInstallationIdentity() {
        val before = AccountRequestIdentity.capture()
        val pending = requireNotNull(PrefManager.addAccountIdentity(idB))
        assertEquals(a.cookie(), AccountRequestIdentity.capture().cookie)
        assertEquals("", AccountRequestIdentity.capture(pending).cookie)
        assertTrue(AccountVault.finishLogin(before.generation, b, pending))
        val after = AccountRequestIdentity.capture()
        assertEquals(b.cookie(), after.cookie)
        assertEquals(idB, DeviceCode.decode(after.device).substringBefore(';'))
        assertEquals(DeviceCode.decode(before.device).substringAfter(';'), DeviceCode.decode(after.device).substringAfter(';'))
        assertEquals(before.ua, after.ua)
        assertEquals(before.versionCode, after.versionCode)
        assertEquals("1234567890abcdef", PrefManager.ssaid)
        assertEquals("local-installation", PrefManager.userRandomId)
        assertTrue(AccountVault.switchTo(a.uid))
        assertEquals(a.cookie(), AccountRequestIdentity.capture().cookie)
        assertEquals(idA, DeviceCode.decode(AccountRequestIdentity.capture().device).substringBefore(';'))
        assertEquals(listOf(a.uid, b.uid), AccountVault.accounts().map { it.uid })
    }

    @Test fun rejectsReusedIdAndStaleRefreshAndKeepsOtherAccountAfterLogout() {
        val gen = PrefManager.messageAccountGeneration
        assertFalse(AccountVault.finishLogin(gen, b, requireNotNull(PrefManager.addAccountIdentity(idA))))
        assertEquals(a.uid, PrefManager.uid)
        assertTrue(AccountVault.finishLogin(gen, b, requireNotNull(PrefManager.addAccountIdentity(idB))))
        assertFalse(PrefManager.saveSzlmId(idA, PrefManager.messageAccountGeneration))
        assertFalse(PrefManager.saveSzlmId(idB, gen))
        assertFalse(PrefManager.saveSzlmId("DU" + "c".repeat(34), PrefManager.messageAccountGeneration, true))
        assertTrue(AccountVault.logoutCurrent())
        assertFalse(PrefManager.isLogin)
        assertEquals("", PrefManager.SZLMID)
        assertEquals(listOf(a.uid), AccountVault.accounts().map { it.uid })
        assertTrue(AccountVault.switchTo(a.uid))
        assertEquals(a.cookie(), AccountRequestIdentity.capture().cookie)
        assertFalse(AccountIdentity.acceptsNewId("DU" + "0".repeat(34)))
        assertFalse(PrefManager.dumpAll().values.any { it.toString().contains("refresh-b") })
    }

    @Test fun rejectsQueuedOldRequestBeforeItCanSendUnderNewAccount() {
        var sent = false
        val request = Request.Builder().url("https://api.coolapk.com/test").build()
        val client = OkHttpClient.Builder().addInterceptor(AddCookiesInterceptor).addInterceptor { chain ->
            sent = true
            assertEquals(b.cookie(), chain.request().header("Cookie"))
            assertEquals(idB, DeviceCode.decode(chain.request().header("X-App-Device")!!).substringBefore(';'))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()
        val factory = AccountRequestIdentity.bind(client)
        val queued = factory.newCall(request)
        assertTrue(AccountVault.finishLogin(PrefManager.messageAccountGeneration, b, requireNotNull(PrefManager.addAccountIdentity(idB))))
        assertTrue(runCatching { queued.execute() }.exceptionOrNull() is IOException)
        assertFalse(sent)
        factory.newCall(request).execute().close()
        assertTrue(sent)
    }
}
