package com.example.c001apk.util

import com.example.c001apk.MyApplication.Companion.context
import com.example.c001apk.ui.message.MessageCenterAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 消息中心的**列表缓存**（2026-10-24 ✓）
 *
 * ## 为什么需要
 * 消息中心每次打开都要并发拉 5~7 个分类接口 ✗ ⇒ 冷启动时列表是**空的** ✗
 * ⇒ 用户要盯着空白等一两秒 ✗
 *
 * ⇒ 先把**上次拉到的那批**显示出来 ✓ 后台再去拉 ✓
 * ⇒ 有新的就由 `ListAdapter` + `DiffUtil` **增量插入** ✓（不用重置整个列表 ✓）
 *
 * ## 为什么用文件而不是 SharedPreferences
 * 要存的是 `List<MessageCenterAdapter.Item>`（里面带 `MessageResponse.Data` ✓）
 * ⇒ 序列化成 JSON 几 KB~几十 KB ✗ SharedPreferences 存这么大的字符串不合适 ✗
 * ⇒ 写到 `filesDir/message_center_cache.json` ✓
 *
 * ## 为什么不存原始响应
 * `NetworkRepo` 已经把响应**归一化**成 `MessageResponse.Data` ✓（见 `NotificationV18Kit`）
 * ⇒ 直接存归一化后的对象 ✓ 读出来就能用 ✓ 不用再归一遍 ✓
 *
 * ## 上限
 * 只留最近 [MAX_ITEMS] 条 ✓（按 `dateline` 倒序 ✓）—— 再多也没人翻到 ✗
 */
object MessageCenterCacheStore {

    private const val FILE_NAME = "message_center_cache.json"

    /** 最多缓存多少条（够首屏展示就行 ✓ 太大拖慢启动 ✗） */
    private const val MAX_ITEMS = 200

    private val gson = Gson()

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    /**
     * 读缓存
     *
     * ⚠️ 任何异常都返回空列表 ✓ 不能因为缓存坏了让消息中心打不开 ✗
     */
    fun load(): List<MessageCenterAdapter.Item> {
        return runCatching {
            val f = file
            if (!f.exists() || f.length() == 0L) return emptyList()
            val type = object : TypeToken<List<MessageCenterAdapter.Item>>() {}.type
            val list: List<MessageCenterAdapter.Item>? = gson.fromJson(f.readText(), type)
            list.orEmpty()
                .filter { it.category.isNotEmpty() }
                .sortedByDescending { it.data.dateline }
                .take(MAX_ITEMS)
        }.getOrDefault(emptyList())
    }

    /**
     * 写缓存
     *
     * ⚠️ 空列表**不清空**旧缓存 ✓ —— 拉取失败时列表可能是空的 ✗
     *    如果这时写空 ⇒ 下次打开就连缓存都没了 ✗
     */
    fun save(items: List<MessageCenterAdapter.Item>) {
        if (items.isEmpty()) return
        runCatching {
            val top = items.sortedByDescending { it.data.dateline }.take(MAX_ITEMS)
            val f = file
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(gson.toJson(top))
            // 原子替换 ✓ 写一半崩了不会留下坏文件 ✓
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
