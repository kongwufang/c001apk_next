package com.example.c001apk.util

import android.app.Activity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 「获取数字联盟 ID」知情同意弹窗。
 *
 * 正文列出会被上传的设备信息，两个出口：
 *  - 不同意，手动填入自己的 ID；
 *  - 知情并同意，请求 [SzlmIdApi] 换取 DUID 并落到 [PrefManager.SZLMID]。
 *
 * 触发点：应用启动时发现 SZLMID 为空（[RiskControlPrompter]），
 * 以及「设置 - 高级 - 数字联盟ID」被点击时。
 */
object SzlmIdPrompt {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 弹知情同意窗。
     * @param onDismiss 弹窗关闭（含两个按钮各自动作）后回调，调用方用它复位重入标记
     */
    fun showConsent(activity: Activity, onDismiss: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismiss?.invoke()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.szlm_id_consent_title)
            .setMessage(R.string.szlm_id_consent_message)
            .setNegativeButton(R.string.szlm_id_consent_manual) { d, _ ->
                d.dismiss()
                showManualInput(activity)
            }
            .setPositiveButton(R.string.szlm_id_consent_agree) { d, _ ->
                d.dismiss()
                requestAndSave(activity)
            }
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    /** 手动填入框（不同意自动获取时走这里，与设置里的编辑框一致） */
    fun showManualInput(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val view = LayoutInflater.from(activity).inflate(R.layout.item_x_app_token, null, false)
        val editText: EditText = view.findViewById(R.id.editText)
        editText.setText(PrefManager.SZLMID)
        MaterialAlertDialogBuilder(activity).apply {
            setView(view)
            setTitle(R.string.szlmId)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ ->
                save(editText.text.toString().trim())
            }
            if (BuildConfig.DEBUG) {
                setNeutralButton(R.string.random_value) { _, _ ->
                    // 调试用：换一份随机 szlmId（不置 szlmIdConfigured，仍算未配置）
                    PrefManager.SZLMID = TokenDeviceUtils.randHexString(16)
                    PrefManager.szlmIdNoticed = true
                    TokenDeviceUtils.applyDefaultFingerprint()
                }
            }
        }.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            editText.requestFocus()
        }.show()
    }

    /** 请求接口取 DUID；期间显示不可取消的进度框 */
    private fun requestAndSave(activity: Activity) {
        val loading = MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.szlm_id_fetching)
            .setCancelable(false)
            .create()
        runCatching { loading.show() }

        scope.launch {
            val result = runCatching { SzlmIdApi.fetch() }
            runCatching { if (loading.isShowing) loading.dismiss() }
            val ctx = activity.applicationContext
            result.onSuccess {
                save(it.duid)
                Toast.makeText(
                    ctx,
                    ctx.getString(
                        if (it.fromCache) R.string.szlm_id_fetch_ok_cached else R.string.szlm_id_fetch_ok,
                        it.duid
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.szlm_id_fetch_failed, it.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** 落盘 + 重建设备串（szlmId 是设备串首字段，改完必须重造才生效） */
    private fun save(id: String) {
        PrefManager.SZLMID = id
        // 填过（非空）才算「已配置」；清空则回到未配置状态
        PrefManager.szlmIdConfigured = id.isNotEmpty()
        PrefManager.szlmIdNoticed = true
        TokenDeviceUtils.applyDefaultFingerprint()
    }
}
