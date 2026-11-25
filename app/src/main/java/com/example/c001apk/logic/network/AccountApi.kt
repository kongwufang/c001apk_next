package com.example.c001apk.logic.network

import com.example.c001apk.logic.model.CheckResponse
import com.example.c001apk.util.AddCookiesInterceptor
import com.example.c001apk.util.AccountRequestIdentity
import retrofit2.http.Tag
import com.example.c001apk.util.SslVerify
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Isolated client: no auth URL/header/body logging, ambient account cookies or redirects. */
internal interface AccountApi {
    @GET("/v6/account/accessToken")
    suspend fun exchange(
        @Query("code") code: String,
        @Tag identity: AccountRequestIdentity? = null,
    ): Response<CheckResponse>

    @GET("/v6/account/checkLoginInfo")
    suspend fun check(
        @Query("checkInit") checkInit: Int,
        @Header("Cookie") cookie: String?,
        @Tag identity: AccountRequestIdentity? = null,
    ): Response<CheckResponse>

    companion object {
        val instance: AccountApi by lazy {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain -> AddCookiesInterceptor.intercept(chain, useSavedCookies = false) }
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(20, TimeUnit.SECONDS)
                .let(SslVerify::applyStrict)
                .build()
            Retrofit.Builder()
                .baseUrl("https://api.coolapk.com/")
                .addConverterFactory(GsonConverterFactory.create(LenientGson.gson))
                .client(client)
                .build()
                .create(AccountApi::class.java)
        }
    }
}
