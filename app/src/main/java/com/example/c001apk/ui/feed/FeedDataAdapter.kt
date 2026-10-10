package com.example.c001apk.ui.feed

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.BR
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.constant.Constants
import com.example.c001apk.databinding.ItemFeedArticleActionBinding
import com.example.c001apk.databinding.ItemFeedArticleHeaderBinding
import com.example.c001apk.databinding.ItemFeedArticleImageBinding
import com.example.c001apk.databinding.ItemFeedArticleShareUrlBinding
import com.example.c001apk.databinding.ItemFeedArticleTextBinding
import com.example.c001apk.databinding.ItemFeedContentBinding
import com.example.c001apk.logic.model.FeedArticleContentBean
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.Like
import com.example.c001apk.util.UiSkin

class FeedDataAdapter(
    private val listener: ItemListener,
    feedDataList: List<HomeFeedResponse.Data>?,
    articleList: List<FeedArticleContentBean.Data>?,
    header: HomeFeedResponse.Data? = null,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var feedDataList: List<HomeFeedResponse.Data>? = feedDataList
    private var articleList: List<FeedArticleContentBean.Data>? = articleList

    /** 图文详情的头部项（作者行 + 封面）。只有图文有，动态把它并进内容卡里了。 */
    private var header: HomeFeedResponse.Data? = header

    /** 头部项占着一个位置，图文正文块的 position 都要往后挪一格 */
    private val headerOffset: Int
        get() = if (header != null) 1 else 0

    /**
     * 详情回填：列表项直出首屏时 adapter 拿到的是预览 list 的引用，而 [FeedViewModel.handleFeedData]
     * 每次都新建 list，不重设这里首屏就永远停在预览数据上。
     */
    fun submit(
        feedDataList: List<HomeFeedResponse.Data>?,
        articleList: List<FeedArticleContentBean.Data>?,
        header: HomeFeedResponse.Data?,
    ) {
        this.feedDataList = feedDataList
        this.articleList = articleList
        this.header = header
        notifyDataSetChanged()
    }

    /**
     * 点赞 / 关注状态回填。动态只有内容卡一项；图文是头部作者行（关注）+ 末尾互动栏（点赞）两项，
     * 少通知一项就会出现"点了赞、数字没动"。
     */
    fun notifyFeedStateChanged() {
        if (header != null) {
            notifyItemChanged(0, true)
            notifyItemChanged(itemCount - 1, true)
        } else {
            notifyItemChanged(0, true)
        }
    }

    /** 收藏数变化要整条重绑：动态刷内容卡，图文刷末尾互动栏（favnum 都挂在 data 上） */
    fun notifyFavChanged() {
        if (header != null) notifyItemChanged(itemCount - 1)
        else notifyItemChanged(0)
    }

    class FeedViewHolder(val binding: ItemFeedContentBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Data?) {
            // 字号先定：点赞的图标边长是拿 textSize 铺出来的，必须早于 setVariable
            applySkin()

            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.setVariable(
                BR.likeData,
                Like(
                    data?.likenum ?: "0",
                    data?.userAction?.like ?: 0
                )
            )
            binding.setVariable(
                BR.followAuthor,
                // 列表项不下发这个字段（详情才有）：详情回来前当"未知"，按钮位转圈而不是错显"关注"
                data?.userAction?.followAuthor ?: Constants.FOLLOW_AUTHOR_UNKNOWN
            )
            binding.executePendingBindings()
        }

        /**
         * 应用界面规格表（uiskin）：详情正文要比列表大一号、行距更松（官方是标题 18sp、行距 1.5），
         * 以前这里和列表写死同一套值，层级拉不开。只覆盖配过的项，没配就继续用布局原值。
         */
        private fun applySkin() {
            UiSkin.f("feedDetail.titleTextSize")?.let { binding.messageTitle.setTextSize(it) }
            UiSkin.f("feedDetail.messageTextSize")?.let { binding.message.setTextSize(it) }
            UiSkin.f("feedDetail.messageLineSpacing")?.let {
                binding.message.setLineSpacing(binding.message.lineSpacingExtra, it)
            }
        }
    }

    /**
     * 图文详情的头部：作者行 + 封面。
     * 预览态（列表项直出）就铺这一项，详情回来只在它下面补正文，画面不会往下跳。
     */
    class ArticleHeaderViewHolder(
        val binding: ItemFeedArticleHeaderBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.setVariable(
                BR.followAuthor,
                data?.userAction?.followAuthor ?: Constants.FOLLOW_AUTHOR_UNKNOWN
            )
            binding.executePendingBindings()
        }
    }

    /**
     * 图文正文末尾的互动栏（回复 / 点赞 / 收藏 / 转发）。
     * 绑的是头部那份 `data`——图文全程只有一份 feedData，评论数、点赞数、收藏数都挂在它上面。
     */
    class ArticleActionViewHolder(
        val binding: ItemFeedArticleActionBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.setVariable(
                BR.likeData,
                Like(
                    data?.likenum ?: "0",
                    data?.userAction?.like ?: 0
                )
            )
            binding.executePendingBindings()
        }
    }

    class TextViewHolder(val binding: ItemFeedArticleTextBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedArticleContentBean.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            // title 标记是给图文首行标题打的。不能再按 adapter position 判断：封面挪进头部项后
            // 标题已经不在 0/1 位上，按位置判断的话标题就永远不加粗了
            binding.textView.paint.isFakeBoldText = data?.title == "true"
            binding.executePendingBindings()
        }
    }

    class ImageViewHolder(val binding: ItemFeedArticleImageBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedArticleContentBean.Data?) {
            binding.setVariable(BR.data, data)
            binding.executePendingBindings()
        }
    }

    class ShareUrlViewHolder(
        val binding: ItemFeedArticleShareUrlBinding,
        val listener: ItemListener
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedArticleContentBean.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {

            0 -> {
                val binding = ItemFeedContentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                setFullSpan(parent, binding.root)
                FeedViewHolder(binding, listener)
            }

            4 -> {
                val binding = ItemFeedArticleHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                setFullSpan(parent, binding.root)
                ArticleHeaderViewHolder(binding, listener)
            }

            5 -> {
                val binding = ItemFeedArticleActionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                setFullSpan(parent, binding.root)
                ArticleActionViewHolder(binding, listener)
            }

            1 -> TextViewHolder(
                ItemFeedArticleTextBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), listener
            )

            2 -> ImageViewHolder(
                ItemFeedArticleImageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), listener
            )

            3 -> ShareUrlViewHolder(
                ItemFeedArticleShareUrlBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), listener
            )

            else -> throw IllegalArgumentException("invalid viewType: $viewType")
        }
    }

    private fun setFullSpan(parent: ViewGroup, root: View) {
        with(root.layoutParams) {
            if (parent.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                && this is StaggeredGridLayoutManager.LayoutParams
            )
                isFullSpan = true
        }
    }

    override fun getItemCount(): Int {
        // 属性是 var（详情回填要整体换掉），先落到局部变量才能 smart cast
        val articles = articleList
        // 图文：头部项 + 正文块 + 末尾互动栏
        if (header != null) return headerOffset + (articles?.size ?: 0) + 1
        val feeds = feedDataList
        return if (feeds.isNullOrEmpty() && !articles.isNullOrEmpty()) articles.size
        else if (!feeds.isNullOrEmpty() && articles.isNullOrEmpty()) feeds.size
        else 0
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ArticleHeaderViewHolder -> holder.bind(header)
            is ArticleActionViewHolder -> holder.bind(header)
            is FeedViewHolder -> holder.bind(feedDataList?.getOrNull(position - headerOffset))
            is TextViewHolder -> holder.bind(articleList?.getOrNull(position - headerOffset))
            is ImageViewHolder -> holder.bind(articleList?.getOrNull(position - headerOffset))
            is ShareUrlViewHolder -> holder.bind(articleList?.getOrNull(position - headerOffset))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            if (payloads[0] == true) {
                // 关注状态回填：动态在内容卡上，图文在头部作者行上
                if (holder is ArticleHeaderViewHolder) {
                    holder.bind(header)
                    return
                }
                // 图文的互动栏也挂在 header 上，点赞后就靠这一支刷数字
                if (holder is ArticleActionViewHolder) {
                    holder.bind(header)
                    return
                }
                with(holder as FeedViewHolder) {
                    binding.setVariable(
                        BR.likeData,
                        Like(
                            feedDataList?.getOrNull(0)?.likenum ?: "0",
                            feedDataList?.getOrNull(0)?.userAction?.like ?: 0
                        )
                    )
                    binding.setVariable(
                        BR.followAuthor,
                        feedDataList?.getOrNull(0)?.userAction?.followAuthor
                            ?: Constants.FOLLOW_AUTHOR_UNKNOWN
                    )
                    binding.executePendingBindings()
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (header != null) {
            if (position == 0) return 4
            // 互动栏钉在末位：正文块多少段都不影响它的位置
            if (position == itemCount - 1) return 5
        }
        val articles = articleList
        return if (articles.isNullOrEmpty()) 0
        else when (articles[position - headerOffset].type) {
            "text" -> 1
            "image" -> 2
            "shareUrl" -> 3
            else -> throw IllegalArgumentException("invalid article type: ${articles[position - headerOffset].type}")
        }
    }

}