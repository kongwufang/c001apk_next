package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.PlaybackException
import com.example.c001apk.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.io.InterruptedIOException
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Response

/** Foreground-only connectivity checks. This observer never replays a user's request. */
internal object NetworkHealth {
    private const val BACKGROUND_GRACE = 700L
    private const val FAILURE_SPACING = 3_000L
    private val main by lazy { Handler(Looper.getMainLooper()) }
    // At most one real worker, even when the OS resolver gets stuck past the UI deadline.
    private val worker = ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, SynchronousQueue())
    private val signalPosted = AtomicBoolean()
    @Volatile private var foreground = false
    private var started = 0
    private var generation = 0
    private var running = false
    private var nextCheck = 0L
    private var retryRequested = false
    private var failed = false
    private var warned = false
    private var current = WeakReference<Activity>(null)
    private var dialog: AlertDialog? = null

    private val background = Runnable {
        foreground = false
        generation++
        running = false
        retryRequested = false
        failed = false
        warned = false
        main.removeCallbacks(recheck)
        dismiss()
    }
    private val recheck = Runnable { checkNow() }

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                main.removeCallbacks(background)
                if (!foreground) {
                    foreground = true
                    nextCheck = 0L
                    checkNow()
                }
            }
            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) main.postDelayed(background, BACKGROUND_GRACE)
            }
            override fun onActivityResumed(activity: Activity) {
                current = WeakReference(activity)
                showIfNeeded()
            }
            override fun onActivityPaused(activity: Activity) {
                if (current.get() !== activity) return
                current.clear()
                dismiss()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun onMediaRequestFailed(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) onRequestFailed()
    }

    fun onRequestFailed() {
        if (!foreground || !signalPosted.compareAndSet(false, true)) return
        main.post {
            signalPosted.set(false)
            if (foreground) {
                if (running) retryRequested = true else queueCheck()
            }
        }
    }

    private fun queueCheck() {
        main.removeCallbacks(recheck)
        val delay = nextCheck - SystemClock.elapsedRealtime()
        if (delay <= 0) checkNow() else main.postDelayed(recheck, delay)
    }

    private fun checkNow() {
        if (!foreground || running) return
        running = true
        val ticket = ++generation
        val timeout = Runnable { finish(ticket, false) }
        main.postDelayed(timeout, MiuiConnectivityProbe.TIMEOUT_MS.toLong())
        try {
            worker.execute {
                val connected = MiuiConnectivityProbe.check()
                main.post {
                    main.removeCallbacks(timeout)
                    finish(ticket, connected)
                }
            }
        } catch (_: RejectedExecutionException) {
            // The previous OS DNS call still occupies the worker. The deadline above handles it.
        }
    }

    private fun finish(ticket: Int, connected: Boolean) {
        if (!foreground || !running || ticket != generation) return
        running = false
        nextCheck = SystemClock.elapsedRealtime() + FAILURE_SPACING
        failed = !connected
        if (connected) {
            warned = false
            dismiss()
        } else showIfNeeded()
        if (retryRequested) {
            retryRequested = false
            queueCheck()
        }
    }

    private fun showIfNeeded() {
        val activity = current.get() ?: return
        if (!failed || warned || dialog != null || activity.isFinishing || activity.isDestroyed) return
        val prompt = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.network_health_title)
            .setMessage(R.string.network_health_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.network_health_settings) { _, _ ->
                val opened = runCatching { activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.isSuccess
                if (!opened) runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            .create()
        prompt.setOnDismissListener { if (dialog === prompt) dialog = null }
        prompt.show()
        dialog = prompt
        warned = true
    }

    private fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /** Final transport or HTTP failure, not an intermediate retry and never user cancellation. */
    fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder().eventListenerFactory {
        FailureListener(::onRequestFailed)
    }

    internal class FailureListener(private val report: () -> Unit) : EventListener() {
        private var status = 0
        override fun responseHeadersEnd(call: Call, response: Response) { status = response.code }
        override fun callEnd(call: Call) {
            if (status >= 400 && !call.isCanceled()) report()
        }
        override fun callFailed(call: Call, ioe: IOException) {
            // OkHttp also marks calls canceled when its overall timeout fires.
            if (!call.isCanceled() || ioe is SocketTimeoutException ||
                (ioe is InterruptedIOException && ioe.message == "timeout")) report()
        }
    }
}
