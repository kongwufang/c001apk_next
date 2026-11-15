package com.example.c001apk.util

import android.content.Context
import com.example.c001apk.BuildConfig
import java.io.File

/**
 * 仅 DEBUG 构建生效的 UI 验收替身（2026-11-15 新增）。
 *
 * 为什么需要：侧栏升级卡只有在「线上正式版号 > 当前包号」时才出现，
 * 而验证“长更新日志限高滚动”必须能看到足够长的文本。现网 stable.json 的号
 * 与测试包相同，改服务端号会影响所有真实用户，所以不能动服务端。
 *
 * 用法（只在 debug 包上）：
 *   adb push <长文本文件> /sdcard/Android/data/com.example.c001apk/files/debug_changelog.txt
 * 存在该文件时，侧栏升级卡用它替代真实 changelog；删除文件即恢复真实值。
 *
 * Release 构建里 [BuildConfig.DEBUG] 为 false，本类永远返回 null，不会影响正式版。
 */
object DebugUi {

    private const val CHANGELOG_FILE = "debug_changelog.txt"

    /** 读取调试用更新日志；没有文件 / 非 DEBUG 构建时返回 null。 */
    fun changelogOverride(context: Context): String? {
        if (!BuildConfig.DEBUG) return null
        val file: File = context.getExternalFilesDir(null)?.resolve(CHANGELOG_FILE) ?: return null
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
