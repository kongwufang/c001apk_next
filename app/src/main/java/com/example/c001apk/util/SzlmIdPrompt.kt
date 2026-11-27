package com.example.c001apk.util

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.ui.others.SzlmIdActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 「获取数字联盟 ID」入口：手动填入，或进入原生页面直接向上游取号。
 * 只有成功取得有效 ID 才覆盖 [PrefManager.SZLMID]；失败或取消保留现有 ID。
 *
 * 触发点：应用启动时发现 SZLMID 为空（[RiskControlPrompter]），
 * 以及「设置 - 高级 - 数字联盟ID」被点击时。
 */
object SzlmIdPrompt {

    /**
     * 取号入口弹窗（2026-11-08 按用户要求简化）
     *
     * 原来是一大段「知情同意」正文，逐条列了要上传的机型参数 / SSAID / 版本 / UA。
     * 用户的判断是：机型只是标记信息；Android 8+ 起系统对每个应用派发**各自独立**的
     * SSAID，无法用来跨应用追踪设备 ⇒ 没必要拿一大段隐私说明吓用户。
     *
     * 现在只保留标题 + 两个动作：
     * ```
     *   获取数字联盟ID
     *   [手动填入]   [自动获取（推荐）]
     * ```
     * · 自动获取（推荐）→ 使用应用 SSAID 在本地构造请求，直接向上游换取 DUID
     * · 手动填入        → 弹输入框自己粘一个已有的 DUID
     *
     * @param onDismiss 弹窗关闭（含两个按钮各自动作）后回调，调用方用它复位重入标记
     */
    fun showConsent(activity: Activity, onDismiss: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismiss?.invoke()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.szlm_id_consent_title)
            .setNegativeButton(R.string.szlm_id_get_manual) { d, _ ->
                d.dismiss()
                showManualInput(activity)
            }
            .setPositiveButton(R.string.szlm_id_get_auto) { d, _ ->
                d.dismiss()
                openNative(activity)
            }
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    /** 手动填入框（不同意自动获取时走这里，与设置里的编辑框一致） */
    fun showManualInput(activity: Activity, onDone: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val view = LayoutInflater.from(activity).inflate(R.layout.item_x_app_token, null, false)
        val editText: EditText = view.findViewById(R.id.editText)
        val generation = PrefManager.messageAccountGeneration
        editText.setText(PrefManager.SZLMID)
        MaterialAlertDialogBuilder(activity).apply {
            setView(view)
            setTitle(R.string.szlmId)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ ->
                if (save(editText.text.toString().trim(), generation)) onDone?.invoke()
                else Toast.makeText(activity, R.string.accountIdentitySaveRejected, Toast.LENGTH_SHORT).show()
            }
            if (BuildConfig.DEBUG) {
                setNeutralButton(R.string.random_value) { _, _ ->
                    // 调试用：换一份随机 szlmId（不置 szlmIdConfigured，仍算未配置）
                    save(TokenDeviceUtils.randHexString(16), generation)
                }
            }
        }.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            editText.requestFocus()
        }.show()
    }

    /** 原生取号页在主进程中保存 ID，返回后引导页通过 onResume 刷新配置状态。 */
    private fun openNative(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.startActivity(Intent(activity, SzlmIdActivity::class.java))
    }

    internal fun save(
        id: String,
        generation: Long = PrefManager.messageAccountGeneration,
    ): Boolean = PrefManager.saveSzlmId(id, generation)
}
