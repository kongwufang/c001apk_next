package com.example.c001apk.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.c001apk.R
import com.example.c001apk.BuildConfig
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.OnboardingPolicy
import com.example.c001apk.util.SzlmIdPrompt
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 首次使用或引导内容修订升级时展示的欢迎/激活流程，由 [PrefManager.onboarded] 决定。
 * 四步：
 * ```
 * ① 欢迎使用 c001apk_next            [快速开始]
 * ② 设置你的机型参数                  [下一步]
 * ③ 取号（数字联盟）及自动刷新偏好      [下一步]（已有已配置的非空 ID 才可用）
 * ④ 确认 SSL 证书校验和 Wi-Fi 下载设置   [完成]
 * ```
 *
 * 四页均在代码中搭建，正文可滚动，底部主按钮固定。
 * 第 ③ 步复用 [SzlmIdPrompt.showConsent] 授权后原生取号或手动填入，已有 ID 继续沿用。
 * 自动刷新开关沿用已有明确选择，否则默认开启；草稿在该页成功点击「下一步」后保存。
 * 第 ④ 步 SSL、Wi-Fi 下载和表情包流量策略沿用已有选择，点击「完成」才保存并标记本次引导完成。
 * 草稿在旋转时保留，页面重绘不会覆盖尚未确认的选择。
 */
class OnboardingActivity : AppCompatActivity() {

    private val dp by lazy { resources.displayMetrics.density }

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var body: LinearLayout
    private lateinit var primary: MaterialButton
    private lateinit var stepLabel: TextView

    private var step = 0

