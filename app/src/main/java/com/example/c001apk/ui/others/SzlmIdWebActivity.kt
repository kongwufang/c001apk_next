package com.example.c001apk.ui.others

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivitySzlmIdWebBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.SzlmIdParams
import com.example.c001apk.util.SzlmIdPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 「数字联盟 ID」换取页：内置 WebView 打开自建页面 [SzlmIdParams.WEB_ENTRY]。
 *
 * 为什么改成网页而不是 App 直连接口：
 *  直连接口无法要求「人机验证」，而取号端点一直被刷。改走网页后，签发要同时满足
 *  ①App 签名是官方包、②Cloudflare Turnstile 人机验证通过；旧直连入口已整体停用
 *  （统一回「请求被拒绝，请升级最新版本」）。
 *
 * 参数怎么过去：网页通过 JsBridge（[WebBridge.getParams]）向 App 要一份 JSON，
 *  内容与旧直连时代完全一致（ua / deviceinfo / appinfo，另加 signature），
 *  由 [SzlmIdParams] 生成 —— deviceinfo 必须逐字节稳定，服务端靠它做指纹派生。
 *
 * 结果怎么回来：网页签发成功后回调 [WebBridge.onSuccess]，在这里落盘并关页。
 *  本 Activity 跑在主进程（不像 [com.example.c001apk.ui.others.WebViewActivity] 那样单独开进程），
 *  否则 [com.example.c001apk.util.PrefManager] 的 SharedPreferences 会跨进程不一致。
 */
class SzlmIdWebActivity : BaseActivity<ActivitySzlmIdWebBinding>() {

    private var webView: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 预生成的参数 JSON。
     * JsBridge 的方法跑在 WebView 的 JavaBridge 线程上，只能同步返回现成字符串；
     * 而 WebView 默认 UA 又必须在主线程取，所以进页面之前就备好。
     */
    @Volatile
    private var paramsJson: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.toolBar.title = getString(R.string.szlm_id_web_title)
        binding.toolBar.setNavigationIcon(R.drawable.ic_back)
        binding.toolBar.setNavigationOnClickListener { finish() }

        val web = WebView(this)
        web.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding.webContainer.addView(web)
        webView = web

        web.settings.apply {
            // 页面本体、JsBridge、Turnstile 都依赖 JS
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            defaultTextEncodingName = "UTF-8"
            allowFileAccess = false
            // 刻意不套 PrefManager.USER_AGENT（那是给酷安接口伪装的 UA）：
            // Turnstile 要真实浏览器 UA，这里在系统默认 UA 上只追加一个私有标记，
            // 服务端据此判定请求来自 App 内置 WebView（纯浏览器打开的一律拒绝签发）
            userAgentString = SzlmIdParams.webUserAgent(this@SzlmIdWebActivity)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Turnstile 的 iframe 在 challenges.cloudflare.com 上，必须允许第三方 cookie
            setAcceptThirdPartyCookies(web, true)
        }

        web.addJavascriptInterface(WebBridge(), "C001Bridge")

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.isVisible = false
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.isVisible = newProgress < 100
                binding.progressBar.progress = newProgress
            }
        }

        val started = paramsJson.isNotEmpty()
        scope.launch {
            if (!started) paramsJson = SzlmIdParams.buildForWeb(this@SzlmIdWebActivity)
            web.loadUrl(SzlmIdParams.WEB_ENTRY)
        }
    }

    /** 网页签发成功后落盘：与手动填入走同一套保存逻辑（含重建设备串） */
    private fun onIssued(duid: String) {
        val id = duid.trim()
        if (id.isEmpty()) return
        SzlmIdPrompt.save(id)
        Toast.makeText(this, getString(R.string.szlm_id_fetch_ok, id), Toast.LENGTH_LONG).show()
        finish()
    }

    /** 暴露给网页的 JsBridge。方法名与 webgetv1/index.html 里一一对应 */
    private inner class WebBridge {

        /** 同步返回参数 JSON（网页在页面加载后立刻调用） */
        @JavascriptInterface
        fun getParams(): String = paramsJson

        /** 签发成功：duid 为服务端返回值 */
        @JavascriptInterface
        fun onSuccess(duid: String) {
            runOnUiThread { onIssued(duid ?: "") }
        }
    }

    override fun onDestroy() {
        runCatching {
            webView?.apply {
                removeJavascriptInterface("C001Bridge")
                loadUrl("about:blank")
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                settings.javaScriptEnabled = false
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
        webView = null
        super.onDestroy()
    }
}
