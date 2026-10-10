package com.example.c001apk.util

import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * 启动时从自建服务拉取的轻量配置：用户认证表 + 可信链接白名单。
 *
 * 两个接口都在 service.houlangs.cn 下，跟随升级接口那套会话头，所以复用
 * [UpdateChecker.fetchConfig] 的 client / UA / 请求头，不再各造一份。
 *
 * 生命周期：
 *   1. 进程启动 [initFromCache]：把上次落盘的配置先装进内存（离线也不掉认证 / 白名单）；
 *   2. 随后 [refreshAsync] 后台刷新一次，拉到就覆盖缓存，拉不到什么都不做。
 *
 * 另外两张表（卡片规则表 cardui / 界面规格表 uiskin）是「清单 + 本体」两跳、各自带版本号，
 * 所以不塞进 [refresh] 里，由各自的 Remote 对象单独起线程，公共实现在 [RemoteTwoHop]。
 */
object RemoteConfig {

    private const val TAG = "RemoteConfig"

    /** 用户认证表（一个 uid 一份 json，服务端读整个目录合并下发） */
    const val VERIFY_URL = "https://service.houlangs.cn/c001apk/userverify/"

    /** 可信链接白名单 */
    const val LINK_URL = "https://service.houlangs.cn/c001apk/reliablelink/"

    /** 离线兜底：先用上次缓存，首启时没有缓存就走各自的内置默认表 */
    fun initFromCache() {
        VerifyBadge.load(PrefManager.userVerifyConfig)
        LinkGuard.load(PrefManager.reliableLinkConfig)
        CardUiRemote.initFromCache()
        UiSkinRemote.initFromCache()
    }

    /** 后台刷新，不阻塞启动；失败只是继续用旧配置，不提示用户 */
    fun refreshAsync() {
        Thread {
            runCatching { runBlocking { refresh() } }
                .onFailure { Log.w(TAG, "拉取云端配置失败", it) }
        }.apply {
            name = "remote-config"
            isDaemon = true
        }.start()
        // 这两张表是两跳（先取清单、再按服务端给的地址下载），各起一条线程，别拖住上面那俩
        CardUiRemote.refreshAsync()
        UiSkinRemote.refreshAsync()
    }

    /** 拉一遍两个接口；任一失败都保留原来的表 */
    suspend fun refresh() {
        UpdateChecker.fetchConfig(VERIFY_URL)?.let {
            PrefManager.userVerifyConfig = it
            VerifyBadge.load(it)
        }
        UpdateChecker.fetchConfig(LINK_URL)?.let {
            PrefManager.reliableLinkConfig = it
            LinkGuard.load(it)
        }
    }
}
