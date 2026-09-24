package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.c001apk.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference
import javax.net.ssl.SSLException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * SSL 校验失败 → 「网络环境存在安全风险」警告弹窗。
 *
 * 触发来源：
 * 1. OkHttp：[SslErrorInterceptor] 捕获 [SSLException]（证书链/域名/有效期校验不过都算）；
 * 2. WebView：onReceivedSslError 回调里直接调用 [onSslFailure]。
 *
 * 弹窗按钮：
 * - 「确定」：仅关闭弹窗；
 * - 「关闭 SSL 证书校验（不推荐，…，修改重启应用后生效）」：把开关置为关，
 *   **不自动重启**，用户自己重启后生效。
 *
 * 同一时间只弹一个（网络重试/并发请求失败不会刷屏）。
 */
object SslErrorPrompter {

    @Volatile
    private var showing = false

    /** 无前台 Activity 时挂起，等下一个 Activity resume 再弹 */
    @Volatile
    private var pending = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivity = WeakReference<Activity?>(null)

    /** 在 Application.onCreate 里注册，跟踪前台 Activity */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
                if (pending) {
                    pending = false
                    show()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity.get() === activity) currentActivity = WeakReference(null)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * 网络层 SSL 校验失败时调用（任意线程）。
     * @param error 仅用于日志/调试，可为 null（WebView 路径传 SslError.toString()）
     */
    fun onSslFailure(error: Exception?) {
        // 「网络传输调试模式」下本来就不校验证书，弹风险提示只会干扰抓包
        if (PrefManager.isSslDebug) return
        if (showing || pending) return
        showing = true
        val activity = currentActivity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            // 没有可用界面，挂起等下一个 Activity
            showing = false
            pending = true
            return
        }
        mainHandler.post {
            if (showing) show()
            else showing = false
        }
    }

    private fun show() {
        val activity = currentActivity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            showing = false
            return
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ssl_error_title)
            .setMessage(R.string.ssl_error_message)
            .setPositiveButton(R.string.ssl_error_confirm) { d, _ -> d.dismiss() }
            .setCancelable(false)
            .apply {
                // 只有当前校验开关是开的，才提供「关闭校验」的出口
                if (PrefManager.isVerifySsl) {
                    setNegativeButton(R.string.ssl_error_disable) { d, _ ->
                        PrefManager.isVerifySsl = false
                        Toast.makeText(
                            activity,
                            R.string.ssl_error_disabled_toast,
                            Toast.LENGTH_LONG
                        ).show()
                        d.dismiss()
                    }
                }
            }
            .create()

        dialog.setOnDismissListener { showing = false }
        dialog.show()
    }
}

/**
 * 应用级拦截器：所有 API 请求 SSL 握手失败（证书校验不过）时触发风险弹窗。
 * 异常原样抛出，不改变请求行为。
 */
object SslErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: SSLException) {
            SslErrorPrompter.onSslFailure(e)
            throw e
        }
    }
}
