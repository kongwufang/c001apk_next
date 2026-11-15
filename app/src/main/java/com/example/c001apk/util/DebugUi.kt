package com.example.c001apk.util

import com.example.c001apk.BuildConfig
import com.example.c001apk.MyApplication
import java.io.File

/**
 * 仅 DEBUG 构建生效的 UI 验收替身（2026-11-15 新增）。
 *
 * 为什么需要：侧栏升级卡只有在「服务端正式版号 > 当前包号」时才出现，
 * 而验证“长更新日志限高滚动”必须真的看到一张带长日志的卡。现网 `stable.json`
 * 的号与测试包同为 10010，改服务端号会推送给所有真实用户，所以不能动服务端。
 *
 * 用法（只在 debug 包上）：
 *
 * ```powershell
 * adb push <长文本文件> /sdcard/Android/data/com.example.c001apk/files/debug_changelog.txt
 * ```
 *
 * 存在该文件时，[updateOverride] 返回一张虚拟升级卡（版本号 = 当前包 +1），
 * 侧栏就会显示它；删除文件即恢复真实更新检查结果。
 *
 * Release 构建里 [BuildConfig.DEBUG] 为 false，本类恒返回 null，不影响正式版。
 */
object DebugUi {

    private const val CHANGELOG_FILE = "debug_changelog.txt"

    /** 调试用虚拟升级信息；没有文件 / 非 DEBUG 构建时返回 null。 */
    fun updateOverride(): UpdateChecker.UpdateInfo? {
        if (!BuildConfig.DEBUG) return null
        val text = readChangelog() ?: return null
        return UpdateChecker.UpdateInfo(
            versionName = "V9.9.9-调试卡",
            // 必须比当前包新，否则侧栏不显示（这就是“线上号没涨”时看不到卡的原因）
            versionCode = BuildConfig.VERSION_CODE + 1L,
            changelog = text,
            lines = listOf(UpdateChecker.DownloadLine("调试线路", "https://example.invalid/")),
        )
    }

    private fun readChangelog(): String? {
        val file: File = MyApplication.context.getExternalFilesDir(null)
            ?.resolve(CHANGELOG_FILE) ?: return null
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
