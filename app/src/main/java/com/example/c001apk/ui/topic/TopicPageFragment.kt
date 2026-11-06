package com.example.c001apk.ui.topic

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants.LOADING_EMPTY
import com.example.c001apk.logic.model.TopicBean
import com.example.c001apk.ui.base.BaseViewAppFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import java.net.URLDecoder

/**
 * 话题 / 商品聚合页。
 *
 * ★ 2026-11-07 从 [TopicActivity] 抽成 Fragment（全量迁移第 5 批，
 *   BaseView 系第二个 —— 它的数据**不依赖登录** ✓ 所以能真验内容 ✓）。
 *
 * 与 Activity 版的差别：
 *   ① 工厂参数改读 **arguments**（url / title 要 URLDecoder 解一次 ✓）
 *   ② 子页面用 childFragmentManager ✓
 *
 * 老的 [TopicActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class TopicPageFragment : BaseViewAppFragment<TopicViewModel>() {

    private fun arg(key: String): String =
        runCatching { URLDecoder.decode(requireArguments().getString(key).orEmpty(), "UTF-8") }
            .getOrDefault(requireArguments().getString(key).orEmpty())

    override val viewModel by viewModels<TopicViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<TopicViewModel.Factory> { factory ->
                factory.create(
                    arg("url"),
                    arg("title"),
                    requireArguments().getString("id").orEmpty(),
                    requireArguments().getString("type").orEmpty(),
                )
            }
        }
    )

    override fun getSavedData(savedInstanceState: Bundle?) {
        if (viewModel.topicList == null) {
            viewModel.topicList = if (Build.VERSION.SDK_INT >= 33)
                savedInstanceState?.getParcelableArrayList("topicList", TopicBean::class.java)
            else
                @Suppress("DEPRECATION")
                savedInstanceState?.getParcelableArrayList("topicList")
        }
    }

    @SuppressLint("CommitTransaction")
    override fun beginTransaction() {
        if (!viewModel.topicList.isNullOrEmpty()) {
            if (childFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                childFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, TopicFragment())
                    .commit()
            }
        } else {
            viewModel.loadingState.value = LoadingState.LoadingError(LOADING_EMPTY)
        }
    }

    override fun fetchData() {
        if (viewModel.type == "topic") {
            viewModel.url = viewModel.url.replace("/t/", "")
            viewModel.fetchTopicLayout()
        } else if (viewModel.type == "product") {
            viewModel.fetchProductLayout()
        }
    }

    override fun initData() {
        if (viewModel.isAInit) {
            viewModel.isAInit = false
            if (viewModel.topicList == null) {
                viewModel.activityState.value = LoadingState.Loading
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.topicList?.let { outState.putParcelableArrayList("topicList", it) }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val ARG_URL = "url"
        const val ARG_TITLE = "title"
        const val ARG_ID = "id"
        const val ARG_TYPE = "type"

        fun newInstance(url: String?, title: String?, id: String?, type: String?) =
            TopicPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url.orEmpty())
                    putString(ARG_TITLE, title.orEmpty())
                    putString(ARG_ID, id.orEmpty())
                    putString(ARG_TYPE, type.orEmpty())
                }
            }
    }
}
