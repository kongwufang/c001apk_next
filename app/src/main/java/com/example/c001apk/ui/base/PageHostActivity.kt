package com.example.c001apk.ui.base

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityPageHostBinding
import com.example.c001apk.ui.settings.SettingsPreferenceFragment
import com.example.c001apk.util.TransitionAnim

/**
 * 单 Activity 试点宿主（debug2 专属）。
 *
 * 目的：验证「转场动画期间**可以点击**、点击可**立即打断**上一个动画」。
 *
 * 为什么 window 动画做不到：`overridePendingTransition` / `ActivityOptions.makeCustomAnimation`
 * 由 WindowManager 驱动，动画期间系统**延迟派发触摸** ⇒ 必须等播完 ✗。
 * 而 Fragment 的 [androidx.fragment.app.FragmentTransaction.setCustomAnimations] 是**视图动画**，
 * 跑在 Fragment 的 View 上 ⇒ 不挡触摸 ✓，新事务也能立刻顶掉正在播的动画 ✓。
 *
 * ⚠️ 进场不要用 `IntentUtil.startActivity`（它会带 enterOptions 播 window 动画 ✗），
 *    直接用 `startActivity(intent)`：宿主机立刻出现，动画交给 Fragment ✓
 * ⚠️ 已知代价（试点阶段接受）：Fragment 1.7.0 的**预测性返回**只对
 * `res/animator` 或 AndroidX Transition 1.5.0+ 生效，用 `res/anim` 的视图动画时
 * 右滑没有跟手预览 ✗。试点验证完手感后，再决定是否升级到 animator 版。
 *
 * 用法：
 * ```
 * IntentUtil 里不带 enterOptions（关键：宿主本身不播 window 动画）
 * intent.putExtra(PageHostActivity.ARG_ROOT_KEY, key)   // 可空 = 设置一级页
 * ```
 */
@dagger.hilt.android.AndroidEntryPoint
class PageHostActivity : BaseActivity<ActivityPageHostBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (savedInstanceState == null) {
            openPage(intent.getStringExtra(ARG_ROOT_KEY))
        }
    }

    /** 压一个新的设置页进返回栈（带视图动画 ✓ 动画中可点 ✓ 可被下一次点击打断 ✓） */
    fun openPage(rootKey: String?) {
        val fragment = SettingsPreferenceFragment.newInstance(rootKey)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                TransitionAnim.fragmentEnter(),
                TransitionAnim.fragmentExit(),
                TransitionAnim.fragmentPopEnter(),
                TransitionAnim.fragmentPopExit(),
            )
            .replace(R.id.pageHostContainer, fragment)
            .addToBackStack(rootKey ?: ROOT_STACK)
            .commit()
    }

    companion object {
        /** 二级 PreferenceScreen 的 key，和 SettingsDetailActivity.ARG_ROOT_KEY 同义 */
        const val ARG_ROOT_KEY = "rootKey"
        private const val ROOT_STACK = "settings-root"
    }
}
