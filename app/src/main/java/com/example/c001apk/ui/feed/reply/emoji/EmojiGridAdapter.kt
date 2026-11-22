package com.example.c001apk.ui.feed.reply.emoji

import android.annotation.SuppressLint
import android.os.Build.VERSION.SDK_INT
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.example.c001apk.R
import com.example.c001apk.util.dp
import com.example.c001apk.util.emoji.EmojiPackStore


class EmojiGridAdapter(
    private val emojiList: List<String>,
    private val onClickEmoji: (String) -> Unit,
    private val onCountStart: () -> Unit,
    private val onCountStop: () -> Unit
) : BaseAdapter() {

    override fun getCount() = 28

    override fun getItem(position: Int): Any {
        return 0
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    @SuppressLint("ViewHolder", "ClickableViewAccessibility")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val emoji = emojiList.getOrNull(position)
        return if (emoji != null || position == 27) {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_emoji, parent, false)
            val imageView: ImageView = view.findViewById(R.id.imageView)

            imageView.setImageDrawable(
                emoji?.let { EmojiPackStore.drawable(parent.context, it) }
                    ?: parent.context.getDrawable(R.drawable.ic_backspace)
            )
            emoji?.let {
                view.background = parent.context.getDrawable(R.drawable.selector_emoji)
                if (SDK_INT >= 26)
                    view.tooltipText = it.substring(1, it.lastIndex)
            }
            view.setOnClickListener {
                onClickEmoji(emoji ?: "[c001apk]")
            }
            if (position == 27) {
                if (SDK_INT >= 26)
                    view.tooltipText = null
                view.setOnLongClickListener {
                    onCountStart()
                    false
                }
                view.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                        onCountStop()
                    }
                    false
                }
            }
            view
        } else View(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(48.dp, 48.dp)
            isEnabled = false
        }
    }

}