    // 第④步的开关草稿只在用户点完成后保存。
    private var sslVerify = true
    private var autoDownloadUpdatesOnWifi = false
    private var autoRefreshSzlmId = true
    private var emojiPackOnMetered = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sameRevision = savedInstanceState?.getInt("guideRevision", 0) ==
            OnboardingPolicy.requiredRevision(BuildConfig.VERSION_CODE)
        val initialAutoRefresh = if (PrefManager.hasAutoRefreshSzlmIdChoice)
            PrefManager.autoRefreshSzlmId else true
        val initialWifiUpdates = if (PrefManager.hasAutoDownloadUpdatesChoice)
            PrefManager.autoDownloadUpdatesOnWifi else !PrefManager.hasCompletedOnboardingBefore
        val initialEmojiPackMetered = PrefManager.emojiPackDownloadOnMetered
        // Reuse existing choices, but never restore an old revision directly into a later step.
        step = if (sameRevision) savedInstanceState!!.getInt("step").coerceIn(0, 3) else 0
        sslVerify = if (sameRevision) savedInstanceState!!.getBoolean("sslVerify", PrefManager.isVerifySsl)
            else PrefManager.isVerifySsl
        autoRefreshSzlmId = if (sameRevision) savedInstanceState!!.getBoolean("autoRefreshSzlmId", initialAutoRefresh)
            else initialAutoRefresh
        autoDownloadUpdatesOnWifi = if (sameRevision)
            savedInstanceState!!.getBoolean("autoDownloadUpdatesOnWifi", initialWifiUpdates)
        else initialWifiUpdates
        emojiPackOnMetered = if (sameRevision)
            savedInstanceState!!.getBoolean("emojiPackOnMetered", initialEmojiPackMetered)
        else initialEmojiPackMetered

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
            )
        }

        stepLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(
                MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
            )
        }
        root.addView(stepLabel)

        title = TextView(this).apply {
            textSize = 26f
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
            )
            setPadding(0, dp(12), 0, dp(8))
        }
        root.addView(title)

        subtitle = TextView(this).apply {
            textSize = 14f
            setTextColor(
                MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        root.addView(subtitle)

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        // 正文在小屏/横屏时可滚动，取号入口与开关不会被底部主按钮裁掉。
        val bodyScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(bodyScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        primary = MaterialButton(this).apply {
            text = "快速开始"
            textSize = 15f
            setOnClickListener { onPrimary() }
        }
        root.addView(
            primary,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(12)
            }
        )

        setContentView(root)
        render()
    }

    private fun dp(v: Int) = (v * dp).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("guideRevision", OnboardingPolicy.requiredRevision(BuildConfig.VERSION_CODE))
        outState.putInt("step", step)
        outState.putBoolean("sslVerify", sslVerify)
        outState.putBoolean("autoRefreshSzlmId", autoRefreshSzlmId)
        outState.putBoolean("autoDownloadUpdatesOnWifi", autoDownloadUpdatesOnWifi)
        outState.putBoolean("emojiPackOnMetered", emojiPackOnMetered)
        super.onSaveInstanceState(outState)
    }

    /**
     * 回到前台就刷新一次（2026-11-08 用户反馈）
     *
     * 手动填入 ID 会跳到设置页，回来时 **onDismiss 不一定触发** ✗
     * ⇒ 之前第 3 步停在「开始取号 →」、按钮还是灰的，用户点不动「下一步」✗
     * ⇒ 在 onResume 里按当前步骤重画一次，取号成功回来立刻变成「✓ 已取到号」+ 按钮可用 ✓
     */
    override fun onResume() {
        super.onResume()
        if (step == 2) render()
    }

    private fun onPrimary() {
        when (step) {
            0 -> { step = 1; render() }
            1 -> {
                step = 2
                render()
            }
            2 -> {
                // 目标要求：**取号成功才可下一步** ✓ 未取到时按钮不可点（见 render 里 isEnabled）
                if (!hasConfiguredId()) {
                    Toast.makeText(this, "请先完成取号", Toast.LENGTH_SHORT).show()
                    return
                }
                PrefManager.autoRefreshSzlmId = autoRefreshSzlmId
                step = 3
                render()
            }
            else -> finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        PrefManager.isVerifySsl = sslVerify
        PrefManager.autoDownloadUpdatesOnWifi = autoDownloadUpdatesOnWifi
        PrefManager.emojiPackDownloadOnMetered = emojiPackOnMetered
        PrefManager.onboarded = true
        // App 日志/HTTP 日志统一开关跟随「用户体验改善计划」？不跟随：
        // 日志开关是排障用的独立开关，默认关，避免用户不知情地落盘 ✓
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun hasConfiguredId() = PrefManager.szlmIdConfigured && PrefManager.SZLMID.isNotBlank()

    @SuppressLint("SetTextI18n")
    private fun render() {
        body.removeAllViews()
        stepLabel.text = "第 ${step + 1} / 4 步"
        when (step) {
            0 -> {
                primary.isEnabled = true
                title.text = if (PrefManager.hasCompletedOnboardingBefore) "确认更新后的设置" else "欢迎使用 c001apk_next"
                subtitle.text = if (PrefManager.hasCompletedOnboardingBefore)
                    "这次更新新增了偏好选项，请重新确认。已有机型参数和数字联盟ID会继续保留。"
                    else "这是一个第三方酷安客户端。\n接下来有 3 个小步骤：选机型参数、取号、确认几个开关，很快就好。"
                primary.text = "快速开始"
            }

            1 -> {
                primary.isEnabled = true
                title.text = "设置你的机型参数"
                subtitle.text = "机型参数会随请求一起上报，用来让服务端按你的机型返回合适的内容（如机型话题、参数对比）。\n可以先去「设置 → 高级 → 机型参数」里挑一个，也可以稍后再设。"
                primary.text = "下一步"
                // 2026-11-08 用户要求：直接跳「设置 → 高级 → 机型参数」那个页面
                //   （原来是打开设置首页，还得自己点两下才到 ✗）
                body.addView(
                    actionText("去设置机型参数 →") {
                        com.example.c001apk.util.IntentUtil.startActivity<
                            com.example.c001apk.ui.settings.params.ParamsActivity>(this) {}
                    }
                )
            }

            2 -> {
                title.text = "取号"
                subtitle.text = "「数字联盟 ID」是酷安用来识别设备的一个标识。已有 ID 会继续使用；没有 ID 时，请先自动获取或手动填入。"
                primary.text = "下一步"
                // 未取到号 ⇒ 主按钮置灰（取号成功后 render() 会刷新成可用）
                primary.isEnabled = hasConfiguredId()
                body.addView(
                    actionText(
                        if (hasConfiguredId()) "✓ 已取到号（可以下一步了）" else "开始取号 →"
                    ) {
                        SzlmIdPrompt.showConsent(this) {
                            if (hasConfiguredId()) {
                                Toast.makeText(this, "取号成功", Toast.LENGTH_SHORT).show()
                                render()
                            } else {
                                // 用户选了「手动填入」：弹窗关掉后再刷一次，填完就能点下一步 ✓
                                window?.decorView?.postDelayed({ render() }, 800)
                            }
                        }
                    }
                )
                body.addView(switchRow(
                    getString(R.string.auto_refresh_szlm_id),
                    getString(R.string.auto_refresh_szlm_id_summary), autoRefreshSzlmId
                ) { autoRefreshSzlmId = it })
            }

            else -> {
                primary.isEnabled = true
                title.text = "最后确认"
                subtitle.setText(R.string.onboarding_final_settings_summary)
                primary.text = "完成"
                body.addView(switchRow("校验 SSL 证书", "更安全；抓包调试时需关闭", sslVerify) {
                    sslVerify = it
                })
                body.addView(switchRow(
                    getString(R.string.auto_download_updates_on_wifi),
                    getString(R.string.auto_download_updates_on_wifi_summary),
                    autoDownloadUpdatesOnWifi,
                ) { autoDownloadUpdatesOnWifi = it })
                body.addView(choiceRow(
                    "表情包下载",
                    "表情包（默认、酷币、牛牛共 183 张）改为单独下载，约 1.3 MB，以后可随服务端更新。",
                    listOf("仅 Wi-Fi 自动下载", "流量下也自动下载"),
                    if (emojiPackOnMetered) 1 else 0,
                ) { emojiPackOnMetered = it == 1 })
            }
        }
    }

    /** 一行开关 */
    private fun switchRow(
        name: String,
        desc: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        val texts = LinearLayout(this@OnboardingActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        texts.addView(TextView(this@OnboardingActivity).apply {
            text = name
            textSize = 15f
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
            )
        })
        texts.addView(TextView(this@OnboardingActivity).apply {
            text = desc
            textSize = 12f
            setTextColor(
                MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        })
        addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            MaterialSwitch(this@OnboardingActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, v -> onChange(v) }
            }
        )
    }

    /** 一组二选一按钮（默认选中 [selected]），点「完成」时由调用方保存 */
    private fun choiceRow(
        name: String,
        desc: String,
        labels: List<String>,
        selected: Int,
        onChange: (Int) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(4))
        addView(TextView(this@OnboardingActivity).apply {
            text = name
            textSize = 15f
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
            )
        })
        addView(TextView(this@OnboardingActivity).apply {
            text = desc
            textSize = 12f
            setTextColor(
                MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        })
        val buttons = labels.map { label ->
            MaterialButton(
                this@OnboardingActivity,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                id = View.generateViewId()
                text = label
                textSize = 13f
                isCheckable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        }
        val group = com.google.android.material.button.MaterialButtonToggleGroup(
            this@OnboardingActivity
        ).apply {
            isSingleSelection = true
            setPadding(0, dp(8), 0, 0)
            buttons.forEach { addView(it) }
            check(buttons[selected.coerceIn(0, buttons.lastIndex)].id)
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) onChange(buttons.indexOfFirst { it.id == checkedId })
            }
        }
        addView(group, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    /** 一行可点文字（链接风格） */
    private fun actionText(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, dp(12), 0, dp(4))
            setTextColor(
                MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
            )
            setOnClickListener { onClick() }
        }
}
