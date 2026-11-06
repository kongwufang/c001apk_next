package com.example.c001apk.ui.others

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.databinding.FragmentAboutBinding
import com.example.c001apk.databinding.ItemAboutEntryBinding
import com.example.c001apk.databinding.ItemAboutLinkBinding
import com.example.c001apk.util.GitHubProfile
import com.example.c001apk.util.NetWorkUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

/**
 * 关于本应用（M3E 风格）。
 *
 * ★ 2026-11-06 从 [AboutActivity] 抽成 Fragment（单 Activity 试点的第一个迁移样例）。
 *
 * 迁移只做了三件事：
 *   ① 布局：拿 activity_about.xml 去掉 AppBar/toolbar（宿主提供 ✓）⇒ fragment_about.xml
 *   ② 绑定：Activity 的 `binding` 换成 Fragment 的 `_binding`（onDestroyView 置空 ✓）
 *   ③ 上下文：`this` → `requireContext()`、`lifecycleScope` → `viewLifecycleOwner.lifecycleScope`
 *
 * 老的 [AboutActivity] **一行没动** ✓ 从设置里是否进宿主由调用方决定 ✓
 */
class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    /** 维护者，同时是拉 GitHub bio 用的用户名 */
    private val maintainerUser = "kongwufang"

    /** 装机量统计页（key 与 _rev/stats_key.txt、服务端 conf.php 同一份） */
    private val statsUrl = "https://service.houlangs.cn/c001apk/stats.php"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindAppInfo()
        bindUpdate()
        bindDevelopers()
        bindFeedback()
        bindLicenses()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindAppInfo() = with(binding) {
        appIcon.setImageResource(R.mipmap.ic_launcher)
        val ctx = requireContext()
        appName.text = ctx.applicationInfo.loadLabel(ctx.packageManager)
        appVersion.text = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
        appBuildTime.text = getString(R.string.about_build_time, BuildConfig.BUILD_TIME)
    }

    private fun bindUpdate() = with(binding) {
        switchUpdate.isChecked = PrefManager.isCheckUpdateStable
        switchUpdate.setOnCheckedChangeListener { _, checked ->
            PrefManager.isCheckUpdateStable = checked
        }
        rowUpdateSwitch.setOnClickListener { switchUpdate.toggle() }
        rowCheckNow.setOnClickListener { checkUpdateNow() }
    }

    private fun checkUpdateNow() {
        val ctx = requireContext()
        Toast.makeText(ctx, R.string.about_checking_update, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val info = UpdateChecker.fetchUpdate()
            if (!isAdded) return@launch
            when {
                info == null ->
                    Toast.makeText(ctx, R.string.about_check_update_failed, Toast.LENGTH_SHORT).show()

                info.isNewer ->
                    UpdateChecker.showUpdateDialog(requireActivity(), info) {
                        _binding?.switchUpdate?.isChecked = PrefManager.isCheckUpdateStable
                    }

                else ->
                    Toast.makeText(ctx, R.string.about_is_latest, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindDevelopers() {
        addEntry(
            binding.groupPredecessor, R.drawable.cont_author, "bggRGjQaUbCoE",
            getString(R.string.about_role_developer_designer), "https://github.com/bggRGjQaUbCoE"
        )
        addDivider(binding.groupPredecessor)
        addEntry(
            binding.groupPredecessor, R.drawable.cont_klxiaoniu, "klxiaoniu",
            getString(R.string.about_role_developer_collaborator), "https://github.com/klxiaoniu"
        )
        addEntry(
            binding.groupThanks, R.drawable.cont_people11, "People-11",
            getString(R.string.about_role_thanks), "https://github.com/People-11"
        )
        val me = addEntry(
            binding.groupMaintainer, R.drawable.cont_kongwufang, maintainerUser,
            getString(R.string.about_role_maintainer), "https://github.com/$maintainerUser"
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val bio = GitHubProfile.fetchBio(maintainerUser) ?: return@launch
            me.bio.text = bio
            me.bio.visibility = View.VISIBLE
        }
    }

    private fun bindFeedback() {
        val ctx = requireContext()
        val repoUrl = getString(R.string.about_source_code_url)
        addLink(
            binding.groupFeedback,
            getString(R.string.about_view_source_code, "GitHub"),
            repoUrl,
        ) { UpdateChecker.openExternal(ctx, repoUrl) }

        addLink(
            binding.groupFeedback,
            getString(R.string.about_install_stats),
            getString(R.string.about_install_stats_desc),
        ) { NetWorkUtil.openLink(ctx, statsUrl, getString(R.string.about_install_stats)) }

        viewLifecycleOwner.lifecycleScope.launch {
            UpdateChecker.fetchOrgLinks().forEach { link ->
                if (!isAdded) return@launch
                addLink(binding.groupFeedback, link.name, link.url) {
                    UpdateChecker.openExternal(ctx, link.url, "无法打开群组链接")
                }
            }
        }
    }

    private fun bindLicenses() {
        val ctx = requireContext()
        LICENSES.forEach { license ->
            addLink(
                binding.groupLicense,
                license.name,
                "${license.author} · ${license.license}",
            ) { UpdateChecker.openExternal(ctx, license.url) }
        }
    }

    private fun addEntry(
        container: LinearLayout,
        avatar: Int,
        title: String,
        subtitle: String,
        url: String,
    ): ItemAboutEntryBinding {
        val item = ItemAboutEntryBinding.inflate(layoutInflater, container, false)
        item.avatar.setImageResource(avatar)
        item.title.text = title
        item.subtitle.text = subtitle
        item.root.setOnClickListener { UpdateChecker.openExternal(requireContext(), url) }
        container.addView(item.root)
        return item
    }

    private fun addLink(
        container: LinearLayout,
        title: String,
        subtitle: String,
        onClick: () -> Unit,
    ) {
        val item = ItemAboutLinkBinding.inflate(layoutInflater, container, false)
        item.title.text = title
        item.subtitle.text = subtitle
        item.root.setOnClickListener { onClick() }
        container.addView(item.root)
    }

    private fun addDivider(container: LinearLayout) {
        val divider = View(requireContext())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        lp.marginStart = (72 * resources.displayMetrics.density).toInt()
        divider.layoutParams = lp
        divider.setBackgroundColor(
            MaterialColors.getColor(divider, com.google.android.material.R.attr.colorSurfaceVariant)
        )
        container.addView(divider)
    }

    private data class License(
        val name: String,
        val author: String,
        val license: String,
        val url: String,
    )

    private companion object {
        const val APACHE_2 = "Apache-2.0"
        const val MIT = "MIT"
        const val GPL_V3 = "GPL-3.0"

        val LICENSES = listOf(
            License("kotlin", "JetBrains", APACHE_2, "https://github.com/JetBrains/kotlin"),
            License("AndroidX", "Google", APACHE_2, "https://github.com/androidx/androidx"),
            License(
                "material-components-android", "Google", APACHE_2,
                "https://github.com/material-components/material-components-android"
            ),
            License("RikkaX", "RikkaApps", MIT, "https://github.com/RikkaApps/RikkaX"),
            License("LSPosed", "LSPosed", GPL_V3, "https://github.com/LSPosed/LSPosed"),
            License("LibChecker", "LibChecker", APACHE_2, "https://github.com/LibChecker/LibChecker"),
            License(
                "Hide-My-Applist", "Dr-TSNG", GPL_V3,
                "https://github.com/Dr-TSNG/Hide-My-Applist"
            ),
            License("okhttp", "square", APACHE_2, "https://github.com/square/okhttp"),
            License("retrofit", "square", APACHE_2, "https://github.com/square/retrofit"),
            License("glide", "bumptech", APACHE_2, "https://github.com/bumptech/glide"),
            License("jBCrypt", "jeremyh", APACHE_2, "https://github.com/jeremyh/jBCrypt"),
            License("flexbox-layout", "google", APACHE_2, "https://github.com/google/flexbox-layout"),
            License(
                "glide-transformations", "wasabeef", APACHE_2,
                "https://github.com/wasabeef/glide-transformations"
            ),
            License("jsoup", "jhy", MIT, "https://github.com/jhy/jsoup"),
            License(
                "NineGridImageView", "plain-dev", MIT,
                "https://github.com/plain-dev/NineGridImageView"
            ),
            License("mojito", "mikaelzero", APACHE_2, "https://github.com/mikaelzero/mojito"),
            License(
                "CircleIndicator", "ongakuer", APACHE_2,
                "https://github.com/ongakuer/CircleIndicator"
            ),
            License("libraries", "zhaobozhen", MIT, "https://github.com/zhaobozhen/libraries"),
            License("dagger", "google", APACHE_2, "https://github.com/google/dagger"),
            License(
                "SmoothInputLayout", "AlexMofer", APACHE_2,
                "https://github.com/AlexMofer/SmoothInputLayout"
            ),
        )
    }
}
