package com.example.c001apk.desktop.core.net

import com.example.c001apk.desktop.core.model.CheckCount
import com.example.c001apk.desktop.core.model.Feed
import com.example.c001apk.desktop.core.model.HomeCard
import com.example.c001apk.desktop.core.model.LoginInfo
import com.example.c001apk.desktop.core.model.Resp
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 酷安 API 接口声明。对应 Android 端 `logic/network/ApiService.kt`（只收录桌面端已用到的）。
 *
 * ★ 域名分流（与 Android 端 `NetworkModule` 一致，别混用）：
 *   * `https://api.coolapk.com`   —— 大部分业务接口
 *   * `https://api2.coolapk.com`  —— **详情 / 评论**等
 *     （Android 端 `Network.kt:27` 用 `api2Service.getFeedContent(...)`；
 *       抓包 HAR 里成功的详情请求 Host 也是 `api2.coolapk.com`）
 *
 * 实测：两个域名对详情/评论**都能成功**，但按源码口径走 api2 更稳妥。
 */
interface ApiService {

    // ─────────────── 账号 ───────────────

    /** 登录自检。是唯一能可靠判断登录态失效的接口（失效时 message = "登录信息有误"）。 */
    @GET("/v6/account/checkLoginInfo")
    fun checkLoginInfo(): Call<Resp<LoginInfo>>

    /** 未读计数 */
    @GET("/v6/notification/checkCount")
    fun checkCount(): Call<Resp<CheckCount>>

    /** 账号配置（如 my_page_card_config） */
    @GET("/v6/account/loadConfig")
    fun loadConfig(
        @Query("key") key: String,
        @Query("refresh") refresh: Int = 0,
    ): Call<Resp<Any>>

    // ─────────────── 首页 / 列表 ───────────────

    /** 首页头条 */
    @GET("/v6/main/indexV8")
    fun indexV8(
        @Query("page") page: Int,
        @Query("firstLaunch") firstLaunch: Int = 0,
    ): Call<Resp<List<HomeCard>>>

    /** 通用数据列表（话题、我的设备、动态页等都用它 + url 参数） */
    @GET("/v6/page/dataList")
    fun dataList(
        @Query("url") url: String,
        @Query("page") page: Int? = null,
        @Query("hotType") hotType: String? = null,
        @Query("recommend") recommend: Int? = null,
        @Query("firstLaunch") firstLaunch: Int? = null,
    ): Call<Resp<List<HomeEntity>>>

    // ─────────────── 动态 ───────────────

    /**
     * 动态详情。
     *
     * `rid` 是列表项带过来的「回复锚点」，可为空。
     * ⚠️ 该接口会限流：高频调用会返回 `403 暂时无法访问，请稍后重试(5902)`。
     *    调用方需做缓存与退避重试（见 _rev/win_ref/DETAIL_5902.md 第 5 节）。
     */
    @GET("/v6/feed/detail")
    fun getFeedContent(
        @Query("id") id: String,
        @Query("rid") rid: String? = null,
    ): Call<Resp<Feed>>

    /** 评论列表。参数口径照 Android 端 `ApiService.getFeedContentReply`。 */
    @GET("/v6/feed/replyList")
    fun getFeedContentReply(
        @Query("id") id: String,
        @Query("listType") listType: String? = null,
        @Query("page") page: Int = 1,
        @Query("firstItem") firstItem: String? = null,
        @Query("lastItem") lastItem: String? = null,
        @Query("discussMode") discussMode: Int = 1,
        @Query("feedType") feedType: String = "feed",
        @Query("blockStatus") blockStatus: Int = 0,
    ): Call<Resp<List<Feed>>>

    // ─────────────── 用户 ───────────────

    @GET("/v6/user/feedList")
    fun getUserFeedList(
        @Query("uid") uid: String,
        @Query("page") page: Int = 1,
    ): Call<Resp<List<Feed>>>

    @GET("/v6/user/likeList")
    fun getUserLikeList(
        @Query("uid") uid: String,
        @Query("page") page: Int = 1,
    ): Call<Resp<List<Feed>>>

    @GET("/v6/user/profile")
    fun getUserProfile(@Query("uid") uid: String): Call<Resp<UserProfile>>

    @GET("/v6/user/hitHistoryList")
    fun getHitHistoryList(@Query("page") page: Int = 1): Call<Resp<List<Feed>>>

    @GET("/v6/user/blackList")
    fun getBlackList(@Query("page") page: Int = 1): Call<Resp<List<Any>>>

    @GET("/v6/user/spamWordList")
    fun getSpamWordList(): Call<Resp<Any>>

    // ─────────────── 收藏 ───────────────

    @GET("/v6/collection/list")
    fun getCollectionList(
        @Query("showDefault") showDefault: Int = 0,
        @Query("page") page: Int = 1,
    ): Call<Resp<List<Collection>>>

    data class Collection(
        @com.google.gson.annotations.SerializedName("id") val id: String? = null,
        @com.google.gson.annotations.SerializedName("title") val title: String? = null,
        @com.google.gson.annotations.SerializedName("pic") val pic: String? = null,
        @com.google.gson.annotations.SerializedName("likenum") val likenum: Int? = null,
    )

    data class UserProfile(
        @com.google.gson.annotations.SerializedName("uid") val uid: String? = null,
        @com.google.gson.annotations.SerializedName("username") val username: String? = null,
        @com.google.gson.annotations.SerializedName("userAvatar") val userAvatar: String? = null,
        @com.google.gson.annotations.SerializedName("level") val level: String? = null,
        @com.google.gson.annotations.SerializedName("fans") val fans: Int? = null,
        @com.google.gson.annotations.SerializedName("follow") val follow: Int? = null,
        @com.google.gson.annotations.SerializedName("feed") val feed: Int? = null,
        @com.google.gson.annotations.SerializedName("bio") val bio: String? = null,
        @com.google.gson.annotations.SerializedName("verify_status") val verifyStatus: String? = null,
        @com.google.gson.annotations.SerializedName("isLogin") val isLogin: Int? = null,
        @com.google.gson.annotations.SerializedName("email") val email: String? = null,
        @com.google.gson.annotations.SerializedName("mobile") val mobile: String? = null,
    )
}
