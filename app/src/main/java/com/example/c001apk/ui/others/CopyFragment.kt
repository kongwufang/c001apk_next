package com.example.c001apk.ui.others

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.FragmentCopyBinding
import com.example.c001apk.logic.model.HomeMenu
import com.example.c001apk.ui.home.HomeMenuAdapter
import com.example.c001apk.ui.home.HomeViewModel
import com.example.c001apk.ui.home.ItemTouchHelperCallback
import com.example.c001apk.util.dp
import com.example.c001apk.view.LinearItemDecoration2
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import java.util.regex.Pattern

/**
 * 复制正文 / 编辑 Tab（双模式）。
 *
 * ★ 2026-11-06 从 [CopyActivity] 抽成 Fragment（阶段③ 第 4 个迁移样例）。
 *
 * 两个模式：
 *   · 传 [ARG_TEXT]  ⇒ 纯文本查看（把正文里的 <a> 链接提出来 + 高亮）
 *   · 传 [ARG_TYPE]=homeMenu ⇒ 编辑 Tab（拖拽排序 + 完成保存 + 重启应用）
 *
 * ⚠️ homeMenu 模式取 HomeViewModel 用的是 `ViewModelProvider(requireActivity())` ✓
 *    （与老 CopyActivity 逐字一致 ✓ 作用域落在宿主上 ✓）
 *    保存走 PrefManager ⇒ 重启后主页面读到的就是新顺序 ✓
 *
 * 老的 [CopyActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class CopyFragment : Fragment() {

    private var _binding: FragmentCopyBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private lateinit var mAdapter: HomeMenuAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var menuList: ArrayList<HomeMenu>

    private val text: String? get() = arguments?.getString(ARG_TEXT)
    private val mode: String? get() = arguments?.getString(ARG_TYPE)

    private fun getAllLinkAndText(str: String): String =
        if (TextUtils.isEmpty(str)) ""
        else Pattern.compile("<a class=\"feed-link-url\"\\s+href=\"([^<>\"]*)\"[^<]*[^>]*>")
            .matcher(str).replaceAll(" $1 ")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCopyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 模式 A：纯文本查看
        val body = text
        if (body != null) {
            val linkText = getAllLinkAndText(body)
            binding.textView.apply {
                text = Html.fromHtml(
                    linkText.replace("\n", "<br/>"),
                    Html.FROM_HTML_MODE_COMPACT
                ).toString()
                highlightColor = ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimaryDark, 0), 128
                )
            }
            return
        }

        // 模式 B：编辑 Tab
        if (mode == "homeMenu") {
            // 和老 CopyActivity 完全一样的取法 ✓（activityViewModels 在宿主里也行，
            // 但为了和原实现逐字一致、排除编译风险，先用 ViewModelProvider ✓）
            viewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[HomeViewModel::class.java]
            menuList = ArrayList()
            binding.tabPage.isVisible = true
            (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title =
                getString(R.string.edit_tab)
            initObserve()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initObserve() {
        viewModel.tabListLiveData.observe(viewLifecycleOwner) {
            menuList.addAll(it)
            initView()
        }

        viewModel.restart.observe(viewLifecycleOwner) {
            if (it) {
                val intent = requireContext().packageManager
                    .getLaunchIntentForPackage(requireContext().packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            }
        }
    }

    private fun initView() {
        binding.done.setOnClickListener {
            viewModel.updateTab(menuList.mapIndexed { index, tab -> tab.copy(position = index) })
        }

        mLayoutManager = LinearLayoutManager(requireContext())
        mAdapter = HomeMenuAdapter(menuList)
        binding.recyclerView.apply {
            adapter = mAdapter
            layoutManager = mLayoutManager
            addItemDecoration(LinearItemDecoration2(10.dp))
        }
        val callback: ItemTouchHelper.Callback = ItemTouchHelperCallback(mAdapter)
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView)
    }

    companion object {
        const val ARG_TEXT = "text"
        const val ARG_TYPE = "type"

        fun forText(text: String) = CopyFragment().apply {
            arguments = Bundle().apply { putString(ARG_TEXT, text) }
        }

        fun forHomeMenu() = CopyFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, "homeMenu") }
        }
    }
}
