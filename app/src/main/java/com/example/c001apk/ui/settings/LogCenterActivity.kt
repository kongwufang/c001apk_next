package com.example.c001apk.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.c001apk.util.HttpLog
import com.example.c001apk.util.LogFileSink
import com.example.c001apk.util.LogModule
import com.example.c001apk.util.PrefManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 日志中心（2026-11-08）
 *
 * 用户要求：**HTTP 日志和 App 日志合并成一个开关**，日志相关的所有操作都收进这个页面。
 *
 * 页面结构：
 * ```
 * 记录日志            [开关]   ← 同时控制 AppLog（分模块日志）和 HttpLog（请求日志）
 * 日志级别            INFO     ← 点开选 VERBOSE/DEBUG/INFO/WARN/ERROR
 * 日志模块            全部模块  ← 点开多选 14 个模块
 * ─────── App 日志 ───────
 * 查看最近日志                  ← LogViewerActivity
 * 导出 App 日志（zip）
 * 清空 App 日志
 * ─────── HTTP 请求日志 ───────
 * 查看统计                     ← 显示大小/文件数/条数
 * 导出 HTTP 日志（txt）
 * 清空 HTTP 日志
 * ```
 *
 * 为什么用代码搭 UI：一屏列表 + 一个开关，没有复用价值，照 LogViewerActivity 的做法 ✓
 */
class LogCenterActivity : AppCompatActivity() {

    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var container: LinearLayout
    private lateinit var switch: MaterialSwitch
    private lateinit var levelRow: Row
    private lateinit var moduleRow: Row
    private lateinit var httpStatsRow: Row

