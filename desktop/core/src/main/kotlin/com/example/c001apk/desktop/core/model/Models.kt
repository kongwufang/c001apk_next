package com.example.c001apk.desktop.core.model

import com.google.gson.JsonElement
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
 * 首页 `/v6/main/indexV8` 的条目。
 *
 * ⚠️ 实测：该接口的 `data` 是**卡片信封列表**，不是平铺动态：
 * ```
 * { "entityType": "card", "title": "", "entities": [ { …真实动态… }, … ] }
 * ```
 * 真实内容在 [entities] 里，可能是 feed / 商品 / 话题 / 图文 等不同结构。
 *
 * 为保留多态结构，这里用 [JsonElement] 承载 entities，由上层按需转换 ——
 * 不要在 core 里把它硬转成单一类型，否则会丢字段（踩过：早期版本直接当 List&lt;Feed&gt;
 * 解析，导致界面上出现结构错乱的条目）。
 */
data class HomeCard(
    @SerializedName("entityType") val entityType: String? = null,
    @SerializedName("entityId") val entityId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("lastupdate") val lastupdate: Long? = null,
    @SerializedName("entities") val entities: List<JsonElement>? = null,
    @SerializedName("extraData") val extraData: JsonElement? = null,
)

/**
 * 兼容旧用法的通用条目（保留字段），用于卡片内的实际内容。
 *
 * `message` 字段实测**含行内 HTML**，例如：
 * ```
 * 我一直好奇…为什么还 1.5k？ <a class="feed-link-tag" href="/t/%E9%85%B7%E5%AE%89%E5%A4%9C%E8%AF%9D?type=0">#酷安夜话#</a>
 * ```
 * 渲染前必须经 `HtmlText.parse()` 处理，否则界面上会直接显示标签源码。
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
    @SerializedName("messageTitle") val messageTitle: String? = null,
    @SerializedName("dateline") val dateline: Long? = null,
    @SerializedName("likenum") val likenum: Int? = null,
    @SerializedName("replynum") val replynum: Int? = null,
    @SerializedName("commentnum") val replynum2: Int? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("picArr") val picArr: List<String>? = null,
    @SerializedName("deviceTitle") val deviceTitle: String? = null,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("entities") val entities: List<JsonElement>? = null,
    @SerializedName("extraData") val extraData: JsonElement? = null,
)
