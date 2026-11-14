package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Checks on cold start/background return, never on ordinary in-app page changes. */
internal object SzlmIdAutoRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    private var installed = false

    fun install(app: Application) {
        if (installed || !isMainProcess(app)) return
        installed = true
        // Older versions saved an ID without its acquisition time. Keep it for a full first window.
        if (PrefManager.SZLMID.isNotEmpty() && PrefManager.szlmIdLastObtainedAt <= 0) {
            PrefManager.szlmIdLastObtainedAt = System.currentTimeMillis()
        }
        val handler = Handler(Looper.getMainLooper())
        var started = 0
        var foreground = false
        val markBackground = Runnable { foreground = false }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                handler.removeCallbacks(markBackground)
                if (!foreground) {
                    foreground = true
                    if (activity !is com.example.c001apk.ui.others.SzlmIdActivity &&
                        activity !is com.example.c001apk.ui.onboarding.OnboardingActivity) check(app)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                // Debounce rotation/activity recreation, matching process lifecycle semantics.
                if (started == 0) handler.postDelayed(markBackground, 700)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun cancelPending() {
        pending?.cancel()
    }

    private fun check(app: Application) {
        if (pending?.isActive == true) return
        val oldId = PrefManager.SZLMID
        val lastSuccess = PrefManager.szlmIdLastObtainedAt
        if (!DuidRefreshPolicy.shouldRefresh(
                PrefManager.autoRefreshSzlmId, PrefManager.onboarded, oldId.isNotEmpty(),
                lastSuccess, System.currentTimeMillis()
            )) return
        pending = scope.launch {
            try {
                val id = LocalDuidClient.fetch(app)
                // Explicit manual changes and switching off always win over an in-flight refresh.
                if (PrefManager.autoRefreshSzlmId && PrefManager.onboarded &&
                    PrefManager.SZLMID == oldId && PrefManager.szlmIdLastObtainedAt == lastSuccess) {
                    SzlmIdPrompt.save(id)
                    AppLog.i(LogModule.NET, "SzlmIdAutoRefresh", "自动刷新成功，已记录获取时间")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(LogModule.NET, "SzlmIdAutoRefresh", "自动刷新失败，保留原 ID 和获取时间：${e.javaClass.simpleName}")
            }
        }
    }

    private fun isMainProcess(app: Application): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrNull()
        return name == app.packageName
    }
}
