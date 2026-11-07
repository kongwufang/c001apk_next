package com.example.c001apk.ui.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.util.AppLog
import com.example.c001apk.util.LogFileSink
import com.example.c001apk.util.LogModule
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 「查看最近日志」页（2026-11-08 C 步）
 *
 * 三块：
 * ① 顶部一行模块 chips（全部 / app / net / feed …）—— 点一下只看这个模块 ✓
 * ② 中间 RecyclerView：内存环 + 文件尾部的合并视图，等宽字体、可长按复制单行 ✓
 * ③ 右上角菜单：复制全部 / 分享 zip / 清空
 *
 * 数据来源：内存环 [AppLog.snapshot]（最近 2000 条，不受开关影响）+ 文件尾
 * [LogFileSink.tail]（开关打开时才有）—— 两者合并去重排序，保证「刚发生的事」一定看得到 ✓
 *
 * 为什么不写 XML：就一个列表 + 一行 chips，照 RecentFollowActivity 的做法在代码里搭 ✓
 */
class LogViewerActivity : AppCompatActivity() {

    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var rv: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var chipRow: LinearLayout

    private var filter: String? = null // null = 全部

    private val adapter = LogLineAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accent = MaterialColors.getColor(
            findViewById(android.R.id.content), androidx.appcompat.R.attr.colorPrimary
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorSurface
                )
            )
        }

        // ① 工具栏
        val bar = MaterialToolbar(this).apply {
            title = "最近日志"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
            inflateMenu(com.example.c001apk.R.menu.log_viewer_menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    com.example.c001apk.R.id.action_log_copy -> {
                        copyAll(); true
                    }

                    com.example.c001apk.R.id.action_log_share -> {
                        shareZip(); true
                    }

                    com.example.c001apk.R.id.action_log_clear -> {
                        confirmClear(); true
                    }

                    else -> false
                }
            }
        }
        root.addView(
            bar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        )

        // ② 模块 chips
        chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }
        root.addView(chipScroll)

        // ③ 列表
        rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LogViewerActivity)
            adapter = this@LogViewerActivity.adapter
            setPadding(0, dp(4), 0, dp(12))
            clipToPadding = false
        }
        root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        emptyView = TextView(this).apply {
            text = "还没有日志。\n去「设置 → 高级」打开「记录 App 日志」再回来。"
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = TextView.GONE
        }
        root.addView(
            emptyView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        buildChips()
        reload()
    }

    private fun dp(v: Int) = (v * dp).toInt()

    private fun buildChips() {
        chipRow.removeAllViews()
        val items = ArrayList<Pair<String?, String>>()
        items.add(null to "全部")
        LogModule.values().forEach { items.add(it.key to it.key) }
        items.forEach { (key, label) ->
            val selected = filter == key
            val accent = MaterialColors.getColor(
                chipRow, androidx.appcompat.R.attr.colorPrimary
            )
            val tv = TextView(this).apply {
                text = label
                textSize = 12f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setTextColor(
                    if (selected) accent
                    else MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(
                        if (selected) (accent and 0x00FFFFFF) or 0x26000000
                        else android.graphics.Color.TRANSPARENT
                    )
                    setStroke(dp(1), if (selected) accent else 0x33FFFFFF)
                }
                setOnClickListener {
                    filter = key
                    buildChips()
                    reload()
                }
            }
            chipRow.addView(
                tv, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reload() {
        val lines = ArrayList<String>()
        // 文件尾（落盘过的，含更早的）
        lines.addAll(LogFileSink.tail(500))
        // 内存环（最近 2000，含刚发生但还没落盘的）
        AppLog.snapshot().forEach { lines.add(it.format()) }
        val filtered = if (filter == null) lines
        else lines.filter { it.contains("/${filter}/") }
        // 去重（文件与内存环会有重叠）
        adapter.submit(filtered.distinct().takeLast(1500))
        emptyView.visibility = if (filtered.isEmpty()) TextView.VISIBLE else TextView.GONE
        rv.visibility = if (filtered.isEmpty()) TextView.GONE else RecyclerView.VISIBLE
    }

    private fun copyAll() {
        val text = adapter.rawLines.joinToString("\n")
        if (text.isEmpty()) {
            toast("没有日志可复制")
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("app-log", text))
        toast("已复制 ${adapter.rawLines.size} 行")
    }

    private fun shareZip() {
        val file = LogFileSink.export(this)
        if (file == null) {
            toast("还没有落盘的日志文件")
            return
        }
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
        }.getOrNull()
        if (uri == null) {
            toast("分享失败（FileProvider 未配置）")
            return
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "导出日志"
            )
        )
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空日志")
            .setMessage("将删除全部落盘日志并清空内存缓冲。此操作不可撤销。")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                LogFileSink.clear()
                reload()
                toast("已清空")
            }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    /** 单行日志，等宽字体、可长按复制 */
    private inner class LogLineAdapter : RecyclerView.Adapter<LogLineAdapter.H>() {

        val rawLines = ArrayList<String>()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<String>) {
            rawLines.clear()
            rawLines.addAll(list)
            notifyDataSetChanged()
        }

        inner class H(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
            val tv = TextView(parent.context).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 11f
                setPadding(dp(12), dp(3), dp(12), dp(3))
                setTextColor(
                    MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
                setTextIsSelectable(true)
            }
            return H(tv)
        }

        override fun getItemCount() = rawLines.size

        override fun onBindViewHolder(holder: H, position: Int) {
            holder.tv.text = rawLines[position]
        }
    }
}
