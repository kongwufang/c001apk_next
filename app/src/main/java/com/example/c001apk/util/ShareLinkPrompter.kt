package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.c001apk.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.lang.ref.WeakReference

/**
 * 每次进入应用读一次剪贴板：识别到酷安分享链接就弹「识别到分享链接 / 是否打开？」。
 *
 * 触发口径是**进入应用**而不是冷启动：把已 start 的 Activity 数出来，0 → 1 才算一次进入。
 * Activity 之间互跳、从二级页返回都到不了 0，所以不会重复打扰；用 onActivityResumed 是错的 ——
 * 那样每切一个页面都会被当成一次进入。
 *
 * 同一个进程里同一份剪贴板内容只问一次（用户点了「取消」也不再来烦），换了一份新内容才会再问。
 * 应用自己「复制链接」写进去的内容会先登记（[markSelfCopied]），切回来不会转头问自己。
 *
 * Android 10 起后台读剪贴板会被系统直接拦成空，所以真正去读要等窗口拿到焦点，
 * 拿不到就短暂重试几次（[MAX_RETRY] ≈ 2s，够窗口从 onStart 走到获得焦点）。
 */
object ShareLinkPrompter {

    private const val RETRY_DELAY_MS = 250L
    private const val MAX_RETRY = 8

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前 resumed 的 Activity：只有它在前台才弹，避免用户已经离开还往上盖窗口 */
    private var resumedActivity = WeakReference<Activity?>(null)

    /** 已启动的 Activity 数：0 → 1 才算「进入应用」 */
    private var startedCount = 0

    /** 弹窗正开着就不再弹第二个 */
    private var showing = false

    /** 问过的剪贴板内容（含用户点「取消」的那次）：同一份内容不再问 */
    private var askedText: String? = null

    /** 本应用自己刚复制进去的文本 */
    private var selfCopiedText: String? = null

    /** 在 Application.onCreate 里注册 */
    fun install(app: Application) {
        if (!isMainProcess(app)) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val entering = startedCount == 0
                startedCount++
                // 冷启动第一个页面起来、或从后台回到前台，都算一次「进入」
                if (entering) ask(activity, 0)
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedCount > 0) startedCount--
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivity = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumedActivity.get() === activity) resumedActivity = WeakReference(null)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** 应用自己写剪贴板时登记一次（见 [ClipboardUtil.copyText]） */
    fun markSelfCopied(text: String) {
        selfCopiedText = text
    }

    private fun ask(activity: Activity, attempt: Int) {
        if (showing || attempt > MAX_RETRY || !isAlive(activity)) return
        // 等到它真的在前台拿到焦点：冷启动时 onStart 早于 onResume，而且 Android 10+ 里
        // 没有焦点的窗口读剪贴板只会得到 null（系统按「后台访问」拦掉）
        val ready = resumedActivity.get() === activity && activity.hasWindowFocus()
        if (!ready) {
            mainHandler.postDelayed({ ask(activity, attempt + 1) }, RETRY_DELAY_MS)
            return
        }
        val text = readClipboard(activity) ?: return
        if (text == askedText || text == selfCopiedText) return
        val link = NetWorkUtil.findCoolapkLink(text) ?: return
        askedText = text
        show(activity, link)
    }

    private fun show(activity: Activity, link: String) {
        showing = true
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.share_link_title)
            .setMessage(R.string.share_link_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.share_link_confirm) { d, _ ->
                d.dismiss()
                NetWorkUtil.openLink(activity, link, null)
            }
            .create()
        dialog.setOnDismissListener { showing = false }
        dialog.show()
    }

    private fun readClipboard(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isAlive(activity: Activity) = !activity.isFinishing && !activity.isDestroyed

    /**
     * WebViewActivity 跑在 :webview 独立进程，那个进程也会新建一份 Application 并走一遍 onCreate，
     * 不排除的话「打开网页 → 回主进程」会被当成两次进入应用。
     */
    private fun isMainProcess(app: Application): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // API 28 以下没有 Application.getProcessName()，从 /proc 里读
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrNull()
        }
        return name.isNullOrEmpty() || name == app.packageName
    }
}
