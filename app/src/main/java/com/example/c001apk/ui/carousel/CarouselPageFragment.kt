package com.example.c001apk.ui.carousel

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.model.TopicBean
import com.example.c001apk.ui.base.BaseViewAppFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 轮播/单页容器（#/page?url= 那种内嵌页）。
 *
 * ★ 2026-11-07 从 [CarouselActivity] 抽成 Fragment（全量迁移第 6 批）。
 *   工厂参数改读 arguments（url / title ✓）
 *
 * 老的 [CarouselActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class CarouselPageFragment : BaseViewAppFragment<CarouselViewModel>() {

    private var isSinglePage: Int? = null

    @Inject
    lateinit var viewModelAssistedFactory: CarouselViewModel.Factory

    override val viewModel by viewModels<CarouselViewModel>(
        factoryProducer = {
            CarouselViewModel.provideFactory(
                viewModelAssistedFactory,
                requireArguments().getString(ARG_URL).orEmpty(),
                requireArguments().getString(ARG_TITLE).orEmpty(),
            )
        }
    )

    override fun getSavedData(savedInstanceState: Bundle?) {
        if (viewModel.topicList == null) {
            viewModel.topicList = if (Build.VERSION.SDK_INT >= 33)
                savedInstanceState?.getParcelableArrayList("topicList", TopicBean::class.java)
            else
                @Suppress("DEPRECATION")
                savedInstanceState?.getParcelableArrayList("topicList")
            isSinglePage = savedInstanceState?.getInt("isSinglePage")
        }
    }

    @SuppressLint("CommitTransaction")
    override fun beginTransaction() {
        if (childFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, CarouselPagerFragment())
                .commit()
        }
    }

    override fun fetchData() {
        viewModel.initCarouselList()
    }

    override fun initData() {
        if (viewModel.isAInit) {
            viewModel.isAInit = false
            if (viewModel.topicList == null && isSinglePage == null) {
                viewModel.activityState.value = LoadingState.Loading
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!viewModel.topicList.isNullOrEmpty()) {
            outState.putParcelableArrayList("topicList", viewModel.topicList)
        } else if (!viewModel.dataList.value.isNullOrEmpty()) {
            outState.putInt("isSinglePage", 1)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val ARG_URL = "url"
        const val ARG_TITLE = "title"

        fun newInstance(url: String?, title: String?) = CarouselPageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, url.orEmpty())
                putString(ARG_TITLE, title.orEmpty())
            }
        }
    }
}
