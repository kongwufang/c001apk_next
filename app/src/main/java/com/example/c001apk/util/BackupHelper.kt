package com.example.c001apk.util

import com.example.c001apk.di.UserBlackList
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.c001apk.logic.dao.FollowUserDao
import com.example.c001apk.logic.dao.LocalCollectionDao
import com.example.c001apk.logic.dao.LocalCollectionFolderDao
import com.example.c001apk.logic.dao.StringEntityDao
import com.example.c001apk.logic.model.FollowUserEntity
import com.example.c001apk.logic.model.LocalCollectionEntity
import com.example.c001apk.logic.model.LocalCollectionFolder
import com.example.c001apk.logic.model.StringEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机数据备份：导出 / 导入（2026-10-25 ✓）
 *
 * > 用户要求：「增加导出功能，可以把本地偏好导出为文件，
 * >   当然，本地缓存也可以导出文件，也支持导入」
 *
 * ## 导出什么
 * | 内容 | 来源 |
 * |---|---|
 * | **偏好设置** | `PrefManager` 那整个 SharedPreferences（带类型标记 ✓）|
 * | **本机关注** | `follow_user.db` → `FollowUserEntity` |
 * | **本机收藏** | `local_collection.db` → `LocalCollectionEntity` + `LocalCollectionFolder` |
 * | **本机黑名单（用户）** | `user_blacklist.db` → `StringEntity` |
 *
 * ## 为什么偏好要带类型标记
 * SharedPreferences 的值有 Boolean / Int / Long / Float / String / Set<String> 六种 ✗
 * JSON 只有 number / boolean / string ✗ ⇒ 直接 `putAll` 会把 Int 变成 Double ✗
 * ⇒ 每个值存成 `{"t":"int","v":123}` ✓ 导入时按 t 还原 ✓
 *
 * ## 为什么用 SAF（`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`）
 * 不需要任何存储权限 ✓ 用户自己选位置 ✓ 现代 Android 推荐做法 ✓
 */
@Singleton
class BackupHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val followUserDao: FollowUserDao,
    private val localCollectionDao: LocalCollectionDao,
    private val localCollectionFolderDao: LocalCollectionFolderDao,
    @UserBlackList private val userBlackListDao: StringEntityDao,
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** 一份备份的全部内容 */
    data class Backup(
        val app: String = "c001apk_next",
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val prefs: Map<String, Tagged>? = null,
        val follow: List<FollowUserEntity>? = null,
        val collection: List<LocalCollectionEntity>? = null,
        val collectionFolders: List<LocalCollectionFolder>? = null,
        val userBlackList: List<StringEntity>? = null,
    )

    /** 带类型标记的一个偏好值 */
    data class Tagged(val t: String, val v: Any?)

    // ── 导出 ──

    suspend fun export(uri: Uri): Boolean = runCatching {
        val backup = Backup(
            prefs = PrefManager.dumpAll().mapValues { (_, v) ->
                when (v) {
                    is Boolean -> Tagged("bool", v)
                    is Int -> Tagged("int", v)
                    is Long -> Tagged("long", v)
                    is Float -> Tagged("float", v)
                    is String -> Tagged("str", v)
                    is Set<*> -> Tagged("strset", v.map { it.toString() })
                    else -> Tagged("str", v.toString())
                }
            },
            follow = followUserDao.loadAll(),
            collection = localCollectionDao.loadAll(),
            collectionFolders = localCollectionFolderDao.loadAll(),
            userBlackList = userBlackListDao.loadAllList(),
        )
        val json = gson.toJson(backup)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
            ?: return false
        Log.i(TAG, "导出成功：${json.length} 字节")
        true
    }.getOrElse {
        Log.e(TAG, "导出失败", it)
        false
    }

    // ── 导入 ──

    suspend fun import(uri: Uri): Boolean = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return false
        val backup: Backup = gson.fromJson(json, Backup::class.java) ?: return false
        if (backup.app != "c001apk_next") {
            Log.w(TAG, "不是本应用的备份文件")
            return false
        }

        // 偏好：按类型还原 ✓
        backup.prefs?.let { prefs ->
            val restored = prefs.mapNotNull { (k, tv) ->
                val value: Any? = when (tv.t) {
                    "bool" -> asBool(tv.v)
                    "int" -> asNum(tv.v)?.toInt()
                    "long" -> asNum(tv.v)?.toLong()
                    "float" -> asNum(tv.v)?.toFloat()
                    "strset" -> (tv.v as? List<*>)?.map { it.toString() }?.toSet()
                    else -> tv.v?.toString()
                }
                if (value == null) null else k to value
            }.toMap()
            PrefManager.restoreAll(restored)
        }

        backup.follow?.let { followUserDao.insertList(it) }
        backup.collectionFolders?.let { localCollectionFolderDao.insertList(it) }
        backup.collection?.let { localCollectionDao.insertList(it) }
        backup.userBlackList?.let { userBlackListDao.insertList(it) }
        Log.i(TAG, "导入成功")
        true
    }.getOrElse {
        Log.e(TAG, "导入失败", it)
        false
    }

    /** Gson 把数字都读成 Double ✗ 这里收敛回 Number ✓ */
    private fun asNum(v: Any?): Number? = when (v) {
        is Number -> v
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private fun asBool(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> null
    }

    companion object {
        private const val TAG = "BackupHelper"
    }
}
