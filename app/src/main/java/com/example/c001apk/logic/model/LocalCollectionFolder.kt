package com.example.c001apk.logic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本机收藏夹（2026-10-25 ✓）
 *
 * > 用户要求：「我开启了收藏夹本地保存之后，去保存显示的还是云端的收藏夹，
 * >   所以需要一个本地收藏夹栏目，然后可以添加收藏夹，收藏夹是保存在本地的」
 *
 * ## id 规则
 * 本地夹 id 用 `local_<时间戳>` ✓
 * 云端夹是服务端给的数字 id ✓ 两者不会撞 ✓
 *
 * 默认夹：id = [DEFAULT_ID] ✓ 空标题时由调用方显示成「默认收藏夹」✓
 */
@Entity
data class LocalCollectionFolder(
    @PrimaryKey(autoGenerate = false)
    var id: String,
    var title: String,
    var dateline: Long = System.currentTimeMillis()
) {
    companion object {
        /** 默认收藏夹的 id（`LocalCollectionEntity.folderId` 为空时也算默认夹 ✓） */
        const val DEFAULT_ID = "local_default"
    }
}
