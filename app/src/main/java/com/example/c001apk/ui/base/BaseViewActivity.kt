package com.example.c001apk.ui.base

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.core.view.isVisible
import com.example.c001apk.R
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants
import com.example.c001apk.databinding.BaseFragmentContainerBinding
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ThemeUtils
import com.example.c001apk.util.TransitionAnim
import rikka.material.app.MaterialActivity

abstract class BaseViewActivity<VM : BaseAppViewModel> : MaterialActivity() {

    abstract val viewModel: VM
    lateinit var binding: BaseFragmentContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BaseFragmentContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyPageBackground()

        getSavedData(savedInstanceState)

        initData()
        initObserve()
        initError()
    }

    /** 是否给窗口上页面底色；本身就是半透明浮层的页面置 false */
    protected open val pageBackground: Boolean = true

    /**
     * 页面底色由**窗口**提供（不再是 `android.R.id.content`）：转场动画作用在整个 window
     * surface 上，窗口必须不透明（见 themes.xml 的 `Theme.C001apk`），底色也就得由窗口自己带。
     *
     * 关键是**什么时候解析**：rikkax 的 MaterialActivity 把「配色 / 夜间」overlay apply 到
     * theme 上的时机晚于 onCreate（在 [onPostCreate]），所以这里不能只靠 onCreate 那一次
     * ——那时读到的还是亮色 colorSurface，暗色模式下页面底色会一直停在浅色（详情页顶栏
     * 是 `android:background="@null"`，会把这条错误底色直接暴露在状态栏区域）。
     * 幂等，重复调用只是重设一次背景色。
     *
     * 用 `peekDecorView()` 而不是 `window.decorView`：后者会把 DecorView 立刻创建出来，
     * 而 [onApplyUserThemeResource] 正是在 DecorView 创建过程中回调的，读它会把这条路径
     * 递归进去。Decor 还没建就跳过，[onPostCreate] 会兜底。
     */
    private fun applyPageBackground() {
        if (!pageBackground) return
        val decor = window.peekDecorView() ?: return
        decor.setBackgroundColor(
            MaterialColors.getColor(decor, com.google.android.material.R.attr.colorSurface)
        )
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // 此时主题 overlay 已经落定，重解析一次才是当前明暗下的真色
        applyPageBackground()
    }

    open fun getSavedData(savedInstanceState: Bundle?) {}

    private fun initError() {
        binding.errorLayout.retry.setOnClickListener {
            binding.errorLayout.parent.isVisible = false
            viewModel.activityState.value = LoadingState.Loading
        }
    }

    private fun initObserve() {
        viewModel.activityState.observe(this) {
            when (it) {
                LoadingState.Loading -> {
                    binding.indicator.parent.isIndeterminate = true
                    binding.indicator.parent.isVisible = true
                    fetchData()
                }

                LoadingState.LoadingDone -> {
                    beginTransaction()
                }

                is LoadingState.LoadingError -> {
                    binding.errorMessage.errMsg.apply {
                        text = it.errMsg
                        isVisible = true
                    }
                }

                is LoadingState.LoadingFailed -> {
                    binding.errorLayout.apply {
                        msg.text = it.msg
                        retry.text =
                            if (it.msg == Constants.LOADING_EMPTY) getString(R.string.refresh)
                            else getString(R.string.retry)
                        parent.isVisible = true
                    }
                }
            }
            if (it !is LoadingState.Loading) {
                binding.indicator.parent.apply {
                    isIndeterminate = false
                    isVisible = false
                }
            }
        }
    }

    abstract fun beginTransaction()

    abstract fun fetchData()

    abstract fun initData()

    override fun attachBaseContext(newBase: Context) {
        val configuration = newBase.resources.configuration
        configuration.fontScale = PrefManager.FONTSCALE.toFloat()
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun computeUserThemeKey() =
        ThemeUtils.colorTheme + ThemeUtils.getNightThemeStyleRes(this)

    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
        if (!ThemeUtils.isSystemAccent) {
            // ① 先落用户选的那套强调色（旧角色集：primary / surface / …）
            theme.applyStyle(ThemeUtils.colorThemeStyleRes, true)
            // ② ★ 2026-11-06：再用它的 colorPrimary 当种子，让 DynamicColors 补齐**全套**
            //    角色 —— 那 20 套 overlay 是 materialthemebuilder 1.3.3 生成的旧角色集 ✗
            //    没有 colorSurfaceContainer* ✗ ⇒ 卡片底色一直是基础主题的紫色系 ✗
            //    这一步只补容器色阶 ✓ 强调色仍由 setThemeOverlay 保底 ✓
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(
                    this,
                    com.google.android.material.color.DynamicColorsOptions.Builder()
                        .setContentBasedSource(ThemeUtils.contentSeedOf(this))
                        .setThemeOverlay(ThemeUtils.colorThemeStyleRes)
                        .build()
                )
            }
        }
        theme.applyStyle(ThemeUtils.getNightThemeStyleRes(this), true) //blackDarkMode
        // overlay 刚落定，把页面底色跟着刷成新明暗下的 colorSurface（Decor 还没建就跳过，
        // onPostCreate 会兜底）
        applyPageBackground()
    }

    override fun finish() {
        super.finish()
        TransitionAnim.applyReturn(this)
    }

}