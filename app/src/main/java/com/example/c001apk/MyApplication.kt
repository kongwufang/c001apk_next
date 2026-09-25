package com.example.c001apk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.example.c001apk.ui.others.BugHandlerActivity
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.RiskControlPrompter
import com.example.c001apk.util.SslErrorPrompter
import com.example.c001apk.util.SslVerify
import dagger.hilt.android.HiltAndroidApp
import net.mikaelzero.mojito.Mojito
import net.mikaelzero.mojito.loader.glide.GlideImageLoader
import net.mikaelzero.mojito.view.sketch.SketchImageLoadFactory
import kotlin.system.exitProcess

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        context = applicationContext

        // SSL 校验失败 → 风险环境警告弹窗（跟踪前台 Activity）
        SslErrorPrompter.install(this)

        // 风控命中（-415 账号过多 / err_request_captcha_v2）→ 「设备标识未配置」引导弹窗
        RiskControlPrompter.install(this)

        // 网络传输调试模式（设置 - 高级）：放开进程级 HttpsURLConnection 默认校验，
        // 不放开的话抓包时走系统默认栈的图片会全部加载失败
        SslVerify.applyDebugGlobally()

        AppCompatDelegate.setDefaultNightMode(PrefManager.darkTheme)

        // 图片加载同样走 OkHttp（Mojito 的 Glide 会替换 GlideUrl 加载器），
        // 调试模式下换成不校验证书的客户端；非调试模式传 null = 行为不变
        Mojito.initialize(
            GlideImageLoader.with(this, SslVerify.debugImageClientOrNull()),
            SketchImageLoadFactory()
        )

        // 数盟 SDK 探针（验证「纯靠 SDK 从官方签发 DUID」是否闭环）。
        // 只在 -PshuzilmProbe=true 时才会被打进包；这里用反射调用，
        // 关掉开关时 src/probe/ 不参与编译，主工程连编译期依赖都没有。
        if (BuildConfig.SHUZILM_PROBE) {
            runCatching {
                Class.forName("com.example.c001apk.probe.ShuzilmProbe")
                    .getMethod("maybeRun", Context::class.java)
                    .invoke(null, this)
            }.onFailure { android.util.Log.w("MyApplication", "数盟探针未就绪：${it.message}") }
        }

        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            val exceptionMessage = android.util.Log.getStackTraceString(paramThrowable)

            val intent = Intent(this, BugHandlerActivity::class.java)
            intent.putExtra("exception_message", exceptionMessage)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

}