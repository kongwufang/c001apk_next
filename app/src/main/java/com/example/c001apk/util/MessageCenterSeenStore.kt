package com.example.c001apk.util

import android.content.Context.MODE_PRIVATE
import com.example.c001apk.MyApplication.Companion.context

/**
 * 消息中心的「展示即已读」本地账本。
 *
 * 服务端只在 `/v6/notification/checkCount` 里给**分类未读数**，不给逐条已读标记
 * （实测 2026-10-05：`/v6/notification/list` 一页 20 条 `isnew` 全是 0，
 * `atMeList` / `atCommentMeList` / `feedLikeList` / `contactsFollowList`
 * 连 `isnew` 字段都没有），所以「哪几条看过了」只能本机自己记：
 *
 * - [markSeen]：把已展示的条目记下来，下次聚合未读时直接跳过，不会再出现；
 * - [seenCount]：该分类在本机已抵消的未读数，宫格红点显示
 *   `max(0, 服务端未读 - 已抵消)`，这样「看过了」的红点会立刻消失。
 *
 * 与 coolapk-desktop 的 `notificationSeen.ts` 是同一套语义：
 * 未读 = 该分类列表最前面的 N 条，看完把 N 记成已抵消。
 */
object MessageCenterSeenStore {

    private const val PREF = "message_center_seen"
    private const val KEY_IDS = "seen_ids"
    private const val KEY_COUNT = "seen_count_"

    /** 已读条目最多记这么多条，超了丢最早的一批（服务端未读远小于这个量级） */
    private const val MAX_SEEN = 300

    // Unowned legacy ledger cannot be attributed to whichever account happens to log in next.
    private val pref get() = context.getSharedPreferences(
        PREF + "_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest((MessageAccount.current()?.uid ?: "logged-out").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }, MODE_PRIVATE)

    /** 这条通知本机是不是已经看过 */
    fun isSeen(category: String, id: String): Boolean =
        pref.getStringSet(KEY_IDS, emptySet())?.contains(key(category, id)) == true

    /** 批量标记已读，返回本次**新增**的条数（用于同步抵消计数，重复标记不会重复加） */
    fun markSeen(category: String, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val seen = pref.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        var added = 0
        ids.forEach { if (seen.add(key(category, it))) added++ }
        val trimmed = if (seen.size > MAX_SEEN) seen.take(MAX_SEEN).toSet() else seen
        pref.edit().putStringSet(KEY_IDS, trimmed).apply()
        return added
    }

    /** 该分类在本机已抵消的未读数 */
    fun seenCount(category: String): Int = pref.getInt(KEY_COUNT + category, 0)

    /** 宫格红点用：服务端未读减掉本机已读，负数按 0 */
    fun unreadOf(category: String, serverCount: Int): Int =
        (serverCount - seenCount(category)).coerceAtLeast(0)

    fun addSeenCount(category: String, delta: Int) {
        if (delta <= 0) return
        pref.edit().putInt(KEY_COUNT + category, seenCount(category) + delta).apply()
    }

    /**
     * 与服务端计数对齐：服务端未读降到本地抵消数以下（通知被删、或在别处读过）
     * 就收敛，服务端归零时把本地抵消一起清掉。
     */
    fun syncSeenCount(category: String, serverCount: Int) {
        val stored = seenCount(category)
        val available = serverCount.coerceAtLeast(0)
        when {
            available == 0 -> pref.edit().remove(KEY_COUNT + category).apply()
            stored > available -> pref.edit().putInt(KEY_COUNT + category, available).apply()
        }
    }

    /** 退出登录时清空账本 */
    fun clear() = pref.edit().clear().apply()

    private fun key(category: String, id: String) = "$category:$id"
}
