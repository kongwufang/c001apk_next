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
            // 三种入口，优先级从上到下 ✓
            //   ① 通用页面 key（全量迁移：每个页面一个 key ✓ 参数沿用老 Activity 的 extras）
            //   ② 显式 Fragment 类名（阶段③ 的通用入口）
            //   ③ 默认：设置页
            //
            // ⚠️ 别写 `return@if` ✗ Kotlin 里 if **不是** lambda ⇒ "Label must be named" 编译错 ✗
            //    （2026-11-06 CI 踩过一次 ✗）这里改成 if / else if / else 链 ✓
            val pageKey = intent.getStringExtra(ARG_PAGE)
            val pageFragment = pageKey?.let { fragmentForPage(it) }
            val wanted = intent.getStringExtra(ARG_FRAGMENT)
            val namedFragment = if (wanted != null) fragmentFor(wanted) else null

            if (pageFragment != null) {
                openFragment(pageFragment, pageKey, intent.getStringExtra(ARG_TITLE))
            } else if (namedFragment != null) {
                openFragment(namedFragment, wanted, intent.getStringExtra(ARG_TITLE))
            } else {
                // ② 默认：设置页。
                //   如果直接指定了二级页（rootKey != null），必须**先铺一级页当底** ✗
                //   否则返回时把二级页弹掉 ⇒ 容器空 ⇒ 白屏 ✗（2026-11-06 实测踩过）
                val rootKey = intent.getStringExtra(ARG_ROOT_KEY)
                openPage(null)          // 一级页当底，不进返回栈 ✓
                if (rootKey != null) openPage(rootKey)   // 二级页压栈 ✓
            }
        }
    }

    /**
     * 类名 → Fragment 实例（**白名单**，不用反射 ✗）。
     *
     * 阶段③ 每迁一个页面，就在这里加一行 ✓ 编译期就能发现漏了 ✓
     * 反射那套在 R8 下要写 keep 规则、拼错类名还是运行时崩 ✗ 不值得 ✓
     */
    /**
     * ★ 通用页面注册表（全量迁移）。
     *
     * 约定：每迁一个页面，就在这里加一个 `key` 分支 ✓
     *   · 参数沿用**老 Activity 的 extras**（id / title / type / uid / pageType / pageParam …✓）
     *   · key 用页面名的小驼峰（collection / search / dyh / fffList …✓）
     *
     * 调用方（新代码）：
     * ```
     * startActivity(PageHostActivity.intent(ctx, "collection", null, "收藏"))
     * // 已经在宿主内 ⇒ (activity as? PageHostActivity)?.openPageByKey("collection")
     * ```
     */
    private fun fragmentForPage(page: String): Fragment? = when (page) {
        // ✅ 收藏（云端）—— newInstance(id, title) ✓ id 传 null 表示「我的收藏」
        "collection" -> com.example.c001apk.ui.collection.CollectionFragment.newInstance(
            null,
            intent.getStringExtra("title") ?: "收藏",
        )

        // ✅ 关注/粉丝/点赞/回复 列表（FFFListActivity → FollowPagerFragment）
        "fffList" -> com.example.c001apk.ui.follow.FollowPagerFragment.newInstance(
            intent.getStringExtra("uid").orEmpty(),
            intent.getStringExtra("type") ?: "feed",
        )

        // ✅ 动态（DyhActivity → DyhFragment）
        "dyh" -> com.example.c001apk.ui.dyh.DyhFragment.newInstance(
            intent.getStringExtra("id").orEmpty(),
            intent.getStringExtra("title").orEmpty(),
        )

        // ✅ 搜索（SearchActivity → SearchFragment）
        "search" -> com.example.c001apk.ui.search.SearchFragment.newInstance(
            intent.getStringExtra("pageType").orEmpty(),
            intent.getStringExtra("pageParam").orEmpty(),
            intent.getStringExtra("title").orEmpty(),
        )

        else -> null
    }

    /** 宿主内压一个通用页面（已在宿主里时用这个 ✓） */
    fun openPageByKey(page: String, title: String? = null): Boolean {
        val f = fragmentForPage(page) ?: return false
        openFragment(f, page, title)
        return true
    }

    private fun fragmentFor(className: String): Fragment? = when (className) {
        SettingsPreferenceFragment::class.java.name ->
            SettingsPreferenceFragment.newInstance(intent.getStringExtra(ARG_ROOT_KEY))

        // 参数设置（Fiddler/机型伪装那页）：官方本来就只是个壳 Activity + Fragment ✓
        // ⇒ 直接进宿主 ✓ 返回也走 Fragment 栈 ✓
        com.example.c001apk.ui.settings.params.ParamsPreferenceFragment::class.java.name ->
            com.example.c001apk.ui.settings.params.ParamsPreferenceFragment()

        // 关于页（2026-11-06 从 AboutActivity 抽出 ✓ 阶段③ 第一个迁移样例）
        com.example.c001apk.ui.others.AboutFragment::class.java.name ->
            com.example.c001apk.ui.others.AboutFragment()


        // 编辑 Tab（CopyActivity 的 homeMenu 模式 ✓ 阶段③ 第 4 个）
        com.example.c001apk.ui.others.CopyFragment::class.java.name ->
            com.example.c001apk.ui.others.CopyFragment.forHomeMenu()

        // 用户/话题黑名单（阶段性③ 第 3 个）✓
        com.example.c001apk.ui.blacklist.BlackListFragment::class.java.name ->
            com.example.c001apk.ui.blacklist.BlackListFragment.newInstance(
                intent.getStringExtra(com.example.c001apk.ui.blacklist.BlackListFragment.ARG_TYPE)
                    ?: "user"
            )

        // 其他屏蔽项（关键字/用户/节点）：type 走 ARG_TYPE ✓
        com.example.c001apk.ui.settings.SpamShieldFragment::class.java.name ->
            com.example.c001apk.ui.settings.SpamShieldFragment.newInstance(
                intent.getStringExtra(com.example.c001apk.ui.settings.SpamShieldFragment.ARG_TYPE)
                    ?: "word"
            )

        else -> null
    }

    /**
     * 压一个设置页。
     *
     * ⚠️ **一级页（rootKey == null）不进返回栈** ✗
     *    2026-11-06 实测：进了栈之后，在设置主页按返回会把**唯一那个 Fragment 弹掉** ✗
     *    ⇒ 容器空了 ⇒ 只剩工具栏 ⇒ **白屏** ✗（用户截图确认）
     *    正式做法：一级页直接 replace 不进栈 ⇒ 返回时栈空 ⇒ Activity 自然 finish ✓
     *    二级页才 addToBackStack ✓
     */
    fun openPage(rootKey: String?) {
        val root = rootKey == null
        openFragment(
            SettingsPreferenceFragment.newInstance(rootKey),
            if (root) null else rootKey,
            null,
            addToBackStack = !root,
        )
    }

    /**
     * ★ 通用入口：把**任意 Fragment** 压进本宿主的返回栈。
     *
     * 阶段③迁移其它页面时只需要：
     * ```
     * PageHostActivity.intent(ctx, SomeFragment::class.java, args, "标题")
     * ```
     * 页面内部再压栈就调 `(activity as? PageHostActivity)?.openFragment(...)` ✓
     *
     * ⚠️ 刻意**不用反射**建 Fragment ✗：反射在 R8 下要 keep 规则 ✓ 且编译期发现不了拼错 ✗
     *    调用方直接 new 好传进来 ✓ 最稳 ✓
     */
    fun openFragment(
        fragment: Fragment,
        tag: String?,
        title: String?,
        addToBackStack: Boolean = true,
    ) {
        if (!title.isNullOrBlank()) supportActionBar?.title = title
        val tx = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                TransitionAnim.fragmentEnter(),
                TransitionAnim.fragmentExit(),
                TransitionAnim.fragmentPopEnter(),
                TransitionAnim.fragmentPopExit(),
            )
            .replace(R.id.pageHostContainer, fragment, tag)
        // ⚠️ 一级页不进栈（否则返回会把它弹掉 ⇒ 白屏 ✗）
        // ⚠️ 入栈名字**必须唯一** ✗ 否则重复进同一个二级页时
        //    FragmentManager 会抛 IllegalStateException（Back stack entry name already exists）✗
        if (addToBackStack) {
            // ⚠️ 用**单参**重载 + 字符串拼唯一名（编译期绝对安全 ✓）
            //    别用 addToBackStack(name, tag) 那个两参重载 ✗ 老版本 Fragment 里没有 ✗
            //    也别写 `System.nanoTime() and 0xFFFF` ✗ Long 和 Int 不能 and ✗
            tx.addToBackStack("${tag ?: ROOT_STACK}#${System.nanoTime()}")
        }
        tx.commit()
    }

    companion object {
        /** 二级 PreferenceScreen 的 key，和 SettingsDetailActivity.ARG_ROOT_KEY 同义 */
        const val ARG_ROOT_KEY = "rootKey"
        private const val ROOT_STACK = "settings-root"

        /**
         * 阶段③ 迁移别的页面时的标准入口（**不带 window 动画** ✓）：
         *
         * ```
         * startActivity(PageHostActivity.intent(this, SomeFragment::class.java, null, "标题"))
         * ```
         *
         * ⚠️ 千万别用 `IntentUtil.startActivity` ✗ 它会带 enterOptions ⇒ 又变成 window 动画 ✗
         */
        /** 通用页面入口（全量迁移首选 ✓ 参数走 Bundle） */
        fun intentByPage(
            context: android.content.Context,
            page: String,
            args: android.os.Bundle? = null,
            title: String? = null,
        ): android.content.Intent =
            android.content.Intent(context, PageHostActivity::class.java).apply {
                putExtra(ARG_PAGE, page)
                args?.let { putExtras(it) }
                if (!title.isNullOrBlank()) putExtra(ARG_TITLE, title)
            }

        fun intent(
            context: android.content.Context,
            fragmentClass: Class<out Fragment>,
            args: android.os.Bundle? = null,
            title: String? = null,
        ): android.content.Intent =
            android.content.Intent(context, PageHostActivity::class.java).apply {
                putExtra(ARG_FRAGMENT, fragmentClass.name)
                args?.let { putExtra(ARG_FRAGMENT_ARGS, it) }
                if (!title.isNullOrBlank()) putExtra(ARG_TITLE, title)
            }

        private const val ARG_FRAGMENT = "fragmentClass"
        /** 通用页面 key（全量迁移用）：见 fragmentFor 的 when 分支 */
        const val ARG_PAGE = "page"
        private const val ARG_FRAGMENT_ARGS = "fragmentArgs"
        private const val ARG_TITLE = "hostTitle"
    }
}
