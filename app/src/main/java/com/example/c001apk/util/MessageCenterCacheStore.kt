package com.example.c001apk.util

import com.example.c001apk.MyApplication.Companion.context
import com.example.c001apk.ui.message.MessageCenterAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest

/** Per-account, authoritative snapshots. Unowned legacy cache is never displayed or migrated. */
object MessageCenterCacheStore {
    private const val MAX_ITEMS = 200
    private val gson = Gson()

    private fun file(uid: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "message_center_$key.json")
    }

    @Synchronized
    fun load(account: MessageAccount): List<MessageCenterAdapter.Item> {
        if (!account.isCurrent()) return emptyList()
        return runCatching {
            // Its owner cannot be established, so do not guess it from the current login.
            File(context.filesDir, "message_center_cache.json").delete()
            File(context.filesDir, "message_center_cache.json.tmp").delete()
            val f = file(account.uid)
            if (!f.exists()) return emptyList()
            val type = object : TypeToken<List<MessageCenterAdapter.Item>>() {}.type
            val list: List<MessageCenterAdapter.Item>? = gson.fromJson(f.readText(), type)
            if (!account.isCurrent()) emptyList()
            else MessageSnapshot.normalize(list.orEmpty()).take(MAX_ITEMS)
        }.getOrDefault(emptyList())
    }

    /** Empty success clears stale rows too; callers must not save failed fetches as empty. */
    @Synchronized
    fun save(account: MessageAccount, items: List<MessageCenterAdapter.Item>) {
        if (!account.isCurrent()) return
        runCatching {
            val f = file(account.uid)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(gson.toJson(MessageSnapshot.normalize(items).take(MAX_ITEMS)))
            if (!account.isCurrent()) {
                tmp.delete()
                return
            }
            if (!tmp.renameTo(f)) tmp.delete()
        }
    }
}