    private val reqExportHttp = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
            )
        }
        root.addView(
            MaterialToolbar(this).apply {
                title = "日志"
                setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                setNavigationOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        )

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(container) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        buildRows()
        refresh()
    }

    private fun dp(v: Int) = (v * dp).toInt()

    /** 一行：标题 + 副标题 + 可选开关 */
    private inner class Row(
        val title: String,
        val withSwitch: Boolean = false,
        val onClick: (() -> Boolean)? = null,
    ) {
        lateinit var subtitle: TextView
        var switcher: MaterialSwitch? = null
        lateinit var card: MaterialCardView

        fun bind() {
            card = MaterialCardView(this@LogCenterActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                radius = dp(12).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer)
                )
            }
            val inner = LinearLayout(this@LogCenterActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val texts = LinearLayout(this@LogCenterActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            texts.addView(TextView(this@LogCenterActivity).apply {
                text = title
                textSize = 15f
                setTextColor(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
                )
            })
            subtitle = TextView(this@LogCenterActivity).apply {
                textSize = 12f
                setTextColor(
                    MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            }
            texts.addView(subtitle)
            inner.addView(
                texts,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            if (withSwitch) {
                switcher = MaterialSwitch(this@LogCenterActivity).apply {
                    setOnCheckedChangeListener { _, checked -> onToggleUnified(checked) }
                }
                inner.addView(switcher)
            }
            card.addView(inner)
            card.setOnClickListener {
                if (!withSwitch) onClick?.invoke()
                else switcher?.toggle()
            }
            container.addView(card)
        }
    }

    private lateinit var rowEnabled: Row

    private fun buildRows() {
        rowEnabled = Row("记录日志", withSwitch = true)
        rowEnabled.bind()
        levelRow = Row("日志级别") { pickLevel(); true }.also { it.bind() }
        moduleRow = Row("日志模块") { pickModules(); true }.also { it.bind() }

        sectionHeader("App 日志")
        Row("查看最近日志") {
            startActivity(Intent(this, LogViewerActivity::class.java)); true
        }.bind()
        Row("导出 App 日志（zip）") { exportAppLog(); true }.bind()
        Row("清空 App 日志") { clearAppLog(); true }.bind()

        sectionHeader("HTTP 请求日志")
        httpStatsRow = Row("查看统计") {
            refresh()
            Toast.makeText(this, httpStatsRow.subtitle.text, Toast.LENGTH_SHORT).show()
            true
        }.also { it.bind() }
        Row("导出 HTTP 日志（txt）") { exportHttpLog(); true }.bind()
        Row("清空 HTTP 日志") { clearHttpLog(); true }.bind()
    }

    private fun sectionHeader(text: String) {
        container.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(dp(4), dp(10), 0, dp(6))
            setTextColor(
                MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
            )
        })
    }

    private fun refresh() {
        // 统一开关的取值：两边任一开着就显示开（因为写的时候是一起写 ✓）
        switch = rowEnabled.switcher!!
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = PrefManager.logEnabled || PrefManager.recordHttpLog
        switch.setOnCheckedChangeListener { _, checked -> onToggleUnified(checked) }
        rowEnabled.subtitle.text = if (switch.isChecked) "已开启（App 日志 + HTTP 请求日志）"
        else "已关闭"

        levelRow.subtitle.text = when (PrefManager.logLevel.lowercase()) {
            "v" -> "VERBOSE"
            "d" -> "DEBUG"
            "i" -> "INFO"
            "w" -> "WARN"
            "e" -> "ERROR"
            else -> PrefManager.logLevel
        }
        val mods = PrefManager.logModules
        moduleRow.subtitle.text = if (mods.isEmpty()) "全部模块" else mods.sorted().joinToString(", ")

        val (bytes, files, lines) = HttpLog.stats(this)
        httpStatsRow.subtitle.text = if (bytes <= 0) "还没有 HTTP 日志"
        else "${bytes / 1024} KB · $files 个文件 · $lines 条"
    }

    /** 一个开关同时控制两套日志 ✓ */
    private fun onToggleUnified(checked: Boolean) {
        PrefManager.logEnabled = checked
        PrefManager.recordHttpLog = checked
        // App 日志的文件 sink 立刻挂/摘，不用重启 ✓
        LogFileSink.install(checked)
        refresh()
    }

    /**
     * 级别选择
     *
     * ⚠️ 2026-11-08 踩坑：原来在 setSingleChoiceItems 的回调里直接 `d.dismiss()`
     * ⇒ 点一下窗口立刻消失、看不到选中态，像「点了没反应」✗
     * ⇒ 改成标准交互：单选只改临时变量，**点确定才生效**，取消则不变 ✓
     */
    private fun pickLevel() {
        val keys = arrayOf("v", "d", "i", "w", "e")
        val labels = arrayOf("VERBOSE（最详细）", "DEBUG", "INFO（默认）", "WARN", "ERROR（只留错误）")
        var picked = keys.indexOf(PrefManager.logLevel.lowercase()).let { if (it < 0) 2 else it }
        MaterialAlertDialogBuilder(this)
            .setTitle("日志级别")
            .setSingleChoiceItems(labels, picked) { _, which -> picked = which }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PrefManager.logLevel = keys[picked]
                refresh()
            }
            .show()
    }

    private fun pickModules() {
        val modules = LogModule.values()
        val checked = BooleanArray(modules.size) { i -> PrefManager.logModules.contains(modules[i].key) }
        MaterialAlertDialogBuilder(this)
            .setTitle("日志模块（全不选 = 全部）")
            .setMultiChoiceItems(modules.map { it.key }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNeutralButton("全选") { _, _ ->
                PrefManager.logModules = modules.map { it.key }.toSet()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PrefManager.logModules =
                    modules.filterIndexed { i, _ -> checked[i] }.map { it.key }.toSet()
                refresh()
            }
            .show()
    }

    private fun exportAppLog() {
        val file = LogFileSink.export(this)
        if (file == null) {
            toast("还没有落盘的 App 日志")
            return
        }
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
        }.getOrNull()
        if (uri == null) {
            toast("分享失败")
            return
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "导出 App 日志"
            )
        )
    }

    private fun clearAppLog() {
        val files = LogFileSink.files()
        if (files.isEmpty() && com.example.c001apk.util.AppLog.snapshot().isEmpty()) {
            toast("没有 App 日志")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("清空 App 日志")
            .setMessage("将删除 ${files.size} 个文件并清空内存缓冲。此操作不可撤销。")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                LogFileSink.clear()
                refresh()
                toast("已清空")
            }
            .show()
    }

    private fun exportHttpLog() {
        val (bytes, _, _) = HttpLog.stats(this)
        if (bytes <= 0L) {
            toast("还没有 HTTP 日志")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("导出 HTTP 日志")
            .setMessage("导出内容包含请求方法、URL、状态码、耗时与截断后的请求/响应体（已脱敏）。")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val (name, content) = HttpLog.exportAll(this)
                pendingHttp = name to content
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, name)
                    }, reqExportHttp
                )
            }
            .show()
    }

    private var pendingHttp: Pair<String, String>? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqExportHttp && resultCode == RESULT_OK) {
            val (name, content) = pendingHttp ?: return
            val uri = data?.data ?: return
            val ok = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                true
            }.getOrDefault(false)
            toast(if (ok) "已导出 $name" else "导出失败")
            pendingHttp = null
        }
    }

    private fun clearHttpLog() {
        val (bytes, files, lines) = HttpLog.stats(this)
        if (bytes <= 0L) {
            toast("没有 HTTP 日志")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("清空 HTTP 日志")
            .setMessage("将删除 $files 个文件、$lines 条记录。此操作不可撤销。")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = HttpLog.clear(this)
                refresh()
                toast("已清空 $n 条")
            }
            .show()
    }

    private fun toast(s: CharSequence) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
