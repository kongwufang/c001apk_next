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
        assertTrue(PrefManager.replaceAccount(PrefManager.messageAccountGeneration, a))
        assertTrue(PrefManager.saveSzlmId(idA, PrefManager.messageAccountGeneration))
    }

    @Test fun switchesCredentialsAndMatchingDeviceTogetherWithoutChangingInstallationIdentity() {
        val before = AccountRequestIdentity.capture()
        assertEquals(a.cookie(), before.cookie)
        assertEquals("", AccountRequestIdentity.capture(includeSession = false).cookie)
        assertTrue(AccountVault.finishLogin(before.generation, b))
        val after = AccountRequestIdentity.capture()
        assertEquals(b.cookie(), after.cookie)
        assertEquals(idA, DeviceCode.decode(after.device).substringBefore(';'))
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

    @Test fun refreshUpdatesEverybodyAndLogoutKeepsDeviceId() {
        val gen = PrefManager.messageAccountGeneration
        assertTrue(AccountVault.finishLogin(gen, b))
        assertFalse(PrefManager.saveSzlmId(idB, gen))
        assertTrue(PrefManager.saveSzlmId(idB, PrefManager.messageAccountGeneration))
        val renewedAt = PrefManager.szlmIdLastObtainedAt
        assertTrue(AccountVault.switchTo(a.uid))
        assertEquals(idB, AccountRequestIdentity.capture().szlmId)
        assertEquals(idB, DeviceCode.decode(AccountRequestIdentity.capture().device).substringBefore(';'))
        assertEquals(renewedAt, PrefManager.szlmIdLastObtainedAt)
        assertTrue(AccountVault.logoutCurrent())
        assertFalse(PrefManager.isLogin)
        assertEquals(idB, PrefManager.SZLMID)
        assertEquals(renewedAt, PrefManager.szlmIdLastObtainedAt)
        assertEquals(listOf(b.uid), AccountVault.accounts().map { it.uid })
        assertTrue(AccountVault.switchTo(b.uid))
        assertEquals(b.cookie(), AccountRequestIdentity.capture().cookie)
        assertEquals(idB, DeviceCode.decode(AccountRequestIdentity.capture().device).substringBefore(';'))
    }

    @Test fun migratesLegacySplitIdsWithoutLosingSavedAccountsOrTheirDeviceSettings() {
        val legacyDevice = TokenDeviceUtils.deviceCodeWithSzlmId(idA)
        val saved = org.json.JSONArray().put(org.json.JSONObject()
            .put("uid", b.uid).put("username", b.username).put("token", b.token)
            .put("refreshToken", b.refreshToken).put("manual", false).put("duid", idA)
            .put("obtainedAt", 2468L).put("deviceCode", legacyDevice)
            .put("device", org.json.JSONObject().put("API_VERSION", "9")))
        val prefs = MyApplication.context.getSharedPreferences("settings", 0)
        prefs.edit().putString("savedAccountsV1", saved.toString()).putString("SZLMID", idB)
            .putBoolean("accountPairedManually", true).commit()
        PrefManager.migrateSharedSzlmId()
        PrefManager.migrateSharedSzlmId()
        assertEquals(idA, PrefManager.SZLMID)
        assertEquals(2468L, PrefManager.szlmIdLastObtainedAt)
        assertEquals(a.cookie(), AccountRequestIdentity.capture().cookie)
        val row = org.json.JSONArray(prefs.getString("savedAccountsV1", null)!!).getJSONObject(0)
        assertFalse(row.has("duid"))
        assertEquals("", DeviceCode.decode(row.getString("deviceCode")).substringBefore(';'))
        assertTrue(AccountVault.switchTo(b.uid))
        assertEquals(b.cookie(), AccountRequestIdentity.capture().cookie)
        assertEquals("9", PrefManager.API_VERSION)
        assertEquals(idA, PrefManager.SZLMID)
    }

    @Test fun keepsCloudCachesPerAccountAndRejectsDelayedWrites() {
        val ownerA = requireNotNull(MessageAccount.current())
        assertTrue(PrefManager.saveCloudBlocked(ownerA, setOf("11")))
        assertTrue(PrefManager.saveCloudSpam(ownerA, "first-cloud"))
        assertTrue(AccountVault.finishLogin(ownerA.generation, b))
        val ownerB = requireNotNull(MessageAccount.current())
        assertTrue(PrefManager.cloudBlocked(ownerB).isEmpty())
        assertEquals("", PrefManager.getCloudSpam(ownerB))
        assertFalse(PrefManager.saveCloudSpam(ownerA, "delayed"))
        assertFalse(PrefManager.saveCloudBlocked(ownerA, setOf("22")))
        assertTrue(PrefManager.saveCloudBlocked(ownerB, setOf("33")))
        val exported = PrefManager.dumpAll()
        assertFalse(exported.values.any { it.toString().contains("token-a") || it.toString().contains("first-cloud") })
        assertTrue(AccountVault.switchTo(a.uid))
        val returned = requireNotNull(MessageAccount.current())
        assertEquals(setOf("11"), PrefManager.cloudBlocked(returned))
        assertEquals("first-cloud", PrefManager.getCloudSpam(returned))
        assertFalse(PrefManager.saveCloudSpam(ownerB, "delayed"))
    }

    @Test fun rejectsQueuedOldRequestBeforeItCanSendUnderNewAccount() {
        var sent = false
        val request = Request.Builder().url("https://api.coolapk.com/test").build()
        val client = OkHttpClient.Builder().addInterceptor(AddCookiesInterceptor).addInterceptor { chain ->
            sent = true
            assertEquals(b.cookie(), chain.request().header("Cookie"))
            assertEquals(idA, DeviceCode.decode(chain.request().header("X-App-Device")!!).substringBefore(';'))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()
        val factory = AccountRequestIdentity.bind(client)
        val queued = factory.newCall(request)
        assertTrue(AccountVault.finishLogin(PrefManager.messageAccountGeneration, b))
        assertTrue(runCatching { queued.execute() }.exceptionOrNull() is IOException)
        assertFalse(sent)
        factory.newCall(request).execute().close()
        assertTrue(sent)
    }
}
