package com.example.c001apk.util

import android.util.Log

/**
 * 「数据保存位置」三开关的**登录态自动对齐**（2026-10-25 ✓）
 *
 * ## 规则（用户原文）
 * > 「默认情况下没有账号登录时使用本地保存，有账号登录时使用云端保存，
 * >   除非用户手动修改开关，但在完成有账号到没账号和没账号到有账号的时候，
 * >   自动调整一次开关」
 *
 * ## 实现
 * 用 [PrefManager.syncLocationLoginSnapshot] 记住「上次对齐时的登录态」✓
 *
 * - 登录态**没变** ⇒ 什么都不做 ✓
 *   ⇒ 用户手动改过的开关**保持不动** ✓（这才符合「除非用户手动修改」✓）
 * - 登录态**变了** ⇒ 把三个开关**都**对齐成当前登录态 ✓ 再更新快照 ✓
 *   ⇒ 这就是「自动调整一次」✓
 *
 * ## 调用点
 * [com.example.c001apk.MyApplication] 的 `onCreate`（每次进程启动一次 ✓）
 * ⇒ 登录 / 退出登录都会重启进程或回到 MainActivity ⇒ 下次进来就能对齐 ✓
 *   （如果以后要做到「当场生效」，在登录成功 / 退出登录的回调里再调一次即可 ✓）
 */
object SyncLocationBootstrap {

    private const val TAG = "SyncLocationBootstrap"

    fun applyIfLoginChanged() {
        val now = if (PrefManager.isLogin) 1 else 0
        val last = PrefManager.syncLocationLoginSnapshot
        if (last == now) {
            Log.i(TAG, "登录态未变化（${if (now == 1) "已登录" else "未登录"}），保持用户设置 ✓")
            return
        }
        val cloud = now == 1
        PrefManager.saveCollectionToCloud = cloud
        PrefManager.saveFollowToCloud = cloud
        PrefManager.saveBlackListToCloud = cloud
        PrefManager.syncLocationLoginSnapshot = now
        Log.i(
            TAG,
            "登录态变化（${if (last == -1) "首次" else if (last == 1) "已登录" else "未登录"}" +
                " → ${if (cloud) "已登录" else "未登录"}）" +
                "，三个开关对齐成${if (cloud) "云端" else "本地"} ✓"
        )
    }
}
