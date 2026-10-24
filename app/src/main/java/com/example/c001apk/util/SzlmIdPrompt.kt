package com.example.c001apk.util

import com.example.c001apk.util.DeviceCode
import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.ui.others.SzlmIdWebActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 「获取数字联盟 ID」知情同意弹窗。
 *
 * 正文列出会被上传的设备信息，两个出口：
 *  - 不同意，手动填入自己的 ID；
 *  - 知情并同意，打开内置网页（[SzlmIdWebActivity]，人机验证 + 签名校验）换取 DUID，
 *    结果落到 [PrefManager.SZLMID]。
 *
 * 触发点：应用启动时发现 SZLMID 为空（[RiskControlPrompter]），
 * 以及「设置 - 高级 - 数字联盟ID」被点击时。
 */
object SzlmIdPrompt {

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
                openWeb(activity)
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
                    rebuildDeviceString()
                }
            }
        }.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            editText.requestFocus()
        }.show()
    }

    /**
     * 打开内置网页换取 DUID（人机验证 + App 签名校验都在网页侧和服务端完成）。
     *
     * 不再由 App 直连接口：`szlmid/request_api.php` 已停用，直连只会拿到
     * 「请求被拒绝，请升级最新版本」。参数经 JsBridge 交给网页，结果由网页回调回来，
     * 保存动作见 [SzlmIdWebActivity.onIssued]。
     */
    private fun openWeb(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching { activity.startActivity(Intent(activity, SzlmIdWebActivity::class.java)) }
    }

    /** 落盘 + 重建设备串（szlmId 是设备串首字段，改完必须重造才生效） */
    internal fun save(id: String) {
        PrefManager.SZLMID = id
        // 填过（非空）才算「已配置」；清空则回到未配置状态
        PrefManager.szlmIdConfigured = id.isNotEmpty()
        PrefManager.szlmIdNoticed = true
        rebuildDeviceString()
        // 换了 duid 立刻验一次 ✓（不用等 8 秒的首次定时器 ✓）
        // 新 duid 无效 ⇒ 立刻弹「可能已失效」⇒ 用户当场就知道这次换的号不行 ✓
        runCatching { SzlmMonitor.checkNow() }
    }

    /**
     * 把新的 szlmId 写进**设备串的首字段** ✓
     *
     * ## 为什么必须在这里做
     * szlmId 是 `X-App-Device` 的**第一个分号字段** ✓，业务请求和 duid 探测用的
     * 都是 `PrefManager.xAppDevice` **不是** `SZLMID` ✗
     * ⇒ 只改 `SZLMID` 不改设备串 ⇒ **换号等于没换** ✗：
     *   探测打出去的还是旧的有效 duid ⇒ 永远判「正常」⇒ 用户以为监测失灵 ✗
     *   （2026-10-24 用户实测：手动填随机值保存后，杀进程重启也没提示 ✓ 就是这个原因）
     *
     * ## 为什么只改首字段
     * szlmId 与机型是两件独立的事 ✓ —— 只替换 `parts[0]`，
     * 机型 / MAC / 尾字段一律保留 ✓，也不碰 [PrefManager.customFingerprint] ✓
     * （跟 [TokenDeviceUtils.migrateSzlmId] 的做法一致 ✓）
     *
     * ## 为什么不再按 customFingerprint 分支
     * 原来只在 `customFingerprint == true` 时重造 ✗，打算把其余情况
     * 交给 `getLastingDeviceCode()` 的「上报真实机型」分支 ✗ —— 但那条分支
     * 只在 `xAppDevice != detectRealDevice().deviceCode` 时才重建 ✗
     * 而真实机型的设备串**不含新 szlmId** ⇒ 永远相等 ⇒ 永远不重建 ✗
     *
     * 解不出结构（用户手填的畸形串）就原样不动 ✓，不反复重试 ✓
     */
    private fun rebuildDeviceString() {
        runCatching {
            val cur = PrefManager.xAppDevice
            if (cur.isEmpty()) return
            val parts = DeviceCode.decode(cur).split(";").toMutableList()
            if (parts.isEmpty()) return
            parts[0] = PrefManager.SZLMID
            PrefManager.xAppDevice = DeviceCode.encode(parts.joinToString(";"))
        }.onFailure {
            android.util.Log.w("SzlmIdPrompt", "重建设备串首字段失败，保持原值", it)
        }
    }
}
