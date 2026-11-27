package com.example.c001apk.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.c001apk.R

object IntentUtil {

    fun shareText(ctx: Context, text: String) {
        val payload = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(payload, ctx.getString(R.string.share))
        if (ctx !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    inline fun <reified T> startActivity(context: Context, block: Intent.() -> Unit) {
        val intent = Intent(context, T::class.java)
        intent.block()
        TransitionAnim.startActivity(context, intent)
    }

}