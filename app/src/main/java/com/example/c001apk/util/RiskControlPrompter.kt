package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.c001apk.R
import com.example.c001apk.ui.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 「设备标识说明」弹窗：首启一次性说明 + 运行中命中风控时的补充说明。
 *
 * 性质是**告知，不是闸门**：不阻塞任何界面，用户关掉就继续用。
 *
 * 为什么不做「填了才能进」的准入校验：
 *  1. 客户端**没有**能判定「通过」的状态。服务端对陌生设备只会要求人机验证，
 *     而 `_rev/probe_trust.py` 已实测：过码之后同一设备串请求同一接口**仍被拒**，
 *     不存在「填对就放行」的判据，任何「验证通过才放行」的写法都只会永远拦住人；
 *  2. 能通过校验的只有**真机官方 App 里签发出来的**那份 ID，把它当入场券，
 *     等于要求每个用户先去别处弄一份设备标识 —— 那正是本项目不做的事；
 *  3. 代价与收益也不成比例：首页 / 搜索 / 个人页等本来就不依赖这个字段，
 *     加闸门只会让 App 对绝大多数人直接不可用。
 *
 * 触发时机：
 *  - 首次安装后第一个 Activity 起来时（[install] 注册的生命周期回调）；
 *  - 或运行中命中 [RiskControlInterceptor]（403 且响应体带 `-415` / `err_request_captcha_v2`）。
 *
 * 两者共用 [PrefManager.szlmIdNoticed]，一次安装最多打扰一次；
 * 当前已经有一份可用 ID（内置的或用户填的，见 [PrefManager.SZLMID]）则完全不出现。
 */
object RiskControlPrompter {

    @Volatile
    private var showing = false

    /** 无前台 Activity 时挂起，等下一个 Activity resume 再弹 */
    @Volatile
    private var pending = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivity = WeakReference<Activity?>(null)

    /** 首启延后一点再弹，避免盖在启动页上 */
    private const val FIRST_LAUNCH_DELAY_MS = 800L

    /** 在 Application.onCreate 里注册，跟踪前台 Activity */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
                if (pending) {
                    pending = false
                    show()
                    return
                }
                if (shouldExplain()) {
                    mainHandler.postDelayed({
                        if (!showing && shouldExplain()) show()
                    }, FIRST_LAUNCH_DELAY_MS)
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

    /** 当前**确实拿不到**任何 ID（既没内置、用户也没填）且本次安装还没说明过 → 值得说明一次 */
    private fun shouldExplain() = PrefManager.SZLMID.isEmpty() && !PrefManager.szlmIdNoticed

    /**
     * 网络层命中风控时调用（任意线程）。
     * @param body 响应体片段，仅用于判断/日志
     */
    fun onRiskControl(body: String?) {
        if (!shouldExplain()) return
        if (showing || pending) return
        showing = true
        val activity = currentActivity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            // 没有可用界面，挂起等下一个 Activity
            showing = false
            pending = true
            return
        }
        mainHandler.post { if (showing) show() else showing = false }
    }

    private fun show() {
        val activity = currentActivity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            showing = false
            return
        }
        // 真正显示出来才记账，避免用户根本没看到就被永久静音
        PrefManager.szlmIdNoticed = true

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.szlm_id_missing_title)
            .setMessage(R.string.szlm_id_missing_message)
            .setPositiveButton(R.string.szlm_id_missing_goto) { d, _ ->
                runCatching {
                    activity.startActivity(Intent(activity, SettingsActivity::class.java))
                }
                d.dismiss()
            }
            .setNegativeButton(R.string.szlm_id_missing_later) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnDismissListener { showing = false }
        dialog.show()
    }
}

/**
 * 应用级拦截器：所有 API 请求命中风控时触发一次「设备标识说明」弹窗。
 *
 * 判定条件：HTTP 403 且响应体包含 `-415`（账号过多）或 `err_request_captcha_v2`
 * （需要人机验证）。只 [Response.peekBody] 读取片段，不消费响应流，请求行为不变。
 *
 * 注意不拦 `err_request_captcha`（不带 v2）：那条走 [com.example.c001apk.ui.feed.reply.ReplyViewModel]
 * 已有的验证码流程，与本弹窗无关。
 */
object RiskControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 403) {
            val body = runCatching { response.peekBody(4096).string() }.getOrNull()
            if (body != null && (body.contains("-415") || body.contains("err_request_captcha_v2"))) {
                RiskControlPrompter.onRiskControl(body)
            }
        }
        return response
    }
}
