package com.example.c001apk.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.BR
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemAppVersionBinding
import com.example.c001apk.databinding.ItemCollectionListItemBinding
import com.example.c001apk.databinding.ItemFeedReplyBinding
import com.example.c001apk.databinding.ItemHomeFeedBinding
import com.example.c001apk.databinding.ItemHomeFeedRefreshCardBinding
import com.example.c001apk.databinding.ItemHomeGameCardListBinding
import com.example.c001apk.databinding.ItemHomeGameTabCardBinding
import com.example.c001apk.databinding.ItemHomeHiddenBinding
import com.example.c001apk.databinding.ItemHomeIconLinkGridCardBinding
import com.example.c001apk.databinding.ItemHomeIconMiniScrollCardBinding
import com.example.c001apk.databinding.ItemHomeImageCarouselCardBinding
import com.example.c001apk.databinding.ItemHomeImageTextGridCardBinding
import com.example.c001apk.databinding.ItemHomeImageSquareScrollCardBinding
import com.example.c001apk.databinding.ItemHomeImageTextScrollCardBinding
import com.example.c001apk.databinding.ItemHomeTextCardBinding
import com.example.c001apk.databinding.ItemHomeUnsupportedBinding
import com.example.c001apk.databinding.ItemPearGoodsBinding
import com.example.c001apk.databinding.ItemProductConfigListBinding
import com.example.c001apk.databinding.ItemProductListCardBinding
import com.example.c001apk.databinding.ItemProductSelectRowBinding
import com.example.c001apk.databinding.ItemUserNodeRatingBinding
import com.example.c001apk.databinding.ItemRecentHistoryBinding
import com.example.c001apk.databinding.ItemSearchApkBinding
import com.example.c001apk.databinding.ItemSearchTopicBinding
import com.example.c001apk.databinding.ItemSearchUserBinding
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.Like
import com.example.c001apk.util.DateUtils
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.dp
import com.example.c001apk.view.LinearItemDecoration1
import com.google.android.material.color.MaterialColors

