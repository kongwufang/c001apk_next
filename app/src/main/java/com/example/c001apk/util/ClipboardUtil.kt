package com.example.c001apk.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtil {
    fun copyText(context: Context, text: String, showToast: Boolean = true) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ClipData.newPlainText("c001apk text", text)?.let { clipboardManager.setPrimaryClip(it) }
        // 登记一下是自己写的：分享面板「复制链接」→ 切出去再回来，别转头又问自己一遍
        ShareLinkPrompter.markSelfCopied(text)
        if (showToast)
            context.makeToast("已复制: $text")
    }
}