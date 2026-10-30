package com.example.c001apk.util

import android.text.Html
import com.example.c001apk.logic.model.MessageResponse

/**
 * 私信会话（`/v6/message/list`）的字段提取工具。
 *
 * 酷安 API 里 `fromuid` = 消息发送者、`uid` = 消息接收者，所以会话项上的
 * `username` / `userAvatar` 有可能指向我自己（最后一条消息是我发的）。
 * 对方信息要优先认 `messageUid` / `messageUsername` / `messageUserAvatar`，
 * 再退回 `from*`，最后才用裸字段兜底。
 */
object MessageKit {

    /**
     * 列表类型判断。⚠️ 布局里**不能**写 `type == \`list\``：
     * databinding 生成的代码对 String 的 `==` 是引用比较（不是 equals），
     * 传进来的 "list" 和字面量不是同一个对象，恒为 false——
     * 实测私信列表会因此走 else 分支，把「对方」显示成我自己（最后一条是我发的那些会话）。
     * 所有类型判断统一走这里（Kotlin 的 == 是真 equals）。
     */
    @JvmStatic
    fun isList(type: String?): Boolean = type == "list"

    /** 好友关注列表（第二行显示「关注了你」） */
    @JvmStatic
    fun isContactsFollow(type: String?): Boolean = type == "contactsFollow"

    /** 对方的 uid */
    @JvmStatic
    fun partnerUid(data: MessageResponse.Data?): String {
        if (data == null) return ""
        data.messageUid?.takeIf { it.isNotBlank() }?.let { return it }
        val mine = PrefManager.uid
        return listOf(data.fromuid, data.uid)
            .firstOrNull { !it.isNullOrBlank() && it != mine }
            .orEmpty()
    }

    /** 对方昵称 */
    @JvmStatic
    fun partnerName(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return data.messageUsername ?: data.fromusername ?: data.username ?: ""
    }

    /** 对方头像 */
    @JvmStatic
    fun partnerAvatar(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return data.messageUserAvatar ?: data.fromUserAvatar ?: ""
    }

    /**
     * 通知条目发送方的认证图标。
     *
     * 认证三件套在 V18 通知流的 `fromUserInfo` 对象里（[MessageResponse.FromUserInfo]），
     * 老 `/v6/notification/list`、私信会话 `/v6/message/list` 没见下发，那时是空 ——
     * 角标会退回只按 uid 查云端认证表。这两个方法专门替布局挡掉
     * 「嵌套对象可能为空」的那层解引用。
     */
    @JvmStatic
    fun verifyIcon(data: MessageResponse.Data?): String? = data?.fromUserInfo?.verifyIcon

    /**
     * 通知条目发送方的认证状态，取值含义见
     * [com.example.c001apk.util.VerifyBadge.isVerified]。
     */
    @JvmStatic
    fun verifyStatus(data: MessageResponse.Data?): Int? = data?.fromUserInfo?.verifyStatus

    /**
     * 聊天页认证角标用的 uid。
     *
     * 自己发的消息、系统提示这两种情况下对方头像（`avatarLeft`）是 GONE 的，角标得跟着一起收，
     * 不然会孤零零浮在气泡左边；这里统一给空串 —— 空 uid 在云端认证表里查不到，
     * [com.example.c001apk.util.VerifyBadge.isVerified] 自然判成没认证。
     *
     * 角标不给「我的头像」那一侧：聊天记录里没有对方（也没有自己）的认证字段，
     * 能用的只有会话带进来的对方 uid，查云端认证表。
     */
    @JvmStatic
    fun badgeUid(data: MessageResponse.Data?, isMe: Boolean, partnerUid: String?): String =
        if (isMe || isExtra(data)) "" else partnerUid.orEmpty()

    /**
     * 是不是「系统提示」条目（比如「关注对方即可无限制聊天」）。
     * 这种条目居中显示灰字、不出气泡。
     *
     * 注意：布局里不能写 `data.entityType == \`messageExtra\``，
     * databinding 对 String 的 `==` 生成的是 Java 的引用比较（不是 equals），
     * Gson 解析出来的字符串没被 intern，恒为 false。
     */
    @JvmStatic
    fun isExtra(data: MessageResponse.Data?): Boolean = data?.entityType == "messageExtra"

