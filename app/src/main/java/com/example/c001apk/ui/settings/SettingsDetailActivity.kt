package com.example.c001apk.ui.settings

import android.os.Bundle
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivitySettingsBinding
import com.example.c001apk.ui.base.BaseActivity

/**
 * 设置二级页（外观 / 推荐流相关 / 隐私 / 高级 / 其他）。
 *
 * 为什么是独立 Activity 而不是 SettingsActivity 里的 Fragment 返回栈：
 * Fragment 1.7.0 的预测性返回只对「Animator（res/animator）」或「AndroidX Transition 1.5.0+」
 * 的事务生效，用 res/anim 的 View 动画时右滑不会跟手预览（官方版本说明里有资源对照表）。
 * 做成独立 Activity 后由系统处理跨 Activity 预测性返回，手感和「关于」页、帖子详情页完全一致。
 *
 * 所以这里**不要**注册 OnBackPressedCallback，返回一律交给系统。
 */
@dagger.hilt.android.AndroidEntryPoint
class SettingsDetailActivity : BaseActivity<ActivitySettingsBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.settingsContainer,
                    SettingsPreferenceFragment.newInstance(intent.getStringExtra(ARG_ROOT_KEY))
                )
                .commitNow()
        }
    }

    companion object {
        /** 二级 PreferenceScreen 的 key，对应 settings.xml 里的 android:key */
        const val ARG_ROOT_KEY = "rootKey"
    }
}
