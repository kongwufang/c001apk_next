package com.example.c001apk.ui.settings

import android.os.Bundle
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivitySettingsBinding
import com.example.c001apk.ui.base.BaseActivity

/**
 * 设置一级页。从 MineFragment 右上角的「设置」图标进入。
 *
 * 只承载一级 [SettingsPreferenceFragment]（外观 / 推荐流相关 / 隐私 / 高级 / 其他 / 关于）。
 * 顶部 MaterialToolbar 由本 Activity 自带，提供返回与标题。
 *
 * 二级页在 [SettingsDetailActivity] 里，是独立 Activity —— 这样返回手势和「关于」页、
 * 帖子详情页一样由系统做预测性返回（右滑跟手预览）。改成 Fragment 返回栈是拿不到预览的：
 * Fragment 1.7.0 的预测性返回只对 Animator（res/animator）或 AndroidX Transition 1.5.0+
 * 的事务生效，res/anim 的 View 动画不在支持之列。
 *
 * 注意：这里**不要**注册 OnBackPressedCallback。一旦 app 自己接管返回
 * （哪怕只是 popBackStack），系统就不再播放预测性返回动画，右滑时页面不会跟手，
 * 与帖子详情等其它页面的返回手感就不一致了。
 */
@dagger.hilt.android.AndroidEntryPoint
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsPreferenceFragment.newInstance(null))
                .commitNow()
        }
    }
}
