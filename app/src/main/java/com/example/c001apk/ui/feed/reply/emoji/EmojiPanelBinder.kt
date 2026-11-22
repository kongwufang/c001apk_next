package com.example.c001apk.ui.feed.reply.emoji

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.util.dp
import com.google.android.material.color.MaterialColors

/**
 * Binds the emoji panel to the installed pack.
 *
 * The tab strip is rebuilt whenever the pack changes, so a first download or a background update
 * appears without recreating the screen. Tabs are: 最近 plus one tab per pack group.
 */
internal class EmojiPanelBinder(
    private val activity: Activity,
    private val pager: ViewPager2,
    private val indicator: LinearLayout,
    private val onClickEmoji: (String) -> Unit,
    private val onCountStart: () -> Unit,
    private val onCountStop: () -> Unit,
    private val onClearRecent: (() -> Unit)? = null,
) {
    private val tabs = ArrayList<List<List<String>>>()
    private val adapter = EmojiPagerAdapter(tabs, onClickEmoji, onCountStart, onCountStop)
    private var recent: List<String> = emptyList()

    fun bind() {
        pager.adapter = adapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                select(position)
            }
        })
        render()
    }

    fun updateRecent(codes: List<String>) {
        recent = codes
        tabs.getOrNull(0)?.let {
            tabs[0] = EmojiPanels.pages(codes)
            adapter.notifyItemChanged(0)
        }
    }

    fun refresh() = render()

    private fun render() {
        val titles = EmojiPanels.titles()
        tabs.clear()
        tabs.addAll(EmojiPanels.tabs(recent))
        buildIndicator(titles)
        adapter.notifyDataSetChanged()
        val page = pager.currentItem.coerceIn(0, titles.lastIndex)
        pager.setCurrentItem(page, false)
        select(page)
    }

    private fun buildIndicator(titles: List<String>) {
        indicator.removeAllViews()
        titles.forEachIndexed { index, title ->
            indicator.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
                    .apply { weight = 1f }
                gravity = Gravity.CENTER
                text = title
                background = activity.getDrawable(R.drawable.selector_bg_trans)
                setOnClickListener { pager.setCurrentItem(index, false) }
                if (index == 0 && BuildConfig.DEBUG && onClearRecent != null) {
                    setOnLongClickListener {
                        onClearRecent.invoke()
                        true
                    }
                }
            })
            if (index != titles.lastIndex) {
                indicator.addView(View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(1.dp, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(
                        MaterialColors.getColor(
                            activity,
                            com.google.android.material.R.attr.colorSurfaceVariant,
                            0
                        )
                    )
                })
            }
        }
    }

    private fun select(position: Int) {
        for (index in 0 until indicator.childCount) {
            val child = indicator.getChildAt(index)
            if (child is TextView) {
                val selected = index / 2 == position
                child.background = activity.getDrawable(
                    if (selected) R.drawable.selector_emoji_indicator_selected
                    else R.drawable.selector_emoji_indicator
                )
                child.setTextColor(
                    MaterialColors.getColor(
                        activity,
                        if (selected) com.google.android.material.R.attr.colorOnPrimary
                        else androidx.appcompat.R.attr.colorControlNormal,
                        0
                    )
                )
            }
        }
    }
}
