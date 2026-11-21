package com.example.c001apk.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.ui.others.bindUpdateStatus
import com.example.c001apk.util.NoticeRepo
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import com.example.c001apk.util.UpdateState
import com.example.c001apk.view.MaxHeightNestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Optional update card followed by server-driven notice cards in the Mine list. */
internal class SideCardAdapter(
    private val onOpenUrl: (String) -> Unit,
    private val onDownload: (UpdateChecker.UpdateInfo) -> Unit,
    private val onSkipVersion: (Long) -> Unit,
    private val onInstall: (Long) -> Unit,
    private val onNoticeDismissed: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_NOTICE = 0
        const val TYPE_UPDATE = 1
        val UPDATE_STATE_PAYLOAD = Any()
    }

    private var notices = emptyList<NoticeRepo.Card>()
    private var update: UpdateState? = null
    private val noticeOffset: Int get() = if (update == null) 0 else 1

    fun setNotices(newNotices: List<NoticeRepo.Card>) {
        val old = notices
        val current = newNotices.filterNot { PrefManager.isNoticeDismissed(it.id) }
        if (old == current) return
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = current.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition].id == current[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition] == current[newItemPosition]
        })
        notices = current
        val offset = noticeOffset
        diff.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) =
                notifyItemRangeInserted(position + offset, count)

            override fun onRemoved(position: Int, count: Int) =
                notifyItemRangeRemoved(position + offset, count)

            override fun onMoved(fromPosition: Int, toPosition: Int) =
                notifyItemMoved(fromPosition + offset, toPosition + offset)

            override fun onChanged(position: Int, count: Int, payload: Any?) =
                notifyItemRangeChanged(position + offset, count, payload)
        })
    }

    fun setUpdate(state: UpdateState) {
        val old = update
        val current = state.takeIf { it.infoOrNull != null }
        if (old == current) return
        update = current
        when {
            old == null -> notifyItemInserted(0)
            current == null -> notifyItemRemoved(0)
            // Keep the same holder, and do not rebind its changelog when only status changes.
            old.infoOrNull == current.infoOrNull -> notifyItemChanged(0, UPDATE_STATE_PAYLOAD)
            else -> notifyItemChanged(0)
        }
    }

    private fun removeNotice(id: String) {
        val index = notices.indexOfFirst { it.id == id }
        if (index < 0) return
        notices = notices.toMutableList().apply { removeAt(index) }
        notifyItemRemoved(index + noticeOffset)
    }

    override fun getItemCount(): Int = notices.size + noticeOffset

    override fun getItemViewType(position: Int): Int =
        if (position < noticeOffset) TYPE_UPDATE else TYPE_NOTICE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_NOTICE)
            NoticeHolder(inf.inflate(R.layout.item_side_notice, parent, false))
        else
            UpdateHolder(inf.inflate(R.layout.item_side_update, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NoticeHolder -> holder.bind(notices[position - noticeOffset])
            is UpdateHolder -> holder.bind(checkNotNull(update))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (holder is UpdateHolder && payloads.isNotEmpty() &&
            payloads.all { it === UPDATE_STATE_PAYLOAD }
        ) {
            holder.bindState(checkNotNull(update))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    // ---------------------------------------------------------------- 公告卡
    private inner class NoticeHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val card: MaterialCardView = v.findViewById(R.id.card)
        private val title: TextView = v.findViewById(R.id.title)
        private val body: TextView = v.findViewById(R.id.body)
        private val close: ImageView = v.findViewById(R.id.close)
        private val buttons: LinearLayout = v.findViewById(R.id.buttons)
        private val time: TextView = v.findViewById(R.id.time)

        fun bind(c: NoticeRepo.Card) {
            title.text = c.title
            title.visibility = if (c.title.isEmpty()) View.GONE else View.VISIBLE

            body.text = c.body
            body.visibility = if (c.body.isEmpty()) View.GONE else View.VISIBLE

            // ★ 2026-11-07 用户要求：时间那行**默认不显示**（卡片空间宝贵 ✗）
            //   服务器想显示 ⇒ 卡里写 "showTime": true ✓
            val showTime = c.showTime && c.time.isNotEmpty()
            time.text = if (showTime) c.time else ""
            time.visibility = if (showTime) View.VISIBLE else View.GONE

            close.visibility = if (c.dismissible) View.VISIBLE else View.GONE
            close.setOnClickListener {
                PrefManager.dismissNotice(c.id)
                onNoticeDismissed(c.id)
                removeNotice(c.id)
            }

            // 背景色 / 强调色（服务器给了才覆盖 ✓ 没给就用主题 ✓ 深色模式安全 ✓）
            val bg = NoticeRepo.parseColor(c.bgColor)
            if (bg != 0) card.setCardBackgroundColor(bg)
            val accent = NoticeRepo.parseColor(c.accent)
            if (accent != 0) {
                title.setTextColor(accent)
            } else {
                title.setTextColor(
                    MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnSurface)
                )
            }

            // 按钮（最多 3 个 ✓ 服务器没给就不显示 ✓）
            buttons.removeAllViews()
            if (c.buttons.isEmpty()) {
                buttons.visibility = View.GONE
            } else {
                buttons.visibility = View.VISIBLE
                c.buttons.forEachIndexed { i, b ->
                    val btn = MaterialButton(
                        itemView.context, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = b.text
                        textSize = 12f
                        minWidth = 0
                        setPadding(24, 0, 24, 0)
                        isAllCaps = false
                        if (accent != 0) setTextColor(accent)
                        if (i > 0) {
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.marginStart = (8 * itemView.resources.displayMetrics.density).toInt()
                            layoutParams = lp
                        }
                        setOnClickListener {
                            if (b.url.isNotEmpty()) {
                                onOpenUrl(b.url)
                            } else {
                                // url 空 = 「知道了」⇒ 点完就关掉 ✓（服务器没给叉也能关 ✓）
                                PrefManager.dismissNotice(c.id)
                                onNoticeDismissed(c.id)
                                removeNotice(c.id)
                            }
                        }
                    }
                    buttons.addView(btn)
                }
            }
        }
    }

    // ---------------------------------------------------------------- 升级卡
    private inner class UpdateHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.title)
        private val changelogScroll: MaxHeightNestedScrollView = v.findViewById(R.id.changelogScroll)
        private val changelog: TextView = v.findViewById(R.id.changelog)
        private val status: TextView = v.findViewById(R.id.updateStatus)
        private val progress: LinearProgressIndicator = v.findViewById(R.id.updateProgress)
        private val actions: View = v.findViewById(R.id.updateActions)
        private val skip: MaterialButton = v.findViewById(R.id.skip)
        private val action: MaterialButton = v.findViewById(R.id.updateAction)
        private var boundVersionCode: Long? = null
        private var showsInstall = false

        init {
            skip.setOnClickListener {
                when (val state = update) {
                    is UpdateState.Manual, is UpdateState.Ready ->
                        state.infoOrNull?.versionCode?.takeIf { it == boundVersionCode }?.let(onSkipVersion)
                    else -> Unit
                }
            }
            action.setOnClickListener {
                val state = update
                // A queued payload must not change which action the visible label triggers.
                if (state?.infoOrNull?.versionCode != boundVersionCode) return@setOnClickListener
                when (state) {
                    is UpdateState.Manual -> {
                        if (!showsInstall) onDownload(state.info)
                    }
                    is UpdateState.Ready -> {
                        if (showsInstall) onInstall(state.info.versionCode)
                    }
                    else -> Unit
                }
            }
        }

        fun bind(state: UpdateState) {
            val info = checkNotNull(state.infoOrNull)
            val context = itemView.context
            val text = info.changelog.ifBlank { context.getString(R.string.update_no_changelog) }
            if (boundVersionCode != info.versionCode || changelog.text.toString() != text) {
                boundVersionCode = info.versionCode
                changelog.text = text
                changelogScroll.scrollTo(0, 0)
            }
            title.text = context.getString(R.string.update_found_title, info.versionName)
            bindState(state)
        }

        fun bindState(state: UpdateState) {
            bindUpdateStatus(status, progress, state)
            showsInstall = state is UpdateState.Ready
            actions.isVisible = state is UpdateState.Manual || showsInstall
            if (actions.isVisible) {
                action.setText(if (showsInstall) R.string.download_install else R.string.download)
            }
        }
    }
}