    /** 系统提示的文字：多数在 `message` 里，有些只给了 `title` */
    @JvmStatic
    fun extraText(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return if (data.message.isNullOrBlank()) data.title.orEmpty() else data.message
    }

    /**
     * 是不是「酷安小秘书」——官方机器人（uid 10086），只推送登录提醒和站内信，不是私信对象。
     *
     * 实测（2026-10-05）：`GET /v6/message/list` 的 18 条会话里**没有**小秘书，
     * 它的消息全在 `GET /v6/notification/list` 里（`type=notify_xms`、`fromuid=10086`）。
     * 服务端不认 `type` / `fromuid` 过滤参数（照旧返回全部 20 条），只能本地筛。
     *
     * 昵称必须全等——用 contains 会让名字里带「小秘书」的普通用户被误判成机器人。
     */
    @JvmStatic
    fun isSecretaryName(name: String?): Boolean = name == "酷安小秘书"

    /** 通知条目是不是小秘书发的 */
    @JvmStatic
    fun isSecretaryNotify(data: MessageResponse.Data?): Boolean =
        data?.type == "notify_xms" || data?.fromuid == "10086"

    /** 图片消息：`message_pic` 非空就是一张图，气泡里要把文字那行换成图片 */
    @JvmStatic
    fun hasPic(data: MessageResponse.Data?): Boolean = !data?.messagePic.isNullOrEmpty()

    /**
     * 这条消息的图片是不是**本地 uri**（乐观发送的临时消息）。
     *
     * 正常消息的 `message_pic` 是 OSS 对象名（`/message/2026/1006/x.png`），
     * 要现问 `showImage` 换带 auth_key 的签名地址 ✗
     * 而本地乐观消息的 `message_pic` 直接塞的是相册 uri（`content://…`）✓
     * adapter 靠这个区分：本地 uri 直接交给 Glide，别去调 `showImage`（会 404）
     */
    @JvmStatic
    fun isLocalPic(data: MessageResponse.Data?): Boolean {
        val p = data?.messagePic ?: return false
        return p.startsWith("content://") || p.startsWith("file://")
    }

    /**
     * 图片消息取原图要用消息 id（`/v6/message/showImage?id=`）。
     * `/v6/message/chat` 里 `id` 偶尔为空，退到 `entityId`（两者是同一个值）。
     */
    @JvmStatic
    fun picId(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return data.id.ifEmpty { data.entityId.orEmpty() }
    }

    /** 会话项是不是小秘书 */
    @JvmStatic
    fun isSecretary(data: MessageResponse.Data?): Boolean = isSecretaryName(partnerName(data))

    /**
     * 会话列表里的最后一条消息预览：只发图片时 message 是空的，得显示 [图片]。
     *
     * 正文要先降级成纯文本：服务端把消息当 HTML 下发，带链接的消息是
     * `<a class="feed-link-url" href="…">查看链接</a>`，直接显示会在列表里
     * 露出一整串标签。聊天页那侧不用处理——气泡走 LinkTextView，本来就渲染 HTML。
     */
    @JvmStatic
    fun preview(data: MessageResponse.Data?): String {
        if (data == null) return ""
        if (!data.message.isNullOrBlank()) return plainText(data.message)
        if (!data.messagePic.isNullOrBlank()) return "[图片]"
        return ""
    }

    /**
     * HTML → 单行纯文本。没有 `<` 时只还原实体，避免把正文里正常的
     * `<` / `&` 当成标签吃掉。布局里这个 TextView 是 maxLines=1，换行压成空格。
     */
    private fun plainText(html: String): String {
        val text =
            if (html.indexOf('<') < 0) MarkdownUtils.decodeEntities(html)
            else Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
        return text.replace('\n', ' ').trim()
    }
    /**
     * 气泡里的一张图片：`showImage` 换来的签名地址 + 图片原始像素宽高。
     * 宽高是给 `ImageUtil.chatImageSize` 算气泡占位用的（之前死宽 180dp，横竖图都一个样）。
     * 量不出来时给 0，那边会退成正方形占位。
     */
    data class MessagePic(val url: String, val width: Int, val height: Int)
}
