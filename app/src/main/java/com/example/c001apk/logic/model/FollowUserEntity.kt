package com.example.c001apk.logic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本机「关注」关系（2026-10-25 ✓）
 *
 * ## 为什么需要
 * 设置里可以让「关注」存本地还是云端 ✓
 * 但原来 app **只有关注接口、没有任何本地表** ✗
 * ⇒ 没登录 / 本地模式下根本无法记住关注关系 ✗
 *
 * ## 字段
 * 只要 uid 就能表达「关注了谁」✓
 * username / avatar 是**锦上添花** ✓ —— 关注时如果手上正好有就存一份 ✓
 * 没有就留空 ✓（列表页拿空头像占位 ✓ 点进去照样能看用户 ✓）
 */
@Entity
data class FollowUserEntity(
    @PrimaryKey(autoGenerate = false)
    var uid: String,
    var username: String = "",
    var avatar: String = "",
    var dateline: Long = System.currentTimeMillis()
)
