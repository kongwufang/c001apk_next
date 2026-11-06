package com.example.c001apk.ui.feed

import android.annotation.SuppressLint
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.ui.base.BaseViewAppFragment
import com.example.c001apk.ui.feed.question.FeedQuestionFragment
import com.example.c001apk.ui.feed.vote.FeedVoteFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

/**
 * 帖子 / 动态详情（也承载投票、问答、离线详情）。
 *
 * ★ 2026-11-07 从 [FeedActivity] 抽成 Fragment（全量迁移第 9 批 —— 调用点最多的页面 ×11 ✓）。
 *
 * 与 Activity 版的差别：
 *   ① 工厂参数改读 **arguments**（id / rid / viewReply / offline ✓）
 *   ② `intent.getParcelableExtra("feedData")` 改成 `requireArguments().getParcelable(...)` ✓
 *   ③ 子页面用 childFragmentManager ✓
 *   ④ initData 里那句 `binding.indicator.parent.isVisible = false` → `_binding?.…` ✓
 *      （initData 在 onViewCreated 里调用，_binding 已就绪 ✓）
 *
 * 老的 [FeedActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class FeedPageFragment : BaseViewAppFragment<FeedViewModel>() {

    private fun argString(key: String): String = requireArguments().getString(key).orEmpty()

    override val viewModel by viewModels<FeedViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<FeedViewModel.Factory> { factory ->
                factory.create(
                    argString("id"),
                    requireArguments().getString("rid"),
                    requireArguments().getBoolean("viewReply", false),
                    requireArguments().getBoolean("offline", false),
                )
            }
        }
    )

    override fun getSavedData(savedInstanceState: Bundle?) {
        if (viewModel.feedData == null) {
            viewModel.feedData = if (SDK_INT >= 33)
                savedInstanceState?.getParcelable("feedData", HomeFeedResponse.Data::class.java)
                    ?: requireArguments().getParcelable("feedData", HomeFeedResponse.Data::class.java)
            else
                @Suppress("DEPRECATION")
                savedInstanceState?.getParcelable("feedData")
                    ?: requireArguments().getParcelable("feedData")
        }
    }

    override fun initData() {
        if (viewModel.isAInit) {
            viewModel.isAInit = false
            val saved = viewModel.feedData
            // 投票/问答详情首屏强依赖详情独有字段，列表项顶不上，仍旧走整页转圈
            if (saved != null && saved.feedType !in listOf("vote", "question")) {
                // 列表项直出首屏：先把手上有的内容挂上去，不等详情，省掉整页转圈
                viewModel.isPreview = true
                viewModel.handleFeedData()
                beginTransaction()
                // 不走 Loading 状态：一是别让整页转圈盖住首屏，二是 initData 早于 initObserve，
                // 发状态会变成粘性事件让 observer 再调一次 beginTransaction（事务提交两次）
                // 这里要用 _binding（视图可能已销毁 ✓）⇒ 基类已把它改成可见 ✓
                _binding?.indicator?.parent?.isVisible = false
                fetchData()
            } else {
                viewModel.activityState.value = LoadingState.Loading
            }
        }
    }

    override fun fetchData() {
        viewModel.fetchFeedData()
    }

    @SuppressLint("CommitTransaction")
    override fun beginTransaction() {
        if (childFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    when (viewModel.feedType) {
                        "vote" -> FeedVoteFragment()
                        "question" -> FeedQuestionFragment()
                        else -> FeedFragment()
                    }
                )
                .commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.feedData?.let { outState.putParcelable("feedData", it) }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_RID = "rid"
        const val ARG_VIEW_REPLY = "viewReply"
        const val ARG_OFFLINE = "offline"
        const val ARG_FEED_DATA = "feedData"

        fun newInstance(
            id: String?,
            rid: String? = null,
            viewReply: Boolean = false,
            offline: Boolean = false,
            feedData: HomeFeedResponse.Data? = null,
        ) = FeedPageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ID, id.orEmpty())
                putString(ARG_RID, rid)
                putBoolean(ARG_VIEW_REPLY, viewReply)
                putBoolean(ARG_OFFLINE, offline)
                putParcelable(ARG_FEED_DATA, feedData)
            }
        }
    }
}
