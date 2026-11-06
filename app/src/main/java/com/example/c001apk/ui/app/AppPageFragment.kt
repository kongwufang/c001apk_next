package com.example.c001apk.ui.app

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.ui.base.BaseViewAppFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

/**
 * 应用详情页。
 *
 * ★ 2026-11-07 从 [AppActivity] 抽成 Fragment（全量迁移第 6 批）。
 *   与 UserFragment / TopicPageFragment 同一套模式：
 *     工厂参数改读 arguments ✓ 子页面 childFragmentManager ✓
 *
 * 老的 [AppActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class AppPageFragment : BaseViewAppFragment<AppViewModel>() {

    private val appId: String
        get() = requireArguments().getString(ARG_ID) ?: "com.coolapk.market"

    override val viewModel by viewModels<AppViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AppViewModel.Factory> { factory ->
                factory.create(id = appId)
            }
        }
    )

    override fun getSavedData(savedInstanceState: Bundle?) {
        if (viewModel.appData == null) {
            viewModel.appData = if (Build.VERSION.SDK_INT >= 33)
                savedInstanceState?.getParcelable("appData", HomeFeedResponse.Data::class.java)
            else
                @Suppress("DEPRECATION")
                savedInstanceState?.getParcelable("appData")
        }
    }

    @SuppressLint("CommitTransaction")
    override fun beginTransaction() {
        if (childFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, AppFragment())
                .commit()
        }
    }

    override fun fetchData() {
        viewModel.fetchAppInfo()
    }

    override fun initData() {
        if (viewModel.isInit) {
            viewModel.isInit = false
            if (viewModel.appData == null) {
                viewModel.activityState.value = LoadingState.Loading
            } else {
                viewModel.handleAppData()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.appData?.let { outState.putParcelable("appData", it) }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val ARG_ID = "id"

        fun newInstance(id: String?) = AppPageFragment().apply {
            arguments = Bundle().apply { putString(ARG_ID, id.orEmpty()) }
        }
    }
}
