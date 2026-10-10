package com.example.c001apk.util

import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * 卡片渲染规则表的热更新链路：**地址由服务端给**（以后挪到 OSS/CDN 只改服务端，App 不用发版）。
 *
 * 两跳与校验的实现见 [RemoteTwoHop]，这里只管「拉 → 落盘 → 装载」：
 *   1. `GET .../c001apk/cardui/` → 清单
 *      `{"code":0,"version":3,"sha256":"...","url":"https://.../cardui.json","hosts":["oss.houlangs.cn"]}`
 *   2. 下载 `url` → 校验 sha256 → 存 [PrefManager.cardUiConfig] + 版本号 → [CardUi.load]
 *
 * 任何一步失败都只是「这次没更新」：继续用上次缓存的表，没缓存就用 App 内置默认表。
 * 版本号没涨就不重复下载。
 */
object CardUiRemote {

    private const val TAG = "CardUiRemote"

    /** 清单接口（跟随升级接口那套会话头，复用 UpdateChecker 的 client / UA） */
    const val INDEX_URL = "https://service.houlangs.cn/c001apk/cardui/"

    /** 规则表大小上限（正常几 KB；防呆，别被写爆内存/磁盘） */
    private const val MAX_BYTES = 512 * 1024

    fun initFromCache() {
        CardUi.load(PrefManager.cardUiConfig)
    }

    fun refreshAsync() {
        Thread {
            runCatching { runBlocking { refresh() } }
                .onFailure { Log.w(TAG, "拉取卡片规则表失败", it) }
        }.apply {
            name = "card-ui"
            isDaemon = true
        }.start()
    }

    /** 拉清单 →（版本涨了才）下载 → 校验 → 落盘。返回这次是否真的更新了 */
    suspend fun refresh(): Boolean {
        // 规则表的形状是 cards 对象，解析不出 cards 就当没更新
        val hit = RemoteTwoHop.fetch(
            indexUrl = INDEX_URL,
            currentVersion = PrefManager.cardUiVersion,
            bodyKey = "cards",
            maxBytes = MAX_BYTES,
            tag = TAG
        ) ?: return false

        PrefManager.cardUiConfig = hit.body
        PrefManager.cardUiVersion = hit.version
        CardUi.load(hit.body)
        Log.i(TAG, "卡片规则表更新到 v${hit.version}，共 ${CardUi.remoteSize()} 条模板规则")
        return true
    }
}
