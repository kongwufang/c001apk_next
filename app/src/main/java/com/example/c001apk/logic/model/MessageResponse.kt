package com.example.c001apk.logic.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class MessageResponse(
    val status: Int?,
    val error: Int?,
    val message: String?,
    val messageStatus: Int?,
    val data: List<Data>?
) {

    data class Data(
        val rid: Long? = null,
        val infoHtml: String? = null,
        val entityType: String = "",
        val id: String = "",
        val uid: String = "",
        val dateline: Long = 0L,
        val message: String = "",
        val username: String = "",
        val forwardid: String = "",
        @SerializedName("source_id") val sourceId: String = "",
        val pic: String = "",
        val istag: Int = 0,
        val tags: String = "",
        val likenum: String = "",
        val commentnum: String = "",
        val replynum: String = "",
        val favnum: String = "",
        @SerializedName("device_title") val deviceTitle: String = "",
        val userAvatar: String = "",
        val title: String = "",
        val picArr: List<String>? = null,
        val userAction: HomeFeedResponse.UserAction? = null,
        val forwardSourceFeed: ForwardSourceFeed? = null,
        val feed: Feed? = null,
        val likeUsername: String = "",
        val likeUid: String = "",
        val likeTime: Long = 0L,
        val likeAvatar: String = "",
        val fid: String = "",
        val fromUserAvatar: String = "",
        val fromusername: String = "",
        val fromuid: String = "",
        val note: String = "",
        /**
         * 下面三个只有 `/v6/notificationV18/list` 会下发，而且是下划线命名（老接口没有）。
         *
         * V18 是官方消息中心现在在用的统一通知流。它跟老 `/v6/notification/list` 的差别只有三处：
         * 分类叫 `note_type`（不是 `type`）、发送者叫 `from_uid`（不是 `fromuid`）、
         * 正文 `note` 是**纯文本**（老接口是带 `<a>` 的 HTML）。
         * 其余键名（id / entityType / dateline / note / url / slug / fromUserAvatar /
         * fromusername / uid）两边一模一样，所以能直接反序列化进这个模型；
         * 不一致的那几处由 [com.example.c001apk.util.NotificationV18Kit.toMessage] 归一化。
         */
        @SerializedName("note_type") val noteType: String? = null,
        /** 服务端给的中文分类标题，如「评论了你的动态」（系统 / 活动消息为空） */
        @SerializedName("noteTypeTitle") val noteTypeTitle: String? = null,
        @SerializedName("from_uid") val fromUid: String? = null,
        /**
         * V18 的发送者昵称。老接口写 `fromusername`（没有下划线），V18 是下划线写法，
         * 两个键不是一回事，所以单独声明。
         */
        @SerializedName("from_username") val fromUsername: String? = null,
        /** V18 通知的目标对象。我收到的赞里是被赞的动态（`target_type=feed`）、值就是动态 id */
        @SerializedName("target_id") val targetId: Long? = null,
        /** V18 的附加图：我收到的赞里是被赞动态的封面图 */
        @SerializedName("addition_info") val additionInfo: String? = null,
        /**
         * 通知条目的细分类型：`notify_xms`（酷安小秘书）/ `feed_reply` / `rating_reply` …
         * 只有 `/v6/notification/` 下的接口会下发，私信会话不返回。
         */
        val type: String? = null,
        /** 通知条目的唯一标识，删除通知时用它 */
        val slug: String? = null,
        /** 通知里挂的跳转地址，如 `/u/10086` */
        val url: String? = null,
        /** 私信会话标识，聊天页靠它拉历史记录 */
        val ukey: String? = null,
        /** 私信会话未读数（只有 /v6/message/list 返回） */
        @SerializedName("unreadNum") val unreadNum: Int? = null,
        /** 私信会话是否有新消息（只有 /v6/message/list 返回） */
        @SerializedName("isnew") val isnew: Int? = null,
        /** 私信里的图片消息，纯文本消息为空 */
        @SerializedName("message_pic") val messagePic: String? = null,
        /** 聊天记录里每条消息的真实主键（列表 DiffUtil 用，比 `id` 更可靠） */
        val entityId: String? = null,
        /**
         * 会话列表里**对方**的 uid / 昵称 / 头像。
         * 因为 fromuid = 发送者、uid = 接收者，会话项上的 username / userAvatar
         * 指向的可能是我自己（最后一条是我发的），所以对方信息要认这三个字段。
         */
        @SerializedName("messageUid") val messageUid: String? = null,
        @SerializedName("messageUsername") val messageUsername: String? = null,
        @SerializedName("messageUserAvatar") val messageUserAvatar: String? = null,
        /**
         * V18 通知里**发送方**的完整用户对象，认证三件套就在它里面（`verify_status` /
         * `verify_icon` / `verify_title`）。`/v6/notificationV18/likeList` 实测每条都带
         * （2026-10-07 抓包），所以「我收到的赞」的头像角标认它；
         * 老 `/v6/notification/list` 与 `/v6/message/list` 没见下发，那时它为空，
         * 角标退回按 uid 查云端认证表（见 [com.example.c001apk.util.VerifyBadge]）。
         */
        @SerializedName("fromUserInfo") val fromUserInfo: FromUserInfo? = null
    )

    /**
     * 通知条目里的用户对象。只声明渲染用得上的几个键：同一个对象在别的接口里
     * 是完整用户实体（一大串字段），这里只要认证三件套 —— 少声明一些非空字段
     * 反而更安全，Gson 反射填值不走构造器，没下发的键会直接留 null。
     */
    data class FromUserInfo(
        @SerializedName("verify_status") val verifyStatus: Int? = null,
        @SerializedName("verify_icon") val verifyIcon: String? = null,
        @SerializedName("verify_title") val verifyTitle: String? = null
    )

    /**
     * 写操作（发私信 / 标记已读）的响应包。
     * 只取 status / message，`data` 的形状不稳定（有时是对象有时是数组），故意不声明。
     */
    data class ActionResponse(
        val status: Int?,
        val error: Int?,
        val message: String?,
        val messageStatus: String?
    )

    data class Feed(
        val id: String,
        val uid: String,
        val username: String,
        val message: String,
        val pic: String?,
        val url: String
    )

    @Parcelize
    data class ForwardSourceFeed(
        val entityType: String,
        val feedType: String,
        val id: String,
        val username: String,
        val uid: String,
        val message: String,
        @SerializedName("message_title") val messageTitle: String,
        val pic: String,
        val picArr: List<String>?,
    ): Parcelable
}

