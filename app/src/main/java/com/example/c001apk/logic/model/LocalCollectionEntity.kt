package com.example.c001apk.logic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本机「收藏」记录（2026-10-25 ✓）
 *
 * ## 为什么需要
 * 设置里可以让「收藏」存本地还是云端 ✓
 * 云端模式走 `/v6/collection/addItem` ✓
 * **本地模式必须自己记住收藏了哪些内容** ✗ —— 否则点完只是 UI 亮一下 ✗ 下次就没了 ✗
 *
 * ## 只存 targetId
 * 本地收藏**不拉收藏夹体系** ✗ —— 就是「我收藏过这一条」✓
 * 需要展示时按 targetId 去拿详情 ✓（跟「浏览历史」同一套思路 ✓）
 */
@Entity
data class LocalCollectionEntity(
    @PrimaryKey(autoGenerate = false)
    var targetId: String,
    /**
     * 属于哪个本地收藏夹（[LocalCollectionFolder.id]）
     *
     * 同一条内容**可以同时在多个本地夹里** ✓ 但主键只有 targetId ✗
     * ⇒ 这里只记「最后一次放进的那个夹」✓
     *   （本地夹只是方便归类 ✓ 不追求跟云端一样的多夹语义 ✓）
     */
    var folderId: String = LocalCollectionFolder.DEFAULT_ID,
    var dateline: Long = System.currentTimeMillis()
)
