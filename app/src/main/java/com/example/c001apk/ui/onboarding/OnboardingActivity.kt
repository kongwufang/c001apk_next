package com.example.c001apk.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.c001apk.R
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.SzlmIdPrompt
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 首次启动的欢迎/激活流程（2026-11-08）
 *
 * 只有第一次打开才出现（[PrefManager.onboarded] 为 false 时由 `MainActivity` 拉起，见那边注释）。
 * 四步：
 * ```
 * ① 欢迎使用 c001apk_next            [快速开始]
 * ② 设置你的机型参数                  [下一步]
 * ③ 取号（数字联盟）                  [下一步]（取号成功才可用）
 * ④ 三个开关（默认全开）              [完成]
 *      · 校验 SSL 证书
 *      · 加入匿名举报联盟
 *      · 加入用户体验改善计划   [查看隐私协议 ↗]
 * ```
 *
 * 为什么用代码搭 UI：四页都是「标题 + 说明 + 一两个控件」，没有复用价值，
 * 照 `LogCenterActivity` / `RecentFollowActivity` 的做法在代码里搭，避免再维护 4 个 XML ✓
 *
 * ## 第 ③ 步的取号
 * 复用现有的 [SzlmIdPrompt.showConsent]：它内部会走授权 → 取号 → `save(id)`。
 * 取号是否完成，用 [PrefManager.szlmIdConfigured] 判断（现有字段，置位即说明有号 ✓）；
 * 用户也可能先跳过（历史上允许不带号使用），所以「下一步」在拿不到号时仍然可点，
 * 但会给出一条提示，避免卡死新手流程 ✗
 *
 * ## 第 ④ 步的开关
 * 三个开关**默认全部打开**，用户可以手动关；点「完成」才写入 [PrefManager]，
 * 中途退出不落盘（避免「我还没确认就替我开了」）。
 */
class OnboardingActivity : AppCompatActivity() {

    private val dp by lazy { resources.displayMetrics.density }

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var body: LinearLayout
    private lateinit var primary: MaterialButton
    private lateinit var stepLabel: TextView

    private var step = 0

    // 第④步的三个开关（未点完成前只存内存 ✓）
    private var sslVerify = true
    private var reportAlliance = true
    private var improvePlan = true

    private val privacyUrl =
        "https://raw.githubusercontent.com/kongwufang/c001apk_next/debug/docs/PRIVACY-IMPROVE-PLAN.md"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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

    private fun onPrimary() {
        when (step) {
            0 -> { step = 1; render() }
            1 -> { step = 2; render() }
            2 -> {
                // 目标要求：**取号成功才可下一步** ✓ 未取到时按钮不可点（见 render 里 isEnabled）
                if (!PrefManager.szlmIdConfigured) {
                    Toast.makeText(this, "请先完成取号", Toast.LENGTH_SHORT).show()
                    return
                }
                step = 3
                render()
            }
            else -> finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        PrefManager.isVerifySsl = sslVerify
        PrefManager.reportAllianceJoined = reportAlliance
        PrefManager.joinImprovePlan = improvePlan
        PrefManager.onboarded = true
        // App 日志/HTTP 日志统一开关跟随「用户体验改善计划」？不跟随：
        // 日志开关是排障用的独立开关，默认关，避免用户不知情地落盘 ✓
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun render() {
        body.removeAllViews()
        stepLabel.text = "第 ${step + 1} / 4 步"
        when (step) {
            0 -> {
                primary.isEnabled = true
                title.text = "欢迎使用 c001apk_next"
                subtitle.text = "这是一个第三方酷安客户端。\n接下来有 3 个小步骤：选机型参数、取号、确认几个开关，很快就好。"
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
                subtitle.text = "「数字联盟 ID」是酷安用来识别设备的一个标识。取号后请求里会带上它，否则个别接口可能被风控。\n\n点下面按钮授权并取号；取不到也可以先跳过。"
                primary.text = "下一步"
                // 未取到号 ⇒ 主按钮置灰（取号成功后 render() 会刷新成可用）
                primary.isEnabled = PrefManager.szlmIdConfigured
                body.addView(
                    actionText(
                        if (PrefManager.szlmIdConfigured) "✓ 已取到号（可以下一步了）" else "开始取号 →"
                    ) {
                        SzlmIdPrompt.showConsent(this) {
                            if (PrefManager.szlmIdConfigured) {
                                Toast.makeText(this, "取号成功", Toast.LENGTH_SHORT).show()
                                render()
                            }
                        }
                    }
                )
            }

            else -> {
                primary.isEnabled = true
                title.text = "确认几个开关"
                subtitle.text = "下面三个开关默认都是打开的，你可以逐个关掉。点「完成」才会保存。"
                primary.text = "完成"
                body.addView(switchRow("校验 SSL 证书", "更安全，但抓包调试时需关掉", sslVerify) {
                    sslVerify = it
                })
                body.addView(switchRow("加入匿名举报联盟", "举报内容会匿名汇总到服务端", reportAlliance) {
                    reportAlliance = it
                })
                body.addView(
                    switchRow("加入用户体验改善计划", "开启后可收到诊断请求，帮我们定位问题", improvePlan) {
                        improvePlan = it
                    }
                )
                body.addView(
                    actionText("查看隐私协议 ↗") {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                        }
                    }
                )
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
