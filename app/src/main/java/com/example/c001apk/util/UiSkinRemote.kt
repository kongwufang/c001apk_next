package com.example.c001apk.util

import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * 界面规格表的热更新链路：与卡片规则表同一套两跳，实现见 [RemoteTwoHop]。
 *
 *   1. `GET [INDEX_URL]` → 清单（版本 / sha256 / 本体地址 / 域名白名单）
 *   2. 版本涨了才下载本体 → 校验 → 存 [PrefManager.uiSkinConfig] → [UiSkin.load]
 *
 * 失败一律「这次不更新」：继续用上次缓存，没缓存就用布局里的原值。
 */
object UiSkinRemote {

    private const val TAG = "UiSkinRemote"

    /** 清单接口（跟随升级接口那套会话头，复用 [UpdateChecker.fetchConfig] 的 client / UA） */
    const val INDEX_URL = "https://service.houlangs.cn/c001apk/uiskin/"

    /** 规格表大小上限（正常几 KB；防呆，别被写爆内存 / 磁盘） */
    private const val MAX_BYTES = 128 * 1024

    fun initFromCache() {
        UiSkin.load(PrefManager.uiSkinConfig)
    }

    fun refreshAsync() {
        Thread {
            runCatching { runBlocking { refresh() } }
                .onFailure { Log.w(TAG, "拉取界面规格表失败", it) }
        }.apply {
            name = "ui-skin"
            isDaemon = true
        }.start()
    }

    /** 拉清单 →（版本涨了才）下载 → 校验 → 落盘。返回这次是否真的更新了 */
    suspend fun refresh(): Boolean {
        // 顶层就是各页面对象，没有统一的「本体键」，内容形状只靠 sha256 兜
        val hit = RemoteTwoHop.fetch(
            indexUrl = INDEX_URL,
            currentVersion = PrefManager.uiSkinVersion,
            bodyKey = null,
            maxBytes = MAX_BYTES,
            tag = TAG
        ) ?: return false

        PrefManager.uiSkinConfig = hit.body
        PrefManager.uiSkinVersion = hit.version
        UiSkin.load(hit.body)
        Log.i(TAG, "界面规格表更新到 v${hit.version}，共 ${UiSkin.size()} 项")
        return true
    }
}