class AppAdapter(
    private val listener: ItemListener
) : BaseAdapter<ViewDataBinding>() {

    class FeedViewHolder(val binding: ItemHomeFeedBinding, val listener: ItemListener) :
        BaseViewHolder<ViewDataBinding>(binding) {
        var entityType: String = ""
        var id: String = ""
        var uid: String = ""
        var isStickTop: Boolean = false

        init {
            binding.expand.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    menuInflater.inflate(R.menu.feed_reply_menu, menu).apply {
                        menu.findItem(R.id.copy)?.isVisible = false
                        menu.findItem(R.id.delete)?.isVisible = PrefManager.uid == uid
                        menu.findItem(R.id.show)?.isVisible = false
                        menu.findItem(R.id.report)?.isVisible = PrefManager.isLogin
                        // 只有自己的动态才能改可见性
                        menu.findItem(R.id.publishStatus)?.isVisible =
                            PrefManager.uid == uid && entityType == "feed"
                        // 只有自己的动态才能置顶，标题按当前状态切换
                        menu.findItem(R.id.stickTop)?.apply {
                            isVisible = PrefManager.uid == uid && entityType == "feed"
                            title = it.context.getString(
                                if (isStickTop) R.string.unstick_top else R.string.stick_top
                            )
                        }
                    }
                    setOnMenuItemClickListener(
                        PopClickListener(
                            listener,
                            it.context,
                            entityType,
                            id,
                            uid,
                            bindingAdapterPosition,
                            isStickTop
                        )
                    )
                    show()
                }
            }
        }

        override fun bind(data: HomeFeedResponse.Data) {
            entityType = data.entityType
            id = data.id ?: ""
            uid = data.uid ?: ""
            isStickTop = data.isStickTop == 1

            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.setVariable(
                BR.likeData,
                Like(
                    data.likenum ?: "0",
                    data.userAction?.like ?: 0
                )
            )

            val lp = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(if (data.infoHtml.isNullOrEmpty()) 10.dp else 5.dp, 0, 0, 0)
            lp.topToBottom = binding.uname.id
            lp.startToEnd = binding.from.id
            // 设备串要停在「置顶 / 仅自己可见」两个小标记左侧（两个都隐藏时自动延伸到最右）
            lp.endToEnd = binding.topBadge.id
            binding.device.layoutParams = lp

            bindMessageMore(data)
        }

        /**
         * 正文被折叠成 [MESSAGE_COLLAPSED_LINES] 行时才露出「查看更多」（点了进详情页看全文）。
         *
         * 只能按排版结果判：同样的字数，窄屏 / 长链接都会让行数不同。而此刻
         * `binding.message` 的 layout 还是上一条数据的，所以判定挂到 `doOnNextLayout` 上，
         * 等这条绑定的文本排完版再算。回调有可能在 ViewHolder 被回收复用之后才跑到，
         * 所以先用 id 对一下当前绑的还是不是同一条数据，别把别人的按钮点亮。
         */
        private fun bindMessageMore(data: HomeFeedResponse.Data) {
            binding.messageMore.isVisible = false
            if (data.message.isNullOrEmpty()) return

            binding.messageMore.setOnClickListener { view ->
                // userInfo 在模型里是可空的（databinding 表达式里直接取字段不会报，
                // Kotlin 侧必须自己兜住）
                val userInfo = data.userInfo
                listener.onViewFeed(
                    view, data.id, userInfo?.uid, userInfo?.username,
                    userInfo?.userAvatar, data.deviceTitle, data.message,
                    data.dateline?.toString(), null, null, data
                )
            }

            val bindId = id
            binding.message.doOnNextLayout {
                if (id != bindId) return@doOnNextLayout
                val layout = binding.message.layout ?: return@doOnNextLayout
                val lastLine = layout.lineCount - 1
                val truncated = layout.lineCount > MESSAGE_COLLAPSED_LINES ||
                        (lastLine >= 0 && layout.getEllipsisCount(lastLine) > 0)
                binding.messageMore.isVisible = truncated
            }
        }

        companion object {
            /** 正文折叠行数，与 item_home_feed.xml 里 message 的 maxLines 保持一致 */
            private const val MESSAGE_COLLAPSED_LINES = 5
        }
    }

    class ImageCarouselCardViewHolder(
        val binding: ItemHomeImageCarouselCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            data.entities?.let {
                var currentPosition = 0
                binding.viewPager.adapter = ImageCarouselCardAdapter(listener).also { adapter ->
                    adapter.submitList(it)
                }
                binding.viewPager.registerOnPageChangeCallback(object :
                    ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        currentPosition = position
                    }

                    override fun onPageScrollStateChanged(state: Int) {
                        if (state == ViewPager2.SCROLL_STATE_IDLE) {
                            if (currentPosition == 0) {
                                binding.viewPager.setCurrentItem(it.size - 2, false)
                            } else if (currentPosition == it.size - 1) {
                                binding.viewPager.setCurrentItem(1, false)
                            }
                        }
                    }
                })
                binding.viewPager.setCurrentItem(1, false)
            }
        }
    }


    class IconLinkGridCardViewHolder(
        val binding: ItemHomeIconLinkGridCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            data.entities?.let {
                val dataList: MutableList<List<HomeFeedResponse.Entities>> = ArrayList()
                val page = it.size / 5
                for (index in 0..<page) {
                    dataList.add(it.subList(index * 5, (index + 1) * 5))
                }
                binding.viewPager.adapter = IconLinkGridCardAdapter(dataList, listener)
                if (page < 2) binding.indicator.isVisible = false
                else {
                    binding.indicator.isVisible = true
                    binding.indicator.setViewPager(binding.viewPager)
                }
            }
        }
    }

    class ImageTextScrollCardViewHolder(
        val binding: ItemHomeImageTextScrollCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            if (!data.entities.isNullOrEmpty()) {
                binding.title.text = data.title
                binding.title.setPadding(10.dp, 10.dp, 10.dp, 0)
                binding.recyclerView.apply {
                    adapter = ImageTextScrollCardAdapter(listener).also {
                        it.submitList(data.entities)
                    }
                    layoutManager = LinearLayoutManager(itemView.context).also {
                        it.orientation = LinearLayoutManager.HORIZONTAL
                    }
                    if (itemDecorationCount == 0)
                        addItemDecoration(LinearItemDecoration1(10.dp))
                }
            }
        }

    }

    class IconMiniScrollCardViewHolder(
        val binding: ItemHomeIconMiniScrollCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            if (!data.entities.isNullOrEmpty()) {
                binding.recyclerView.apply {
                    adapter = IconMiniScrollCardAdapter(listener).also {
                        it.submitList(data.entities)
                    }
                    layoutManager = LinearLayoutManager(itemView.context).also {
                        it.orientation = LinearLayoutManager.HORIZONTAL
                    }
                    if (itemDecorationCount == 0)
                        addItemDecoration(LinearItemDecoration1(10.dp))
                }
            }
        }
    }

    // 游戏频道（/v6/page/dataList?url=V15_YOUXI）顶部四个入口：资讯 / 喜加一 / SteamDB / 硬件
    class GameTabCardViewHolder(
        val binding: ItemHomeGameTabCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            data.entities?.let { entities ->
                binding.recyclerView.apply {
                    adapter = GameCardAdapter(listener, GameCardAdapter.Mode.TAB).also {
                        it.submitList(entities)
                    }
                    layoutManager = GridLayoutManager(itemView.context, 4)
                }
            }
        }
    }

    // 游戏频道里「标题 + 一组条目」的卡片。这些排版在接口里各是一个 entityTemplate
    // （热门新游 iconLongTitleGridCard、游戏评分 iconGridCard、发售日历 productTimelineListCard、
    // 游戏闲聊 imageScrollCard、热议手游 iconScrollCard、最新点评 feedListCard、世界频道 sortSelectCard），
    // 但数据都是 entities[]，所以按模板选一份排版交给 GameCardAdapter。
    class GameCardViewHolder(
        val binding: ItemHomeGameCardListBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        private var mode: GameCardAdapter.Mode? = null

        override fun bind(data: HomeFeedResponse.Data) {
            val entities = data.entities
            if (entities.isNullOrEmpty()) return

            val newMode = bindMode(data.entityTemplate)

            binding.title.text = data.title
            binding.title.isVisible = !data.title.isNullOrEmpty()
            // 右上角是接口下发的（「榜单」这类），点了跳卡片自己的 url
            binding.subTitle.text = data.subTitle.orEmpty()
            binding.subTitle.isVisible = !data.subTitle.isNullOrEmpty()
            binding.subTitle.setOnClickListener {
                if (!data.url.isNullOrEmpty()) listener.onOpenLink(it, data.url, data.title)
            }

            binding.recyclerView.apply {
                // 同一个 ViewHolder 会轮流承接不同模板的卡片，排版模式变了就得整套换掉
                layoutManager = when {
                    newMode == GameCardAdapter.Mode.GRID ->
                        GridLayoutManager(itemView.context, 4)

                    newMode.isHorizontal ->
                        LinearLayoutManager(itemView.context).also {
                            it.orientation = LinearLayoutManager.HORIZONTAL
                        }

                    else -> LinearLayoutManager(itemView.context)
                }
                if (mode == newMode) {
                    (adapter as GameCardAdapter).submitList(entities)
                } else {
                    if (newMode.isHorizontal && itemDecorationCount == 0)
                        addItemDecoration(LinearItemDecoration1(10.dp))
                    adapter = GameCardAdapter(listener, newMode).also { it.submitList(entities) }
                    mode = newMode
                }
            }
        }

        private fun bindMode(template: String?): GameCardAdapter.Mode = when (template) {
            "productTimelineListCard" -> GameCardAdapter.Mode.TIMELINE
            "imageScrollCard" -> GameCardAdapter.Mode.IMAGE
            "iconScrollCard" -> GameCardAdapter.Mode.ICON
            "feedListCard" -> GameCardAdapter.Mode.COMMENT
            "sortSelectCard" -> GameCardAdapter.Mode.SORT
            // iconLongTitleGridCard（热门新游）/ iconGridCard（游戏评分）
            else -> GameCardAdapter.Mode.GRID
        }
    }

    class RefreshCardViewHolder(val binding: ItemHomeFeedRefreshCardBinding) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.textView.text = data.title
        }
    }

    class ImageSquareScrollCardViewHolder(
        val binding: ItemHomeImageSquareScrollCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            data.entities?.let { entities ->
                binding.recyclerView.apply {
                    adapter = ImageSquareScrollCardAdapter(listener).also {
                        it.submitList(entities)
                    }
                    layoutManager = LinearLayoutManager(itemView.context).also {
                        it.orientation = LinearLayoutManager.HORIZONTAL
                    }
                    if (itemDecorationCount == 0)
                        addItemDecoration(LinearItemDecoration1(10.dp))
                }
            }
        }
    }

    class UserViewHolder(val binding: ItemSearchUserBinding, val listener: ItemListener) :
        BaseViewHolder<ViewDataBinding>(binding) {
        @SuppressLint("SetTextI18n")
        override fun bind(data: HomeFeedResponse.Data) {

            binding.setVariable(BR.listener, listener)
            // 角标走布局里的 app:verifyUserData="@{data}"，不把 data 塞进 binding 就不会触发
            binding.setVariable(BR.data, data)

            if (data.userInfo != null && data.fUserInfo != null) {
                binding.uid = data.userInfo.uid
                binding.uname.text = data.userInfo.username
                binding.follow.text = "${data.userInfo.follow}关注"
                binding.fans.text = "${data.userInfo.fans}粉丝"
                binding.act.text = DateUtils.fromToday(data.userInfo.logintime) + "活跃"
                ImageUtil.showIMG(binding.avatar, data.userInfo.userAvatar)
            } else if (data.userInfo == null && data.fUserInfo != null) {
                binding.uid = data.fUserInfo.uid
                binding.uname.text = data.fUserInfo.username
                binding.follow.text = "${data.fUserInfo.follow}关注"
                binding.fans.text = "${data.fUserInfo.fans}粉丝"
                binding.act.text = DateUtils.fromToday(data.fUserInfo.logintime) + "活跃"
                ImageUtil.showIMG(binding.avatar, data.fUserInfo.userAvatar)
            } else if (data.userInfo != null) {
                binding.uid = data.uid
                binding.uname.text = data.username
                binding.follow.text = "${data.follow}关注"
                binding.fans.text = "${data.fans}粉丝"
                binding.act.text = DateUtils.fromToday(data.logintime ?: 0L) + "活跃"
                binding.isFollow = data.isFollow ?: 0
                if (data.isFollow == 1) {
                    binding.followBtn.text = "已关注"
                    binding.followBtn.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                } else {
                    binding.followBtn.text = "关注"
                    binding.followBtn.setTextColor(
                        MaterialColors.getColor(
                            itemView.context,
                            androidx.appcompat.R.attr.colorPrimary,
                            0
                        )
                    )
                }
                binding.followBtn.isVisible = PrefManager.isLogin
                ImageUtil.showIMG(binding.avatar, data.userAvatar)
            }

        }
    }

    class TopicProductViewHolder(val binding: ItemSearchTopicBinding, val listener: ItemListener) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            if (data.description == "home") {
                binding.parent.setCardBackgroundColor(
                    MaterialColors.getColor(
                        itemView.context,
                        android.R.attr.windowBackground,
                        0
                    )
                )
            } else {
                binding.parent.setCardBackgroundColor(
                    itemView.context.getColor(R.color.home_card_background_color)
                )
            }
            binding.commentNum.text =
                if (data.entityType == "topic")
                    "${data.commentnumTxt}讨论"
                else
                    "${data.feedCommentNumTxt}讨论"

            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
        }
    }

    class AppViewHolder(val binding: ItemSearchApkBinding, val listener: ItemListener) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
        }
    }

    class CollectionViewHolder(
        val binding: ItemCollectionListItemBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
        }
    }

    class RecentHistoryViewHolder(
        val binding: ItemRecentHistoryBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.fans.text =
                if (data.targetType == "user")
                    "${data.fansNum}粉丝"
                else
                    "${data.commentNum}讨论"
        }
    }

    class FeedReplyViewHolder(val binding: ItemFeedReplyBinding, val listener: ItemListener) :
        BaseViewHolder<ViewDataBinding>(binding) {
        var entityType: String = ""
        var id: String = ""
        var uid: String = ""

        init {
            binding.expand.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    menuInflater.inflate(R.menu.feed_reply_menu, menu).apply {
                        menu.findItem(R.id.copy)?.isVisible = false
                        menu.findItem(R.id.delete)?.isVisible = PrefManager.uid == uid
                        menu.findItem(R.id.show)?.isVisible = false
                        menu.findItem(R.id.report)?.isVisible = PrefManager.isLogin
                    }
                    setOnMenuItemClickListener(
                        PopClickListener(
                            listener,
                            it.context,
                            entityType,
                            id,
                            uid,
                            bindingAdapterPosition
                        )
                    )
                    show()
                }
            }
        }

        override fun bind(data: HomeFeedResponse.Data) {
            entityType = data.entityType
            id = data.id ?: ""
            uid = data.uid ?: ""

            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.setVariable(
                BR.likeData,
                Like(
                    data.likenum ?: "0",
                    data.userAction?.like ?: 0
                )
            )
        }
    }

    // configCard（页面配置）/ sponsorCard（广告位）这类不该出现在列表里的卡片，占 0 高度
    class HiddenViewHolder(val binding: ItemHomeHiddenBinding) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
        }
    }

    /**
     * 未支持的卡片模板：不抛异常（会让整页空白/崩溃），也不只丢一行提示——把各模板都有的
     * 通用字段排成一张卡片，能点就点，最后一行小字标明模板名。
     *
     * 服务端的卡片是配置驱动的，新模板会随时冒出来（`_rev/card_tpl_scan.py` 实测服务端会下发
     * 39 种顶层模板，本地只实现了一部分）。所以这里要"接得住"：认不出的模板至少让用户看到
     * 卡片里有什么、能顺着 url 进去，而不是只剩一行灰字。
     *
     * 通用字段的取值优先级：正文 message → description（活动/话题卡片常用后者），
     * 标题 title；都没有就退回原来那行「不支持的卡片：xxx」。
     */
    class UnsupportedViewHolder(
        val binding: ItemHomeUnsupportedBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.title.isVisible = !data.title.isNullOrEmpty()
            binding.title.text = data.title

            val summary = data.message?.takeIf { it.isNotBlank() } ?: data.description
            binding.summary.isVisible = !summary.isNullOrEmpty()
            binding.summary.text = summary

            binding.tip.text = binding.root.context.getString(
                R.string.unsupported_card,
                data.entityTemplate ?: data.entityType.orEmpty()
            )

            // 有 url 才可点（点了走既有的链接分发，能进详情/H5 的都会进）；
            // 没有 url 的（纯展示卡片）别给出会亮却没反应的按压反馈
            val url = data.url
            val clickable = !url.isNullOrEmpty()
            binding.root.isClickable = clickable
            binding.root.isFocusable = clickable
            if (clickable) {
                binding.root.setOnClickListener { view -> listener.onOpenLink(view, url, data.title) }
            } else {
                binding.root.setOnClickListener(null)
            }
        }
    }

    // 产品页「参数」tab：版本配置（各存储版本价位，点「参数」进官方 H5 规格页）
    class ProductConfigListViewHolder(
        val binding: ItemProductConfigListBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {

        @SuppressLint("SetTextI18n")
        override fun bind(data: HomeFeedResponse.Data) {
            binding.title.text = data.title
            binding.subTitle.isVisible = !data.subTitle.isNullOrEmpty()
            binding.subTitle.text = data.subTitle ?: ""
            // 卡片自身 url 是「配置对比」H5
            binding.subTitle.setOnClickListener {
                listener.onOpenLink(it, data.url, data.title)
            }

            binding.rowsLayout.removeAllViews()
            val context = binding.root.context
            data.configRows?.forEach { row ->
                val rowLayout = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6.dp, 0, 6.dp)
                }
                val title = android.widget.TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    text = row.title
                }
                val price = android.widget.TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    textSize = 14f
                    setTextColor(
                        MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
                    )
                    text = row.price?.let { "¥$it" }.orEmpty()
                }
                val params = android.widget.TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = 12.dp }
                    setBackgroundResource(R.drawable.shape_outline_btn)
                    setPadding(12.dp, 3.dp, 12.dp, 3.dp)
                    textSize = 12f
                    text = "参数"
                    setOnClickListener {
                        listener.onOpenLink(it, row.url, row.title)
                    }
                }
                rowLayout.addView(title)
                rowLayout.addView(price)
                rowLayout.addView(params)
                binding.rowsLayout.addView(rowLayout)
            }
        }
    }

    // 产品页「参数」tab：同价位 / 同SoC / 同系列机型（横向列表）
    class ProductListCardViewHolder(
        val binding: ItemProductListCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.title.text = data.title
            // 卡片 url 是「同价位对比」之类的机型列表页
            binding.header.setOnClickListener {
                listener.onOpenLink(it, data.url, data.title)
            }
            binding.recyclerView.apply {
                isNestedScrollingEnabled = false
                layoutManager = LinearLayoutManager(context).also {
                    it.orientation = LinearLayoutManager.HORIZONTAL
                }
                adapter = ProductSelectAdapter(listener).also {
                    it.submitList(data.entities.orEmpty())
                }
                if (itemDecorationCount == 0)
                    addItemDecoration(LinearItemDecoration1(5.dp))
            }
        }
    }

    // 活动/众测图文卡片
    class ImageTextGridCardViewHolder(
        val binding: ItemHomeImageTextGridCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            // 众测卡片的数据在 entities[0] 里（title/url/pic），card 级 title/url/logo 均为空，
            // 若不回填则标题不显示、点击也拿不到跳转 url（表现为「众测打不开」）。
            val display = data.entities?.firstOrNull()?.let { e ->
                data.copy(
                    title = e.title,
                    url = e.url,
                    pic = e.pic,
                    logo = e.pic,
                )
            } ?: data
            binding.setVariable(BR.data, display)
            binding.setVariable(BR.listener, listener)
        }
    }

    // 用户主页「点评」tab 的 nodeRating 实体
    class NodeRatingViewHolder(
        val binding: ItemUserNodeRatingBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
        }
    }

    // 活动页「线下」tab 的 pear_goods 实体（无 entityTemplate，仅有 goods_* 字段）
    class PearGoodsViewHolder(
        val binding: ItemPearGoodsBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            val display = if (data.goodsBuyText.isNullOrEmpty())
                data.copy(goodsBuyText = binding.root.context.getString(R.string.view_detail))
            else data
            binding.setVariable(BR.data, display)
            binding.setVariable(BR.listener, listener)
        }
    }

    // 专题页「说明」等纯文本卡片（entityTemplate=textCard，title + description）
    class TextCardViewHolder(
        val binding: ItemHomeTextCardBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
        }
    }

    // 产品列表页（同价位/同SoC/同系列对比）的条目：product/productSelect
    class ProductSelectRowViewHolder(
        val binding: ItemProductSelectRowBinding,
        val listener: ItemListener
    ) :
        BaseViewHolder<ViewDataBinding>(binding) {
        override fun bind(data: HomeFeedResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.score.text = listOfNotNull(
                data.starAverageScore?.let { "${it}分" },
                data.starTotalCount?.let { "${it}人评分" }
            ).joinToString(" · ")
            binding.ratingSpecs.text = data.productRatingSpecs
                ?.entries?.joinToString(" ") { "${it.key}${it.value}" }
                .orEmpty()
            binding.specs.text = data.productSpecs?.joinToString(" | ").orEmpty()
            binding.price.text = listOfNotNull(
                data.priceMin?.let { "¥$it" },
                data.configName
            ).joinToString("\n")
        }
    }

    // 版本历史条目（/v6/apk/downloadVersionList）：接口只下发 versionName / versionSize / versionDate，
    // versionName 缺失时退回同一接口里的 version 字段
    class AppVersionViewHolder(
        val binding: ItemAppVersionBinding,
        private val listener: ItemListener,
    ) : BaseViewHolder<ViewDataBinding>(binding) {

        private var packageName: String? = null
        private var versionCode: Long? = null
        private var versionName: String? = null
        private var versionSize: String? = null

        init {
            binding.root.setOnClickListener {
                listener.onDownloadVersion(
                    it,
                    packageName,
                    versionCode,
                    versionName,
                    versionSize
                )
            }
        }

        override fun bind(data: HomeFeedResponse.Data) {
            versionName = data.versionName ?: data.version.orEmpty()
            versionCode = data.versionCode
            packageName = data.packageName
            versionSize = data.versionSize
            binding.versionName.text = versionName
            binding.versionInfo.text = listOfNotNull(
                data.versionSize,
                data.versionDate
            ).joinToString(" · ")
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding> {
        return when (viewType) {

            0 -> {
                ImageCarouselCardViewHolder(
                    ItemHomeImageCarouselCardBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent, false
                    ), listener
                )
            }

            1 -> {
                IconLinkGridCardViewHolder(
                    ItemHomeIconLinkGridCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            2 -> {
                FeedViewHolder(
                    ItemHomeFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    listener
                )
            }

            3 -> {
                ImageTextScrollCardViewHolder(
                    ItemHomeImageTextScrollCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            4 -> {
                IconMiniScrollCardViewHolder(
                    ItemHomeIconMiniScrollCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            5 -> {
                RefreshCardViewHolder(
                    ItemHomeFeedRefreshCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    )
                )
            }

            6 -> {
                UserViewHolder(
                    ItemSearchUserBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                    listener
                )
            }

            7 -> {
                TopicProductViewHolder(
                    ItemSearchTopicBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                    listener
                )
            }

            8 -> {
                AppViewHolder(
                    ItemSearchApkBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                    listener
                )
            }

            10 -> {
                FeedReplyViewHolder(
                    ItemFeedReplyBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                    listener
                )
            }

            11 -> {
                CollectionViewHolder(
                    ItemCollectionListItemBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    ), listener
                )
            }

            12 -> {
                RecentHistoryViewHolder(
                    ItemRecentHistoryBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ), listener
                )
            }

            13 -> {
                ImageSquareScrollCardViewHolder(
                    ItemHomeImageSquareScrollCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            15 -> {
                ProductConfigListViewHolder(
                    ItemProductConfigListBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            16 -> {
                ProductListCardViewHolder(
                    ItemProductListCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            17 -> {
                ImageTextGridCardViewHolder(
                    ItemHomeImageTextGridCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            18 -> {
                ProductSelectRowViewHolder(
                    ItemProductSelectRowBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            19 -> {
                PearGoodsViewHolder(
                    ItemPearGoodsBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            20 -> {
                TextCardViewHolder(
                    ItemHomeTextCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            21 -> {
                NodeRatingViewHolder(
                    ItemUserNodeRatingBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            22 -> {
                AppVersionViewHolder(
                    ItemAppVersionBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            23 -> {
                GameTabCardViewHolder(
                    ItemHomeGameTabCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            24 -> {
                GameCardViewHolder(
                    ItemHomeGameCardListBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }

            25 -> {
                HiddenViewHolder(
                    ItemHomeHiddenBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    )
                )
            }

            else -> {
                UnsupportedViewHolder(
                    ItemHomeUnsupportedBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), listener
                )
            }
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<ViewDataBinding>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            if (payloads[0] == true) {
                when (holder) {
                    is FeedViewHolder -> {
                        holder.binding.setVariable(
                            BR.likeData,
                            Like(
                                currentList[position].likenum ?: "0",
                                currentList[position].userAction?.like ?: 0
                            )
                        )
                        holder.binding.executePendingBindings()
                    }

                    is FeedReplyViewHolder -> {
                        holder.binding.setVariable(
                            BR.likeData,
                            Like(
                                currentList[position].likenum ?: "0",
                                currentList[position].userAction?.like ?: 0
                            )
                        )
                        holder.binding.executePendingBindings()
                    }

                    is UserViewHolder -> {
                        holder.binding.isFollow = currentList[position].isFollow ?: 0
                        if (currentList[position].isFollow == 1) {
                            holder.binding.followBtn.text = "已关注"
                            holder.binding.followBtn.setTextColor(
                                holder.itemView.context.getColor(
                                    android.R.color.darker_gray
                                )
                            )
                        } else {
                            holder.binding.followBtn.text = "关注"
                            holder.binding.followBtn.setTextColor(
                                MaterialColors.getColor(
                                    holder.itemView.context,
                                    androidx.appcompat.R.attr.colorPrimary,
                                    0
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = currentList[position]
        // 版本历史（/v6/apk/downloadVersionList）的条目没有 entityType / entityId，只有 version* 字段。
        // entityType 虽然声明为非空 String，但 Gson 遇到缺失字段会塞 null，
        // 直接交给下面的 when 取 hashCode 会 NPE，所以先单独认出来（顺便兜住其它 null 情况）
        if (item.entityType.isNullOrEmpty() && item.versionId != null) return 22
        return when (item.entityType.orEmpty()) {
            "card" -> {
                when (currentList[position].entityTemplate) {
                    "imageCarouselCard_1" -> 0
                    "iconLinkGridCard" -> 1
                    "imageTextScrollCard" -> 3

                    "iconMiniScrollCard" -> 4
                    "iconMiniGridCard" -> 4

                    "refreshCard" -> 5

                    "imageSquareScrollCard" -> 13

                    // 产品页「参数」tab：版本配置
                    "productConfigList" -> 15

                    // 产品页「参数」tab：同价位 / 同SoC / 同系列（实体是 product 才走原生模板）
                    "listCard" ->
                        if (currentList[position].entities?.firstOrNull()?.entityType == "product")
                            16
                        else 14

                    // 活动/众测图文卡片
                    "imageTextGridCard" -> 17

                    // 专题页「说明」等纯文本卡片
                    "textCard" -> 20

                    // 游戏频道（/v6/page/dataList?url=V15_YOUXI）的卡片
                    "iconMiniLinkGridCard" -> 23
                    "iconLongTitleGridCard",
                    "iconGridCard",
                    "productTimelineListCard",
                    "imageScrollCard",
                    "iconScrollCard",
                    "feedListCard",
                    "sortSelectCard" -> 24

                    // 页面配置（configCard）/ 广告位（sponsorCard）：不渲染
                    "configCard", "sponsorCard" -> 25

                    // 未支持的卡片模板交给占位 ViewHolder，避免整页空白/崩溃
                    else -> 14
                }
            }

            "feed" -> when (currentList[position].feedType) {
                "vote" -> 2//9
                else -> 2
            }

            "contacts" -> 6
            "user" -> 6

            "topic" -> 7
            "product" ->
                // 产品列表页（同价位等对比页）的条目是 productSelect 模板
                if (currentList[position].entityTemplate == "productSelect")
                    18
                else 7

            "apk" -> 8

            "feed_reply" -> 10

            "collection" -> 11

            "recentHistory" -> 12

            // 活动页「线下」tab 的 pear_goods 实体（无 entityTemplate）
            "pear_goods" -> 19

            // 用户主页「点评」tab 的评分实体
            "nodeRating" -> 21

            // 未支持的实体类型同样兜底到占位，避免整页空白/崩溃
            else -> 14
        }
    }

}