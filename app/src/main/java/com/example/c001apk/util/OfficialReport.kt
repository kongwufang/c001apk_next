package com.example.c001apk.util

import android.content.Context
import android.widget.Toast
import com.example.c001apk.R
import com.example.c001apk.ui.others.WebViewActivity

/** Only opens the original Coolapk form. The user submits it on the official page. */
internal object OfficialReport {
    private const val BASE = "https://m.coolapk.com/mp/do"

    fun url(entityType: String, id: String?): String? {
        val target = id?.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } && it.any { c -> c != '0' } }
            ?: return null
        return when (entityType) {
            "feed", "feed_reply" -> "$BASE?c=feed&m=report&type=$entityType&id=$target"
            "user" -> "$BASE?c=user&m=report&id=$target"
            else -> null
        }
    }

    fun open(context: Context, entityType: String, id: String?) {
        if (!PrefManager.isLogin) {
            Toast.makeText(context, R.string.report_login_required, Toast.LENGTH_SHORT).show()
            return
        }
        val url = url(entityType, id)
        if (url == null) {
            Toast.makeText(context, R.string.report_no_id, Toast.LENGTH_SHORT).show()
            return
        }
        IntentUtil.startActivity<WebViewActivity>(context) { putExtra("url", url) }
    }
}
