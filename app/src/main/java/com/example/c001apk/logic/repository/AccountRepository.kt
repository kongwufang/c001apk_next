package com.example.c001apk.logic.repository

import com.example.c001apk.logic.model.AccountSession
import com.example.c001apk.logic.model.CheckResponse
import com.example.c001apk.logic.network.AccountApi
import com.example.c001apk.util.CookieUtil
import com.example.c001apk.util.PrefManager
import kotlinx.coroutines.CancellationException
import okhttp3.Cookie
import retrofit2.Response

internal class AccountRepository(private val api: AccountApi = AccountApi.instance) {
    internal enum class Result { CONFIRMED, REJECTED, UNAVAILABLE, STALE }

    suspend fun loginWithCode(code: String, generation: Long): Result = request {
        val exchange = api.exchange(code)
        val proposed = eligibleData(exchange)?.let { AccountSession.fromResponse(it) }
            ?.takeIf { it.isValid } ?: return@request failure(exchange)
        confirm(proposed, generation, fromWebCookie = false)
    }

    suspend fun loginWithCookie(candidate: AccountSession, generation: Long): Result = request {
        confirm(candidate, generation, fromWebCookie = true)
    }

    private suspend fun confirm(candidate: AccountSession, generation: Long, fromWebCookie: Boolean): Result {
        val response = api.check(checkInit = 1, cookie = candidate.cookie())
        val data = eligibleData(response) ?: return failure(response)
        val session = AccountSession.fromResponse(data)
        if (!session.isValid || !sameIdentity(candidate, session, compareName = !fromWebCookie)) return Result.REJECTED
        return if (save(generation, session, data.notifyCount)) Result.CONFIRMED else Result.STALE
    }

    /** Signed-out check still obtains a guest SESSID, but can never sign into an account. */
    suspend fun checkStartup(checkInit: Int = 1): Result = request {
        val snapshot = PrefManager.accountSnapshot()
        val prior = snapshot.session
        if (prior != null && !prior.isValid) return@request invalidate(snapshot.generation)
        val response = api.check(checkInit, prior?.cookie() ?: CookieUtil.SESSID.ifBlank { null })
        if (prior == null) {
            synchronized(PrefManager) {
                if (PrefManager.messageAccountGeneration != snapshot.generation) return@request Result.STALE
                if (response.isSuccessful) {
                    Cookie.parseAll(response.raw().request.url, response.headers())
                        .firstOrNull { it.name == "SESSID" && it.value.isNotEmpty() }
                        ?.let { CookieUtil.SESSID = "${it.name}=${it.value}" }
                }
            }
            return@request Result.UNAVAILABLE
        }
        val data = eligibleData(response)
        if (data == null) {
            return@request if (failure(response) == Result.REJECTED) invalidate(snapshot.generation)
            else Result.UNAVAILABLE
        }
        val updated = AccountSession.fromResponse(data)
        if (!updated.isValid) return@request Result.UNAVAILABLE
        if (!sameIdentity(prior, updated)) return@request invalidate(snapshot.generation)
        if (save(snapshot.generation, updated, data.notifyCount)) Result.CONFIRMED else Result.STALE
    }

    private fun save(generation: Long, session: AccountSession, counts: CheckResponse.NotifyCount?): Boolean =
        synchronized(PrefManager) {
            if (!PrefManager.replaceAccount(generation, session)) return@synchronized false
            counts?.let(CookieUtil::applyLoginCounts)
            true
        }

    private fun invalidate(generation: Long): Result =
        if (PrefManager.replaceAccount(generation, null)) Result.REJECTED else Result.STALE

    private suspend inline fun request(block: () -> Result): Result = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Throwable messages (Retrofit/OkHttp) may contain credentials. Never log them.
        Result.UNAVAILABLE
    }

    companion object {
        private val challengeStatuses = setOf("err_request_captcha", "err_request_mobile", "err_request_captcha_v2")

        internal fun eligibleData(response: Response<CheckResponse>): CheckResponse.Data? =
            response.body()?.takeIf {
                response.isSuccessful && (it.status == null || it.status == 1) &&
                    it.messageStatus !in challengeStatuses
            }?.data

        internal fun failure(response: Response<CheckResponse>): Result {
            val body = response.body()
            // Keep the installed session on timeouts, 5xx, captcha/risk-control or unknown responses.
            return if (response.code() == 401 || body?.message?.trim() in
                setOf("登录信息有误", "信息错误，请重新登录")) Result.REJECTED else Result.UNAVAILABLE
        }

        internal fun sameIdentity(expected: AccountSession, actual: AccountSession, compareName: Boolean = true): Boolean =
            expected.uid == actual.uid && (!compareName || expected.username == actual.username) &&
                (expected.adminType == null || expected.adminType == actual.adminType)
    }
}
