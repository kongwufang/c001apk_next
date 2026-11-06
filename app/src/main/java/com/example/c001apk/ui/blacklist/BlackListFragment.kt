package com.example.c001apk.ui.blacklist

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.FragmentBlackListBinding
import com.example.c001apk.logic.model.StringEntity
import com.example.c001apk.ui.search.HistoryAdapter
import com.example.c001apk.ui.topic.TopicActivity
import com.example.c001apk.ui.user.UserActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.makeToast
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 用户 / 话题黑名单。
 *
 * ★ 2026-11-06 从 [BlackListActivity] 抽成 Fragment（阶段③ 第 3 个迁移样例）。
 *
 * 比前两个多出的改动：
 *   ① 工具栏菜单（导出/导入备份）从 `binding.toolBar.inflateMenu` 改成 [MenuProvider]
 *      ⇒ 菜单挂到**宿主**的 toolbar 上 ✓ 离开本页自动清掉 ✓（viewLifecycleOwner 控生命周期 ✓）
 *   ② Hilt ViewModel 带 type 参数：extrasProducer + withCreationCallback ✓
 *      参数来源从 intent 改成 **arguments** ✓
 *   ③ observe 的 owner → viewLifecycleOwner ✓
 *   ④ SAF 启动器（导出 / 导入）在 Fragment 里同样可用 ✓
 *      但回调里要用 `_binding`（视图可能已销毁 ✓）
 *
 * 老的 [BlackListActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class BlackListFragment : Fragment(), IOnItemClickListener {

    private var _binding: FragmentBlackListBinding? = null
    private val binding get() = _binding!!

    private val type: String get() = requireArguments().getString(ARG_TYPE) ?: "user"

    private val viewModel by viewModels<BlackListViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<BlackListViewModel.Factory> { factory ->
                factory.create(type = type)
            }
        }
    )
    private lateinit var mAdapter: HistoryAdapter
    private lateinit var mLayoutManager: FlexboxLayoutManager
    private lateinit var userAdapter: UserBlackListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlackListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title =
            if (type == "user") getString(R.string.user_black_list)
            else getString(R.string.topic_black_list)

        initView()
        initMenu()
        initEditText()
        initEdit()
        initClearHistory()
        initObserve()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 导出 / 导入备份：挂到宿主的 toolbar 上（MenuProvider ✓ 本页销毁自动移除 ✓） */
    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.blacklist_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val ctx = context ?: return false
                return when (menuItem.itemId) {
                    R.id.backup -> {
                        if (currentUids().isEmpty()) {
                            ctx.makeToast("黑名单为空")
                        } else {
                            try {
                                val date = SimpleDateFormat(
                                    "yyyy-MM-dd_HH.mm.ss", Locale.getDefault()
                                ).format(Date())
                                backupSAFLauncher.launch("${type}_blacklist_$date.json")
                            } catch (e: Exception) {
                                ctx.makeToast("导出失败")
                                e.printStackTrace()
                            }
                        }
                        true
                    }

                    R.id.restore -> {
                        try {
                            restoreSAFLauncher.launch("application/json")
                        } catch (e: Exception) {
                            ctx.makeToast("导入失败")
                            e.printStackTrace()
                        }
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private val backupSAFLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) backup@{ uri ->
            if (uri == null) return@backup
            val ctx = context ?: return@backup
            runCatching {
                ctx.contentResolver.openOutputStream(uri).use { output ->
                    if (output == null) ctx.makeToast("导出失败")
                    else output.write(Gson().toJson(currentUids()).toByteArray())
                }
                ctx.makeToast("导出成功")
            }.onFailure { ctx.makeToast("导出失败：${it.message}") }
        }

    private val restoreSAFLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) restore@{ uri ->
            if (uri == null) return@restore
            val ctx = context ?: return@restore
            runCatching {
                val string = ctx.contentResolver
                    .openInputStream(uri)?.reader().use { it?.readText() }
                    ?: throw IOException("Backup file was damaged")
                val dataList: List<String> = Gson().fromJson(
                    string,
                    Array<String>::class.java
                ).toList()
                val currentList: List<String> = currentUids()
                val newList: List<String> =
                    if (currentList.isEmpty()) dataList
                    else dataList.filter { it !in currentList }
                if (newList.isNotEmpty())
                    viewModel.insertList(newList.map { StringEntity(it) })
                else
                    ctx.makeToast("没有需要导入的新条目")
            }.onFailure {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("导入失败")
                    .setMessage(it.message)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton("Crash Log") { _, _ ->
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("Crash Log")
                            .setMessage(it.stackTraceToString())
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    .show()
            }
        }

    private fun initObserve() {
        if (viewModel.isCloud) {
            viewModel.cloudUsers.observe(viewLifecycleOwner) { list ->
                _binding?.let {
                    it.indicator.isIndeterminate = false
                    it.indicator.isVisible = false
                    it.clearAll.isVisible = list.isNotEmpty()
                    it.title.text = "${getString(R.string.user_black_list)}（${list.size}）"
                }
                userAdapter.submitList(list)
            }
            viewModel.loading.observe(viewLifecycleOwner) { loading ->
                _binding?.let {
                    it.indicator.isIndeterminate = loading
                    it.indicator.isVisible = loading
                }
            }
            viewModel.loadCloudUsers()
        } else {
            viewModel.blackListLiveData.observe(viewLifecycleOwner) { list ->
                _binding?.let {
                    it.indicator.isIndeterminate = false
                    it.indicator.isVisible = false
                    it.clearAll.isVisible = list.isNotEmpty()
                }
                mAdapter.submitList(list)
            }
        }

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let {
                context?.makeToast(it)
            }
        }
    }

    private fun currentUids(): List<String> = if (viewModel.isCloud) {
        viewModel.cloudUsers.value?.mapNotNull { it.uid } ?: emptyList()
    } else {
        viewModel.blackListLiveData.value?.map { it.data } ?: emptyList()
    }

    private fun initClearHistory() {
        binding.clearAll.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            MaterialAlertDialogBuilder(ctx).apply {
                setTitle(
                    if (viewModel.isCloud) "确定移除云端全部黑名单？"
                    else "确定清除全部黑名单？"
                )
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.deleteAll()
                    _binding?.clearAll?.isVisible = false
                }
                show()
            }
        }
    }

    private fun initView() {
        binding.indicator.isIndeterminate = true
        binding.indicator.isVisible = true
        if (viewModel.isCloud) {
            userAdapter = UserBlackListAdapter()
            userAdapter.setOnItemClickListener(
                onItemClick = { user ->
                    user.uid?.let {
                        IntentUtil.startActivity<UserActivity>(requireContext()) {
                            putExtra("id", it)
                        }
                    }
                },
                onRemoveClick = { user ->
                    user.uid?.let { viewModel.removeCloudUser(it) }
                }
            )
            binding.recyclerView.apply {
                adapter = userAdapter
                layoutManager = LinearLayoutManager(requireContext())
            }
        } else {
            mLayoutManager = FlexboxLayoutManager(requireContext())
            mLayoutManager.flexDirection = FlexDirection.ROW
            mLayoutManager.flexWrap = FlexWrap.WRAP
            mAdapter = HistoryAdapter()
            mAdapter.setOnItemClickListener(this)
            binding.recyclerView.apply {
                adapter = mAdapter
                layoutManager = mLayoutManager
            }
        }
    }

    private fun initEditText() {
        val ctx = requireContext()
        binding.title.text = when (type) {
            "user" -> getString(R.string.user_black_list)
            "topic" -> getString(R.string.topic_black_list)
            else -> ""
        }
        binding.editText.highlightColor = ColorUtils.setAlphaComponent(
            MaterialColors.getColor(ctx, androidx.appcompat.R.attr.colorPrimaryDark, 0), 128
        )
        binding.editText.hint = when (type) {
            "user" -> "uid"
            "topic" -> "话题"
            else -> ""
        }
        binding.editText.isFocusable = true
        binding.editText.isFocusableInTouchMode = true
        binding.editText.requestFocus()
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.editText, 0)
        binding.editText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.editText.inputType = when (type) {
            "user" -> EditorInfo.TYPE_CLASS_NUMBER
            else -> EditorInfo.TYPE_CLASS_TEXT
        }
    }

    private fun initEdit() {
        binding.editText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, keyEvent ->
            if ((actionId == EditorInfo.IME_ACTION_UNSPECIFIED || actionId == EditorInfo.IME_ACTION_SEARCH) && keyEvent != null) {
                checkData()
                return@OnEditorActionListener true
            }
            false
        })
    }

    private fun checkData() {
        val text = _binding?.editText?.text?.toString().orEmpty()
        if (text.isEmpty()) return
        viewModel.insertList(text)
        _binding?.editText?.text = null
    }

    override fun onItemClick(data: String) {
        when (type) {
            "user" -> IntentUtil.startActivity<UserActivity>(requireContext()) {
                putExtra("id", data)
            }

            "topic" -> IntentUtil.startActivity<TopicActivity>(requireContext()) {
                putExtra("type", "topic")
                putExtra("title", data)
                putExtra("url", data)
                putExtra("id", "")
            }
        }
    }

    override fun onItemDeleteClick(data: String) {
        viewModel.deleteData(data)
    }

    companion object {
        const val ARG_TYPE = "type"

        fun newInstance(type: String) = BlackListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }
}
