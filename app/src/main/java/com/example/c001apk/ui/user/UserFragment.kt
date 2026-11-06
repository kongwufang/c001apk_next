package com.example.c001apk.ui.user

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.model.UserProfileResponse
import com.example.c001apk.ui.base.BaseViewAppFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

/**
 * 用户主页。
 *
 * ★ 2026-11-07 从 [UserActivity] 抽成 Fragment（全量迁移第 4 批，
 *   也是 **BaseViewActivity 系**的第一个迁移样例）。
 *
 * 与 Activity 版的差别只有三处：
 *   ① viewModel 的工厂参数从 `intent` 改读 **arguments** ✓
 *   ② 子页面用 childFragmentManager ✓（不污染宿主返回栈 ✓）
 *   ③ 不用管主题 / 字体缩放 / 窗口底色（宿主统一处理 ✓）
 *
 * 老的 [UserActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class UserFragment : BaseViewAppFragment<UserViewModel>() {

    private val uid: String get() = requireArguments().getString(ARG_UID).orEmpty()

    override val viewModel by viewModels<UserViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<UserViewModel.Factory> { factory ->
                factory.create(uid = uid)
            }
        }
    )

    override fun getSavedData(savedInstanceState: Bundle?) {
        if (viewModel.userData == null) {
            viewModel.userData = if (Build.VERSION.SDK_INT >= 33)
                savedInstanceState?.getParcelable("userData", UserProfileResponse.Data::class.java)
            else
                @Suppress("DEPRECATION")
                savedInstanceState?.getParcelable("userData")
        }
    }

    override fun initData() {
        if (viewModel.isAInit) {
            viewModel.isAInit = false
            if (viewModel.userData == null) {
                viewModel.activityState.value = LoadingState.Loading
            } else {
                viewModel.uid = viewModel.userData?.uid.orEmpty()
            }
        }
    }

    @SuppressLint("CommitTransaction")
    override fun beginTransaction() {
        if (childFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, UserPagerFragment())
                .commit()
        }
    }

    override fun fetchData() {
        viewModel.fetchUser()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.userData?.let { outState.putParcelable("userData", it) }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val ARG_UID = "id"

        fun newInstance(uid: String?) = UserFragment().apply {
            arguments = Bundle().apply { putString(ARG_UID, uid.orEmpty()) }
        }
    }
}
