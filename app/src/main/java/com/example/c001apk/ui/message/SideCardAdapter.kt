package com.example.c001apk.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.util.NoticeRepo
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * 侧栏卡片适配器（2026-11-01 新增）
 *
 * 挂在「我的」页 ConcatAdapter 的**最后**（宫格下面 ✓ 用户圈出来的那块空白 ✓）
 *
 * ## 两种卡片
 *   ① 公告卡（[NoticeRepo.Card]）—— 文案/背景/按钮/有没有叉 全由服务器决定 ✓
 *   ② 升级卡（[UpdateChecker.UpdateInfo]）—— 只有两个按钮：跳过此版本 / 下载 ✓
 *
 * ## 已读隐藏
 *   · 公告：点叉 ⇒ `PrefManager.dismissNotice(id)` ⇒ 这张永不再现（除非服务端换 id）✓
 *   · 升级：点叉 或 点「跳过此版本」⇒ 记下 versionCode ⇒ **同版本**不再出现 ✓
 *           新版本来了还会出现 ✓（用户说的「提升更新积极性」✓）
 *
 * ## 数据是「一次性」的
 *   没数据时 [setData] 传空表 ⇒ 这个 adapter 高度 0 ✓ 不占位 ✓
 */
class SideCardAdapter(
    private val onOpenUrl: (String) -> Unit,
    private val onDownload: (UpdateChecker.UpdateInfo) -> Unit,
    private val onSkipVersion: (Long) -> Unit,
    private val onNoticeDismissed: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_NOTICE = 0
        private const val TYPE_UPDATE = 1
    }

    private sealed interface Item {
        data class Notice(val card: NoticeRepo.Card) : Item
        data class Update(val info: UpdateChecker.UpdateInfo) : Item
    }

    private val items = ArrayList<Item>()

    /**
     * 一次性设置数据（先升级卡，再公告卡 ✓ 升级更重要 ✓）
     */
    fun setData(notices: List<NoticeRepo.Card>, update: UpdateChecker.UpdateInfo?) {
        items.clear()
        if (update != null && update.isNewer && update.versionCode != PrefManager.skippedUpdateCode)
            items.add(Item.Update(update))
        notices.forEach { items.add(Item.Notice(it)) }
        notifyDataSetChanged()
    }

    /** 只移除一条公告（点叉后立刻消失 ✓ 不用整页刷新 ✓） */
    fun removeNotice(id: String) {
        val idx = items.indexOfFirst { it is Item.Notice && it.card.id == id }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    /** 只移除升级卡（跳过此版本后 ✓） */
    fun removeUpdate() {
        val idx = items.indexOfFirst { it is Item.Update }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Notice -> TYPE_NOTICE
        is Item.Update -> TYPE_UPDATE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_NOTICE)
            NoticeHolder(inf.inflate(R.layout.item_side_notice, parent, false))
        else
            UpdateHolder(inf.inflate(R.layout.item_side_update, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val it = items[position]) {
            is Item.Notice -> (holder as NoticeHolder).bind(it.card)
            is Item.Update -> (holder as UpdateHolder).bind(it.info)
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

            time.text = c.time
            time.visibility = if (c.time.isEmpty()) View.GONE else View.VISIBLE

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
        private val card: MaterialCardView = v.findViewById(R.id.card)
        private val title: TextView = v.findViewById(R.id.title)
        private val changelog: TextView = v.findViewById(R.id.changelog)
        // ★ 2026-11-01：这张卡**没有叉** ✗ 只能点下面两个按钮（用户要求）
        private val skip: MaterialButton = v.findViewById(R.id.skip)
        private val download: MaterialButton = v.findViewById(R.id.download)

        fun bind(info: UpdateChecker.UpdateInfo) {
            card.setCardBackgroundColor(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceVariant)
            )
            title.text = itemView.context.getString(
                R.string.update_found_title, info.versionName
            )
            changelog.text = info.changelog.ifBlank {
                itemView.context.getString(R.string.update_no_changelog)
            }

            val skipIt = View.OnClickListener {
                PrefManager.skippedUpdateCode = info.versionCode
                onSkipVersion(info.versionCode)
                removeUpdate()
            }
            skip.setOnClickListener(skipIt)
            download.setOnClickListener { onDownload(info) }
        }
    }
}
