package com.example.c001apk.util

import com.example.c001apk.logic.model.CheckResponse

object CookieUtil {

    @Volatile var SESSID = ""

    internal fun clearAccountState() {
        SESSID = ""
        atme = null
        atcommentme = null
        feedlike = null
        contacts_follow = null
        message = null
        commentme = null
        badge = 0
        notification = 0
    }

    internal fun applyLoginCounts(count: CheckResponse.NotifyCount) {
        atme = count.atme
        atcommentme = count.atcommentme
        feedlike = count.feedlike
        contacts_follow = count.contactsFollow
        message = count.message
        commentme = count.commentme
        badge = count.unreadBadge
        notification = count.unreadNotification
    }

    var atme: Int? = null
    var atcommentme: Int? = null
    var feedlike: Int? = null
    var contacts_follow: Int? = null
    /**
     * 服务端未读总数（checkCount 的 badge_v18）。目前只做缓存、没有消费方：
     * 底部导航的角标已移除（那个位置现在叫「我的」，不再是消息页），
     * 未读提示统一走消息中心宫格，由「服务端分类未读 − 本机已读账本」算出。
     */
    var badge: Int = 0
    var notification: Int = 0

    /** 私信未读数（checkCount 的 message），消息中心宫格「私信」的红点读它 */
    var message: Int? = null

    /**
     * 评论回复未读数（checkCount 的 commentme）：动态 / 点评被评论，以及「回复了你的评论」。
     * 消息中心宫格「我的回复」和下方汇总列表的 commentMe 分类读的都是它。
     */
    var commentme: Int? = null

}