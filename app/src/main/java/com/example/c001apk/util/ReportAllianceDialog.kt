package com.example.c001apk.util

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.c001apk.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「匿名举报联盟」的弹窗流程。
 *
 * 用户 2026-10-31 需求：
 * > 「首次点击会跳出弹窗『如果你认为官方举报是滚木，可以加入我们的匿名举报联盟』
 * >   用户同意后先保存他的偏好 ...
 * >   这个开关开启之后，用户在点击举报按钮，可填举报原因」
 *
 * 流程：
 * ```
 * 点击举报
 *   ├─ 没加入过  => 弹「加入联盟」确认框
 *   │                ├─ 加入 => 保存偏好 + 开启 => 弹原因选择
 *   │                └─ 取消 => 什么都不做
 *   └─ 已加入但开关关着 => 提示「已关闭，可在 设置-推荐流相关 打开」
 *   └─ 已加入且开着 => 弹原因选择
 * ```
 */
object ReportAllianceDialog {

    /**
     * 主入口：点举报。
     *
     * @param onSubmit 用户选好原因后回调（参数为选中的原因编码）
     */
    fun start(activity: Activity, onSubmit: (List<String>) -> Unit) {
        if (ReportAlliance.needsJoinPrompt()) {
            showJoinPrompt(activity) { joined ->
                if (joined) showReasonPicker(activity, onSubmit)
            }
            return
        }
        if (!ReportAlliance.enabled) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.report_alliance_off_title)
                .setMessage(R.string.report_alliance_off_message)
                .setPositiveButton(R.string.report_alliance_enable) { _, _ ->
                    ReportAlliance.enabled = true
                    showReasonPicker(activity, onSubmit)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        showReasonPicker(activity, onSubmit)
    }

    /** 首次的「加入联盟」弹窗 */
    private fun showJoinPrompt(activity: Activity, onResult: (Boolean) -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.report_alliance_join_title)
            .setMessage(R.string.report_alliance_join_message)
            .setPositiveButton(R.string.report_alliance_join) { _, _ ->
                ReportAlliance.join(activity)
                onResult(true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onResult(false) }
            .setCancelable(true)
            .show()
    }

    /**
     * 原因多选。
     *
     * 用 `setMultiChoiceItems` 而不是自定义布局 —— 12 项在系统多选框里能滚动 ✓
     * 不用 `setSingleChoiceItems` ✗ 因为同一条内容可能同时命中多个原因 ✓
     */
    private fun showReasonPicker(activity: Activity, onSubmit: (List<String>) -> Unit) {
        val reasons = ReportAlliance.REASONS
        val labels = reasons.map { it.second }.toTypedArray()
        val checked = BooleanArray(reasons.size)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.report_reason_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.report_submit) { d, _ ->
                val picked = reasons.indices
                    .filter { checked[it] }
                    .map { reasons[it].first }
                if (picked.isEmpty()) {
                    Toast.makeText(
                        activity,
                        R.string.report_need_reason,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onSubmit(picked)
                }
                (d as? AlertDialog)?.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 申诉：被公共黑名单折叠的内容，点「举报」时改走这条 ✓
     *
     * 用户 2026-10-31：「点进去之后如果再点击举报按钮，就是申诉功能，而不是举报功能了，
     *                   可以申诉误拉黑」
     *
     * ⚠️ 申诉不选原因 ✓ 只带一个可空说明 ✓ 服务端 `type=appeal` ✓
     *    **不会**进黑名单聚合 ✓ 也**不触发连坐** ✓
     */
    fun appeal(
        activity: Activity,
        type: String,
        id: String,
        topicId: String? = null,
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.appeal_title)
            .setMessage(R.string.appeal_message)
            .setPositiveButton(R.string.appeal_submit) { _, _ ->
                if (activity.isFinishing || activity.isDestroyed) return@setPositiveButton
                CoroutineScope(Dispatchers.IO).launch {
                    val rid = ReportAlliance.submit(type, id, topicId, emptyList(), null)
                    withContext(Dispatchers.Main) {
                        runCatching {
                            Toast.makeText(
                                activity,
                                if (rid != null) R.string.appeal_submitted
                                else R.string.report_submit_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 统一的上报 + 提示。
     *
     * ⚠️ 成功只提示「已提交」✓ **不透露任何处置结果** ✗
     *    用户要求的就是匿名 + 不公开 ✓
     *
     * ⚠️⚠️ 2026-10-31 修 bug（用户报「点提交显示提交失败」）：
     *    原来是**在主线程**调 [ReportAlliance.submit] ⇒ 它内部是阻塞式
     *    `newCall().execute()` ⇒ Android 抛 `NetworkOnMainThreadException`
     *    ⇒ 被 `runCatching` 吞掉 ⇒ 返回 null ⇒ 一律提示「提交失败」✗
     *    ⇒ 现在挪到 IO 线程 ✓ 结果回主线程弹 Toast ✓
     */
    fun submit(
        activity: Activity,
        type: String,
        id: String,
        topicId: String?,
        reasons: List<String>,
        note: String? = null,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val rid = ReportAlliance.submit(type, id, topicId, reasons, note)
            withContext(Dispatchers.Main) {
                val msg = if (rid != null) {
                    R.string.report_submitted
                } else {
                    R.string.report_submit_failed
                }
                runCatching {
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
