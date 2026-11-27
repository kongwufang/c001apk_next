package com.example.c001apk.ui.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.net.http.SslError
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityWebViewBinding
import com.example.c001apk.logic.model.AccountSession
import com.example.c001apk.logic.network.AccountCallback
import com.example.c001apk.logic.repository.AccountRepository
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.util.AccountRequestIdentity
import com.example.c001apk.util.AccountVault
import com.example.c001apk.util.ActivityCollector
import com.example.c001apk.util.NetworkHealthWebViewClient
import com.example.c001apk.util.SslErrorPrompter
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Credentials are entered on Coolapk's HTTPS page, not in a native form. */
class WebLoginActivity : BaseActivity<ActivityWebViewBinding>() {
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val repository by lazy { AccountRepository() }
    private var verification: Job? = null
    private var complete = false
    private var compatible = false
    private var webReady = false
    private lateinit var loginIdentity: AccountRequestIdentity
    private val draftKey get() = intent.getStringExtra(EXTRA_ADD_ACCOUNT)
    private var elapsed = 0
    private var usedCode: String? = null
    private var lastCandidate: AccountSession? = null

    private val cookieChecker = object : Runnable {
        override fun run() {
            checkLoginCookie()
            if (webReady && compatible && !complete && ++elapsed < 600) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        openLogin(savedInstanceState?.getBoolean("compatible") == true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("compatible", compatible)
        super.onSaveInstanceState(outState)
    }

    private fun openLogin(useCompatible: Boolean) {
        if (complete || isFinishing || isDestroyed) return
        verification?.cancel()
        verification = null
        handler.removeCallbacks(cookieChecker)
        webReady = false
        compatible = useCompatible
        elapsed = 0
        usedCode = null
        lastCandidate = null
        val draftId = draftKey
        val generation = draftId?.let(AccountVault::pendingLogin)
        val identity = AccountRequestIdentity.capture(includeSession = false)
        if (draftId != null && generation != identity.generation) {
            Toast.makeText(this, R.string.accountLoginExpired, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loginIdentity = identity
        releaseWebView()
        supportActionBar?.title = if (compatible) "兼容网页登录" else getString(R.string.loginWeb)
        invalidateOptionsMenu()
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = true
        val view = createWebView()
        webView = view
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(view, true)
        // Await asynchronous cleanup so a previous WebView session cannot be taken as this login.
        cookies.removeAllCookies {
            if (webView !== view || isFinishing || isDestroyed) return@removeAllCookies
            fun loadLogin() {
                if (webView !== view || isFinishing || isDestroyed) return
                webReady = true
                binding.progressBar.isIndeterminate = false
                view.loadUrl(if (compatible) MOBILE_URL else APP_URL)
                if (compatible) handler.postDelayed(cookieChecker, 1000)
            }
            val id = identity.szlmId
            if (id.isNotEmpty()) {
                cookies.setCookie("https://account.coolapk.com", "DID=$id; Domain=.coolapk.com; Path=/; Secure") { set ->
                    if (set == true) loadLogin()
                    else if (webView === view && !isFinishing) {
                        Toast.makeText(this, R.string.accountLoginExpired, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } else loadLogin()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        layoutParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
        ).apply { behavior = ScrollingViewBehavior() }
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            defaultTextEncodingName = "UTF-8"
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            // Keep the system mobile WebView UA; the form itself is controlled by Coolapk.
            if (SDK_INT >= 32) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, true)
                }
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                    WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (webView !== view || !webReady || verification?.isActive == true) return
                binding.progressBar.isVisible = newProgress < 100
                binding.progressBar.progress = newProgress
            }
        }
        webViewClient = object : NetworkHealthWebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                SslErrorPrompter.onSslFailure(error?.let { java.security.cert.CertificateException(it.toString()) })
                reportRejectedSslError(error)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (request.url.scheme != "https") return true
                return handleCallback(view, request.url.toString())
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (url != null && handleCallback(view, url)) view.stopLoading()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (webView !== view) return
                if (url != null && handleCallback(view, url)) return
                checkLoginCookie()
            }
        }
        binding.root.addView(this)
    }

    private fun handleCallback(view: WebView, url: String): Boolean {
        if (webView !== view || !webReady || compatible) return false
        val code = AccountCallback.codeFrom(url) ?: return false
        if (complete || code == usedCode || verification?.isActive == true) return true
        usedCode = code
        verifyLogin { repository.loginWithCode(code, loginIdentity.generation, adding = draftKey != null, requestIdentity = loginIdentity) }
        return true
    }

    private fun checkLoginCookie() {
        if (!webReady || !compatible || complete || isFinishing || verification?.isActive == true) return
        val cookies = CookieManager.getInstance()
        val candidate = COOKIE_URLS.firstNotNullOfOrNull { url ->
            cookies.getCookie(url)?.let { AccountSession.fromWebCookie(it) }
        } ?: return
        if (lastCandidate?.uid == candidate.uid && lastCandidate?.effectiveToken == candidate.effectiveToken) return
        lastCandidate = candidate
        verifyLogin { repository.loginWithCookie(candidate, loginIdentity.generation, adding = draftKey != null, requestIdentity = loginIdentity) }
    }

    private fun verifyLogin(check: suspend () -> AccountRepository.Result) {
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = true
        verification = lifecycleScope.launch {
            val result = check()
            if (isFinishing || isDestroyed) return@launch
            binding.progressBar.isIndeterminate = false
            binding.progressBar.isVisible = false
            when (result) {
                AccountRepository.Result.CONFIRMED -> {
                    complete = true
                    handler.removeCallbacks(cookieChecker)
                    Toast.makeText(this@WebLoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    if (draftKey == null) ActivityCollector.recreateActivity(MainActivity::class.java.name)
                    finish()
                }
                AccountRepository.Result.STALE -> {
                    Toast.makeText(this@WebLoginActivity, "账号状态已变化，请重新打开登录页", Toast.LENGTH_SHORT).show()
                    finish()
                }
                else -> {
                    val message = if (result == AccountRepository.Result.REJECTED) "酷安未确认本次登录，请重新登录。"
                    else "暂未获得酷安的有效登录响应。请检查网络，或换用另一种网页登录方式。"
                    MaterialAlertDialogBuilder(this@WebLoginActivity)
                        .setMessage(message)
                        .setPositiveButton("重新登录") { _, _ -> openLogin(compatible) }
                        .setNeutralButton(if (compatible) "授权码登录" else "兼容网页登录") { _, _ -> openLogin(!compatible) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, SWITCH_MODE, 0, if (compatible) "切换到授权码登录" else "切换到兼容网页登录")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        SWITCH_MODE -> { openLogin(!compatible); true }
        else -> super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && verification?.isActive == true) {
            finish()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun releaseWebView() {
        webView?.let { view ->
            webView = null
            view.stopLoading()
            binding.root.removeView(view)
            view.destroy()
        }
    }

    override fun onDestroy() {
        verification?.cancel()
        handler.removeCallbacksAndMessages(null)
        releaseWebView()
        if (isFinishing) {
            draftKey?.let(AccountVault::clearDraft)
            if (draftKey == null || !complete) CookieManager.getInstance().removeAllCookies(null)
        }
        super.onDestroy()
    }

    companion object {
        /** 账号管理页「添加账号」用；成功后不自己重建主页，交给调用方处理。 */
        const val EXTRA_ADD_ACCOUNT = "add_account"
        private const val SWITCH_MODE = 1
        private const val APP_URL = "https://account.coolapk.com/auth/login?type=coolapk"
        private const val MOBILE_URL = "https://account.coolapk.com/auth/login?type=mobile"
        private val COOKIE_URLS = listOf(
            "https://www.coolapk.com", "https://coolapk.com", "https://account.coolapk.com", "https://m.coolapk.com",
        )
    }
}
