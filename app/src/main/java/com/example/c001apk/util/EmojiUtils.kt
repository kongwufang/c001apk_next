package com.example.c001apk.util

import android.content.Context
import android.graphics.drawable.Drawable
import com.example.c001apk.R
import com.example.c001apk.util.emoji.EmojiPackStore

/**
 * Inline glyphs that are part of the interface rather than emoji artwork.
 *
 * Emoji images used to be compiled in; they now come from the downloadable pack
 * ([EmojiPackStore]). Only these four badges stay as drawable resources, because they mark feed
 * roles and picture placeholders and must render even before the pack is available.
 */
object EmojiUtils {
    val badges: Map<String, Int> = linkedMapOf(
        "[置顶]" to R.drawable.ic_feed_top,
        "[楼主]" to R.drawable.ic_author,
        "[层主]" to R.drawable.ic_subauthor,
        "[图片]" to R.drawable.ic_photo,
    )

    /** Badge drawable, else the installed pack's image for [code]; `null` when unavailable. */
    fun drawable(context: Context, code: String): Drawable? =
        badges[code]?.let { context.getDrawable(it) } ?: EmojiPackStore.drawable(context, code)
}
