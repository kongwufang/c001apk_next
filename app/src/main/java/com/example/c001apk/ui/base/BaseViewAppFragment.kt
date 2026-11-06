package com.example.c001apk.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants
import com.example.c001apk.databinding.BaseFragmentContainerBinding

/**
 * 「详情类」页面的 Fragment 基类 —— **BaseViewActivity 的镜像**。
 *
 * 为什么需要它 ✗：
 *   项目里有两套基类，泛型上界**不一样** ✗
 *     BaseViewActivity<VM : BaseAppViewModel>   ← 详情页（App / User / Topic / Carousel）
 *     BaseViewFragment<VM : BaseViewModel>      ← 列表页
 *   ⇒ 详情页的 ViewModel 塞不进 BaseViewFragment ✗
 *
 * 本类只镜像**与页面结构有关**的部分：
 *   · 相同的布局 base_fragment_container（indicator + errorLayout + fragmentContainer ✓）
 *   · 相同的状态机（Loading → fetchData / LoadingDone → beginTransaction / 两种 Error ✓）
 *   · 相同的抽象钩子（viewModel / getSavedData / initData / beginTransaction / fetchData ✓）
 *
 * ⚠️ 不镜像 Activity 专属的东西（主题 overlay、窗口底色、attachBaseContext 字体缩放 ✗）
 *    —— 这些由**宿主** PageHostActivity 统一处理 ✓
 *
 * ⚠️ 子页面用 **childFragmentManager** ✓（不污染宿主返回栈 ✗）
 */
abstract class BaseViewAppFragment<VM : BaseAppViewModel> : Fragment() {

    private var _binding: BaseFragmentContainerBinding? = null
    val binding get() = _binding!!

    abstract val viewModel: VM

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BaseFragmentContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getSavedData(savedInstanceState)
        initData()
        initObserve()
        initError()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    open fun getSavedData(savedInstanceState: Bundle?) {}
    open fun initData() {}
    abstract fun beginTransaction()
    abstract fun fetchData()

    private fun initError() {
        binding.errorLayout.retry.setOnClickListener {
            _binding?.errorLayout?.parent?.isVisible = false
            viewModel.activityState.value = LoadingState.Loading
        }
    }

    private fun initObserve() {
        viewModel.activityState.observe(viewLifecycleOwner) {
            when (it) {
                LoadingState.Loading -> {
                    _binding?.indicator?.parent?.apply {
                        isIndeterminate = true
                        isVisible = true
                    }
                    fetchData()
                }

                LoadingState.LoadingDone -> beginTransaction()

                is LoadingState.LoadingError -> {
                    _binding?.errorMessage?.errMsg?.apply {
                        text = it.errMsg
                        isVisible = true
                    }
                }

                is LoadingState.LoadingFailed -> {
                    _binding?.errorLayout?.apply {
                        msg.text = it.msg
                        retry.text =
                            if (it.msg == Constants.LOADING_EMPTY) getString(R.string.refresh)
                            else getString(R.string.retry)
                        parent.isVisible = true
                    }
                }
            }
            if (it !is LoadingState.Loading) {
                _binding?.indicator?.parent?.apply {
                    isIndeterminate = false
                    isVisible = false
                }
            }
        }
    }
}
