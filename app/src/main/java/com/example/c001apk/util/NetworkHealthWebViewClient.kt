package com.example.c001apk.util

import android.net.Uri
import android.net.http.SslError
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** Observes WebView failures without changing how the page handles them. */
internal open class NetworkHealthWebViewClient : WebViewClient() {
    final override fun onReceivedHttpError(
        view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // Android also reports HTTP errors for subresources through this callback.
        if (errorResponse.statusCode >= 400) {
            reportNetworkFailure(request.url)
        }
    }

    final override fun onReceivedError(
        view: WebView, request: WebResourceRequest, error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        when (error.errorCode) {
            ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_IO, ERROR_TIMEOUT,
            ERROR_FAILED_SSL_HANDSHAKE, ERROR_PROXY_AUTHENTICATION, ERROR_UNKNOWN -> {
                // Chromium folds many transport and navigation errors into ERROR_UNKNOWN.
                // Exclude identifiable cancellations and local policy/cache failures.
                val description = error.description.toString().removePrefix("net::")
                if (description == "ERR_ABORTED" ||
                    description == "ERR_CLEARTEXT_NOT_PERMITTED" ||
                    description == "ERR_ACCESS_DENIED" ||
                    description == "ERR_UNSAFE_REDIRECT" ||
                    description.startsWith("ERR_BLOCKED_BY_") ||
                    description.startsWith("ERR_CACHE_") ||
                    description.startsWith("ERR_FILE_")
                ) return
                reportNetworkFailure(request.url)
            }
        }
    }

    /** Call only when rejecting a recoverable certificate error, not when proceeding. */
    protected fun reportRejectedSslError(error: SslError?) {
        reportNetworkFailure(error?.url?.let(Uri::parse))
    }

    private fun reportNetworkFailure(url: Uri?) {
        if (url == null) return
        val scheme = url.scheme
        if ((scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) &&
            !url.host.isNullOrEmpty()
        ) {
            NetworkHealth.onRequestFailed()
        }
    }
}
