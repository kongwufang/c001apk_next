package com.example.c001apk.desktop.core.model

import com.google.gson.annotations.SerializedName

/**
 * 通用响应外壳。与 Android 端 `logic/model/Response.kt` 对应。
 *
 * 实测约定：
 *   * 成功：`status == 0`（部分接口不返回 status，此时 error 为 null 且有 data）
 *   * 失败：`status != 0`，如 `403` + message（风控/限流，曾出现 `暂时无法访问，请稍后重试(5902)`）
 *     或 `-419`（设备串无效）
 */
data class Resp<T>(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
)

/** 列表响应（`/v6/page/dataList`、话题等） */
data class ListResp<T>(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<T>? = null,
)

/**
 * 登录自检响应 `/v6/account/checkLoginInfo`（实测 12 个字段）。
 *
 * 服务端对未登录/失效凭据的响应：`message = "登录信息有误"`（此时 data 为 null）。
 * Android 端判定登录只看 `data.token != null`，官方端则比较 `uid + username + adminType`。
 */
data class LoginInfo(
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null,
    @SerializedName("userAvatar") val userAvatar: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("mobile") val mobile: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("adminType") val adminType: Int? = null,
    @SerializedName("isBanned") val isBanned: Int? = null,
    @SerializedName("systemConfig") val systemConfig: SystemConfig? = null,
    @SerializedName("unreadMessage") val unreadMessage: Int? = null,
) {
    data class SystemConfig(
        @SerializedName("isOpenRefreshToken") val isOpenRefreshToken: Int? = null,
    )
}

/** 未读计数 `/v6/notification/checkCount` */
data class CheckCount(
    @SerializedName("badge") val badge: Int? = null,
    @SerializedName("message") val message: Int? = null,
    @SerializedName("reply") val reply: Int? = null,
    @SerializedName("atMe") val atMe: Int? = null,
    @SerializedName("atCommentMe") val atCommentMe: Int? = null,
    @SerializedName("commentMe") val commentMe: Int? = null,
    @SerializedName("likeMe") val likeMe: Int? = null,
    @SerializedName("feedLike") val feedLike: Int? = null,
    @SerializedName("dateline") val dateline: Long? = null,
)

/**
 * 动态条目。列表与详情共用大量字段，桌面端先按实际用到的字段建模，
 * 其余留待按需补充（Gson 会忽略未声明字段）。
 */
data class Feed(
    @SerializedName("id") val id: String? = null,
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("userAvatar") val userAvatar: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("messageHtml") val messageHtml: String? = null,
    @SerializedName("messageTitle") val messageTitle: String? = null,
    @SerializedName("dateline") val dateline: Long? = null,
    @SerializedName("likenum") val likenum: Int? = null,
    @SerializedName("replynum") val replynum: Int? = null,
    @SerializedName("forwardnum") val forwardnum: Int? = null,
    @SerializedName("feedType") val feedType: String? = null,
    @SerializedName("entityType") val entityType: String? = null,
    @SerializedName("type") val type: Int? = null,
    @SerializedName("deviceTitle") val deviceTitle: String? = null,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("fromApi") val fromApi: String? = null,
    @SerializedName("isStick") val isStick: Int? = null,
    @SerializedName("isHeadline") val isHeadline: Int? = null,
    @SerializedName("picArr") val picArr: List<String>? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("frid") val frid: String? = null,
    @SerializedName("userInfo") val userInfo: UserInfo? = null,
    @SerializedName("liked") val liked: Int? = null,
    @SerializedName("isLiked") val isLiked: Int? = null,
)

/** 用户信息（列表内嵌 / 用户资料接口共用一部分字段） */
data class UserInfo(
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("userAvatar") val userAvatar: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("verify_status") val verifyStatus: String? = null,
    @SerializedName("isLogin") val isLogin: Int? = null,
    @SerializedName("fans") val fans: Int? = null,
    @SerializedName("follow") val follow: Int? = null,
    @SerializedName("feed") val feed: Int? = null,
)

/**
 * 首页头条 `/v6/main/indexV8`。
 * 该接口 `data` 是**条目数组**，条目内容按 `entityType` 区分（feed / 商品 / 话题等），
 * 未声明的字段由 Gson 忽略，取值时用 [entityType] 判断。
 */
data class HomeEntity(
    @SerializedName("entityId") val entityId: String? = null,
    @SerializedName("entityType") val entityType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("userAvatar") val userAvatar: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("messageHtml") val messageHtml: String? = null,
    @SerializedName("dateline") val dateline: Long? = null,
    @SerializedName("likenum") val likenum: Int? = null,
    @SerializedName("replynum") val replynum: Int? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("picArr") val picArr: List<String>? = null,
    @SerializedName("deviceTitle") val deviceTitle: String? = null,
)
