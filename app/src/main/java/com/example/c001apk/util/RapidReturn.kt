package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import java.lang.ref.WeakReference

/** Recognizes an actual second close of the immediately resumed page in the same task. */
internal class ReturnBurst<T : Any>(private val clock: () -> Long) {
    private data class Page<T : Any>(val ref: WeakReference<T>, val taskId: Int) {
        fun isPage(page: T, task: Int) = ref.get() === page && taskId == task
    }

    private var foreground: Page<T>? = null
    private var closing: Page<T>? = null
    private var next: Page<T>? = null
    private var closedAt = 0L

    fun onResume(page: T, taskId: Int) {
        val resumed = Page(WeakReference(page), taskId)
        if (closing?.taskId == taskId && closing?.ref?.get() !== page && next == null && withinWindow()) {
            next = resumed
        } else if (next?.isPage(page, taskId) != true) {
            clearClose()
        }
        foreground = resumed
    }

    fun onPause(page: T, taskId: Int) {
        if (foreground?.isPage(page, taskId) != true) return
        if (closing?.isPage(page, taskId) != true) clearClose()
        foreground = null
    }

    fun shouldSkipClose(page: T, taskId: Int): Boolean =
        foreground?.isPage(page, taskId) == true && next?.isPage(page, taskId) == true && withinWindow()

    fun onCreate() = clearClose()

    fun onClose(page: T, taskId: Int) {
        if (foreground?.isPage(page, taskId) != true || closing?.isPage(page, taskId) == true) return
        closing = foreground
        next = null
        closedAt = clock()
    }

    private fun withinWindow() = clock() - closedAt in 0L..350L

    private fun clearClose() {
        closing = null
        next = null
    }
}

/** Main-thread only. No Back callbacks: the system still owns navigation and predictive gestures. */
internal object RapidReturn : Application.ActivityLifecycleCallbacks {
    private val burst = ReturnBurst<Activity>(SystemClock::uptimeMillis)

    fun shouldSkipClose(activity: Activity): Boolean =
        !activity.isTaskRoot && burst.shouldSkipClose(activity, activity.taskId)

    fun onClose(activity: Activity, taskId: Int) = burst.onClose(activity, taskId)

    override fun onActivityResumed(activity: Activity) = burst.onResume(activity, activity.taskId)
    override fun onActivityPaused(activity: Activity) = burst.onPause(activity, activity.taskId)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = burst.onCreate()
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
