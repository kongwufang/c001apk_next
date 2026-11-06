package com.example.c001apk.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.FragmentSpamShieldBinding
import com.example.c001apk.util.makeToast
import dagger.hilt.android.AndroidEntryPoint

/**
 * 其他屏蔽项管理：关键字 / 用户 / 节点（服务端 spam_word_config）。
 *
 * ★ 2026-11-06 从 [SpamShieldActivity] 抽成 Fragment（阶段③ 第 2 个样例）。
 *   迁移套路与 AboutFragment 完全一致：
 *     ① 布局剥 AppBar/toolbar ⇒ fragment_spam_shield.xml
 *     ② binding → _binding（onDestroyView 置空 ✓）
 *     ③ this → requireContext()；observe 的 owner → viewLifecycleOwner ✓
 *   老的 [SpamShieldActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class SpamShieldFragment : Fragment() {

    private var _binding: FragmentSpamShieldBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<SpamShieldViewModel>()

    private lateinit var adapter: SpamShieldAdapter

    /** 关键字 / 用户 / 节点，由调用方传入（默认 word ✓） */
    private val type: String get() = requireArguments().getString(ARG_TYPE) ?: "word"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSpamShieldBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.init(type)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title =
            when (type) {
                "user" -> getString(R.string.spam_shield_user)
                "node" -> getString(R.string.spam_shield_node)
                else -> getString(R.string.spam_shield_word)
            }
        initView()
        initObserve()
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initView() {
        adapter = SpamShieldAdapter { viewModel.remove(it) }
        binding.recyclerView.apply {
            adapter = this@SpamShieldFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        binding.indicator.isIndeterminate = true
        binding.indicator.isVisible = true

        binding.editText.hint = when (type) {
            "user" -> getString(R.string.spam_shield_user_hint)
            "node" -> getString(R.string.spam_shield_node_hint)
            else -> getString(R.string.spam_shield_word_hint)
        }
        binding.editText2.isVisible = type == "node"
        binding.editText2.hint = getString(R.string.spam_shield_node_name_hint)

        binding.add.setOnClickListener { add() }
    }

    private fun add() {
        val value = binding.editText.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) {
            makeToast("请输入内容")
            return
        }
        val name = binding.editText2.text?.toString()?.trim()
        if (type == "node" && name.isNullOrEmpty()) {
            makeToast("请填写节点名称")
            return
        }
        viewModel.add(value, name)
        binding.editText.text = null
        binding.editText2.text = null
    }

    private fun initObserve() {
        viewModel.list.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            _binding?.indicator?.isIndeterminate = it
            _binding?.indicator?.isVisible = it
        }
        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let {
                makeToast(it)
            }
        }
    }

    companion object {
        const val ARG_TYPE = "type"

        fun newInstance(type: String) = SpamShieldFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }
}
