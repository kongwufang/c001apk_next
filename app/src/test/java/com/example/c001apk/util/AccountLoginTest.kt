package com.example.c001apk.util

import android.app.Application
import com.example.c001apk.MyApplication
import com.example.c001apk.logic.model.AccountSession
import com.example.c001apk.logic.model.CheckResponse
import com.example.c001apk.logic.network.AccountApi
import com.example.c001apk.logic.network.AccountCallback
import com.example.c001apk.logic.network.LenientGson
import com.example.c001apk.logic.repository.AccountRepository
import com.example.c001apk.logic.repository.AccountRepository.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountLoginTest {
    @Before fun reset() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        MyApplication.context.getSharedPreferences("settings", 0).edit().clear().commit()
        CookieUtil.clearAccountState()
    }

    @Test fun callbackRequiresExactHttpsHostPathAndOneCode() {
        val base = "https://account.coolapk.com/auth/callback"
        assertEquals("a+b", AccountCallback.codeFrom("$base?ac=access_token&code=a%2Bb"))
        listOf(
            "$base?ac=other&code=a", "$base?ac=access_token&code=a&code=b", "$base?ac=access_token&code=",
            "$base?ac=access_token&code=a%0A", "$base/fake?ac=access_token&code=a",
            "https://account.coolapk.com.attacker.test/auth/callback?ac=access_token&code=a",
            "https://account.coolapk.com:8443/auth/callback?ac=access_token&code=a",
            "http://account.coolapk.com/auth/callback?ac=access_token&code=a",
        ).forEach { assertNull(AccountCallback.codeFrom(it)) }
    }

    @Test fun codeExchangeUsesOfficialRequestsAndStoresOnlyConfirmedSession() = runTest {
        save(OLD)
        val requests = mutableListOf<okhttp3.Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            requests += req
            val refresh = if (req.url.encodedPath.endsWith("accessToken")) "exchange-refresh" else "verified-refresh"
            okhttp3.Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":{"uid":"200","username":"New User","token":"new-token","refreshToken":"$refresh","adminType":0}}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.coolapk.com/").client(client)
            .addConverterFactory(GsonConverterFactory.create(LenientGson.gson)).build().create(AccountApi::class.java)
        assertEquals(Result.CONFIRMED, AccountRepository(api).loginWithCode("demo+code", PrefManager.messageAccountGeneration))
        assertEquals(listOf("/v6/account/accessToken", "/v6/account/checkLoginInfo"), requests.map { it.url.encodedPath })
        assertEquals("demo+code", requests.first().url.queryParameter("code"))
        assertNull(requests.first().header("Cookie"))
        assertEquals("1", requests.last().url.queryParameter("checkInit"))
        assertEquals("uid=200; username=New+User; token=exchange-refresh", requests.last().header("Cookie"))
        assertEquals("uid=200; username=New+User; token=verified-refresh", PrefManager.accountSnapshot().session?.cookie())
    }

    @Test fun cookieFromOneResponseAndNoOldCredentialOnReLogin() = runTest {
        save(OLD)
        PrefManager.logout()
        assertEquals("", PrefManager.refreshToken)
        assertNull(AccountSession.fromWebCookie("uid=200; uid=100; token=x"))
        assertNull(AccountSession.fromWebCookie("uid=200; username=New+User"))
        val candidate = requireNotNull(AccountSession.fromWebCookie("uid=200; token=web; username=New%20User"))
        val api = FakeApi(check = { _, cookie ->
            assertEquals("uid=200; username=New+User; token=web", cookie)
            assertFalse(PrefManager.isLogin)
            success(uid = "200", name = "New User")
        })
        assertEquals(Result.CONFIRMED, AccountRepository(api).loginWithCookie(candidate, PrefManager.messageAccountGeneration))
        assertEquals("", PrefManager.refreshToken)
        assertEquals("200", PrefManager.uid)
    }

    @Test fun startupIdentityMismatchClearsSessionButNetworkFailureDoesNot() = runTest {
        save(OLD)
        assertEquals(Result.UNAVAILABLE, AccountRepository(FakeApi(check = { _, _ -> throw IOException() })).checkStartup())
        assertTrue(PrefManager.isLogin)
        val mismatched = FakeApi(check = { init, cookie ->
            assertEquals(1, init)
            assertEquals(OLD.cookie(), cookie)
            success(uid = "100", name = "Old", adminType = 2)
        })
        assertEquals(Result.REJECTED, AccountRepository(mismatched).checkStartup())
        assertFalse(PrefManager.isLogin)
        assertEquals("", PrefManager.refreshToken)
    }

    @Test fun lateSuccessCannotReviveLoggedOutAccount() = runTest {
        save(OLD)
        val pending = CompletableDeferred<Response<CheckResponse>>()
        val started = CompletableDeferred<Unit>()
        val repository = AccountRepository(FakeApi(check = { _, _ -> started.complete(Unit); pending.await() }))
        val job = launch { assertEquals(Result.STALE, repository.checkStartup()) }
        started.await()
        PrefManager.logout()
        pending.complete(success(uid = "100", name = "Old"))
        job.join()
        assertFalse(PrefManager.isLogin)
    }

    @Test fun refusesChallengeOrIncompleteSessionAndDoesNotReuseOldRefreshToken() = runTest {
        assertFalse(AccountSession("0", "Name", "token").isValid)
        assertFalse(AccountSession("100", "", "token").isValid)
        assertTrue(AccountSession("100", "Old", "", refreshToken = "new").isValid)
        save(OLD)
        val proposed = success(uid = "200", name = "New User").body()!!
        val feedback = AccountRepository(FakeApi(check = { _, _ ->
            Response.success(proposed.copy(messageStatus = "err_request_captcha_v2"))
        }))
        assertEquals(Result.UNAVAILABLE, feedback.loginWithCookie(AccountSession("200", "New+User", "web"), PrefManager.messageAccountGeneration))
        assertEquals(OLD.cookie(), PrefManager.accountSnapshot().session?.cookie())
    }

    @Test fun addedLoginOnlySwitchesAfterConfirmationWithTheNewDevice() = runTest {
        save(OLD)
        val idA = "DU" + "a".repeat(34)
        val idB = "DU" + "b".repeat(34)
        assertTrue(PrefManager.saveSzlmId(idA, PrefManager.messageAccountGeneration))
        val generation = PrefManager.messageAccountGeneration
        val pending = requireNotNull(PrefManager.addAccountIdentity(idB))
        val candidate = AccountSession("200", "New+User", "web-token")
        val api = object : AccountApi {
            override suspend fun exchange(code: String, identity: AccountRequestIdentity?): Response<CheckResponse> =
                error("Unexpected exchange")
            override suspend fun check(checkInit: Int, cookie: String?, identity: AccountRequestIdentity?): Response<CheckResponse> {
                assertEquals(candidate.cookie(), cookie)
                assertEquals(idB, DeviceCode.decode(requireNotNull(identity).device).substringBefore(';'))
                assertEquals(idA, PrefManager.SZLMID)
                assertEquals(OLD.cookie(), PrefManager.accountSnapshot().session?.cookie())
                return success(uid = "200", name = "New User")
            }
        }
        val repository = AccountRepository(api)
        val reused = requireNotNull(PrefManager.addAccountIdentity(idA))
        assertEquals(Result.ID_IN_USE, repository.loginWithCookie(candidate, generation, reused))
        assertEquals(OLD.uid, PrefManager.uid)
        assertEquals(Result.CONFIRMED, repository.loginWithCookie(candidate, generation, pending))
        assertEquals(idB, PrefManager.SZLMID)
        assertTrue(AccountVault.switchTo(OLD.uid))
        assertEquals(idA, PrefManager.SZLMID)
    }

    private fun save(session: AccountSession) {
        assertTrue(PrefManager.replaceAccount(PrefManager.messageAccountGeneration, session))
    }

    private class FakeApi(val check: suspend (Int, String?) -> Response<CheckResponse>) : AccountApi {
        override suspend fun exchange(code: String, identity: AccountRequestIdentity?): Response<CheckResponse> = error("Unexpected exchange")
        override suspend fun check(checkInit: Int, cookie: String?, identity: AccountRequestIdentity?) = check.invoke(checkInit, cookie)
    }

    private fun success(uid: String, name: String, adminType: Int = 0): Response<CheckResponse> = Response.success(
        CheckResponse(null, null, null, CheckResponse.Data(null, null, null, uid, name, "fresh", null, null, null, adminType)),
    )

    companion object {
        private val OLD = AccountSession("100", "Old", "old-token", "old-refresh", adminType = 0)
    }
}
