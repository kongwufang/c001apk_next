package com.example.c001apk.ui.messagedetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.BuildConfig
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityMessageDetailBinding
import com.example.c001apk.logic.model.OSSUploadPrepareModel
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.feed.reply.emoji.EmojiPagerAdapter
import com.example.c001apk.util.EmojiUtils
import com.example.c001apk.util.ImageUtil.getImageDimensionsAndMD5
import com.example.c001apk.util.ImageUtil.toHex
import com.example.c001apk.util.MessageKit
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.dp
import com.example.c001apk.util.ossUpload
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 私信聊天页（独立二级页，保证右滑返回能跟手预览）。
 *
 * 两个入口：
 * - 会话列表点击：带 ukey + uid + uname；
 * - 别人主页的「私信」圆钮：只有 uid，**从没聊过也能进来**，
 *   发出第一条消息时由服务端隐式建会话。
 */
@AndroidEntryPoint
class MessageDetailActivity : BaseActivity<ActivityMessageDetailBinding>() {

    private val viewModel by viewModels<MessageDetailViewModel>()
    private val adapter by lazy {
        MessageDetailAdapter(
            myUid = PrefManager.uid,
            myAvatar = PrefManager.userAvatar,
            partnerAvatar = intent.getStringExtra("avatar").orEmpty(),
            partnerUid = intent.getStringExtra("uid").orEmpty(),
            // 图片消息的地址要现问 showImage 换，只能在 ViewModel 里做（那边有作用域 + 缓存）
            loadPic = { id, onReady -> viewModel.loadMessagePic(id, onReady) },
            // 发送失败时点红色感叹号 ⇒ 问是否重发（2026-10-30 乐观发送）
            onRetry = { item -> askResend(item) },
            // 图片气泡尺寸定下来后回调（见下方 stickToBottom 说明）
            onPicSized = { if (stickToBottom) scrollToBottom() }
        )
    }

    /**
     * 是否"黏"在底部。
     *
     * 为什么需要：进页面时图片还在异步换签名地址 + 加载 ⇒ 气泡先是小占位 ✗
     * 图片回来后气泡被撑到真实比例 ⇒ 上方内容整体变高 ⇒ 列表就不在底部了 ✗
     * （用户 2026-10-30 报的就是这个）
     *
     * 策略：默认 true ✓
     * · 用户手动往上滑 ⇒ 置 false（别抢人家的滚动 ✗）
     * · 用户滑回底部 ⇒ 置 true
     * · 只有 true 时，图片尺寸变化后才再贴一次底 ✓
     */
    private var stickToBottom = true

    /** 只读会话（酷安小秘书）：能看登录提醒 / 站内信，但不给输入框 */
    private var readOnly = false

    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>

    /** 待上传的图片。私信一次只发一张，选完立刻进上传流程，所以只留一份 */
    private var picUri: Uri? = null
    private var picType = ""
    private var picMd5: ByteArray? = null

    private val imm by lazy {
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    /** 表情面板三页（最近 / 默认 / 酷币），页码顺序跟回复页一致，数据表也是同一张 */
    private val emojiData by lazy { EmojiUtils.emojiMap.toList() }
    private val recentEmojiList = ArrayList<List<Pair<String, Int>>>()
    private val emojiList = ArrayList<List<Pair<String, Int>>>()
    private val coolBList = ArrayList<List<Pair<String, Int>>>()
    private val emojiPages = listOf(recentEmojiList, emojiList, coolBList)

    init {
        for (i in 0..3) {
            emojiList.add(emojiData.subList(i * 27 + 4, (i + 1) * 27 + 4))
        }
        coolBList.add(emojiData.subList(112, 139))
        coolBList.add(emojiData.subList(139, 155))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.ukey = intent.getStringExtra("ukey").orEmpty()
        viewModel.uid = intent.getStringExtra("uid").orEmpty()

        initPhotoPick()
        initView()
        initObserve()

        viewModel.loadHistory()
        viewModel.markRead()
    }

    /**
     * 页面可见 ⇒ 开始轮询收新消息（`/v6/message/receive`）。
     *
     * 用 onStart/onStop 而不是 onCreate/onDestroy：
     * 切到后台就停 ✓ 回来立刻继续 ✓ 不会在后台白耗流量与电量 ✓
     * （跟 MessageCenterActivity 用 onStop 的思路一致）
     */
    override fun onStart() {
        super.onStart()
        viewModel.startPolling()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopPolling()
    }

    /**
     * 选图。私信一次只发一张，所以用单选的 [ActivityResultContracts.PickVisualMedia]
     * （系统相册，不用申请读存储权限），跟回复页用同一套。
     */
    private fun initPhotoPick() {
        pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPickImage(uri)
        }
    }

    private fun initView() {
        // 标题直接在代码里给，跟项目里其它 Toolbar 页保持一致（别走 app:title 的 databinding）
        binding.toolBar.title = intent.getStringExtra("uname").orEmpty()
        binding.toolBar.setNavigationOnClickListener { finish() }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // 往上滑 ⇒ 别再强行黏底了 ✓
                if (dy < 0) stickToBottom = false
                // 已经贴到最底 ⇒ 恢复黏底 ✓
                if (!rv.canScrollVertically(1)) stickToBottom = true
                // 滑到**最顶上**且还能往上 ⇒ 翻更早的一页 ✓
                if (dy < 0 && !rv.canScrollVertically(-1)) {
                    viewModel.loadMore()
                }
            }
        })

        binding.recyclerView.apply {
            // ⚠️ 必须 stackFromEnd = true（2026-10-30 用户要求「进页面自动滑到最底下」）
            // 普通 LinearLayoutManager 是从**顶部**开始布局的 ✗ 聊天记录一多，
            // 首次进来会停在最早那条 ✗ 而 submitList 之后那句 scrollToPosition 又可能
            // 赶在布局完成前跑掉（diff 是异步的）⇒ 于是停在顶上 ✗
            // stackFromEnd 让列表**从底部开始**布局 ✓ 内容不满屏时也贴着输入框 ✓
            // 新消息进来时自然就在最下面，不用额外滚 ✓
            layoutManager = LinearLayoutManager(this@MessageDetailActivity).apply {
                stackFromEnd = true
            }
            adapter = this@MessageDetailActivity.adapter
        }

        binding.sendBtn.setOnClickListener { send() }
        binding.imageBtn.setOnClickListener { pickImage() }
        binding.emojiBtn.setOnClickListener { toggleEmojiPanel() }
        // 点输入框要收掉表情面板、把键盘让上来。这里不能用 OnClickListener：
        // 那会把点击整个吞掉，光标定位 / 长按选词全废。OnTouchListener 看一眼就放行。
        binding.editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && binding.emojiLayout.isVisible) {
                hideEmojiPanel()
                showKeyboard()
            }
            false
        }
        initEmojiPanel()
        applyReadOnly()
        applyInputBarInsets()
    }

    private fun pickImage() {
        if (readOnly) return
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.anim_bottom_sheet_slide_up, R.anim.anim_bottom_sheet_slide_down
        )
        try {
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                options
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有可用的相册应用", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 选完图先算尺寸和 md5，交给 [MessageDetailViewModel.prepareImageUpload] 换 OSS 上传凭证；
     * 真正的直传在 [initObserve] 里 uploadImage 的回调中做（跟回复页同一套 OSS 流程）。
     */
    private fun onPickImage(uri: Uri) {
        val result = getImageDimensionsAndMD5(contentResolver, uri)
        val width = result.first?.first ?: 0
        val height = result.first?.second ?: 0
        val type = result.first?.third ?: ""
        if (width == 0 || height == 0) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
            return
        }
        picUri = uri
        picType = type
        picMd5 = result.second
        viewModel.prepareImageUpload(
            OSSUploadPrepareModel(
                name = "${UUID.randomUUID().toString().replace("-", "")}." +
                        if (type.startsWith("image/")) type.substring(6) else type,
                // 服务端拿这个拼对象名（`...@宽x高.png`），必须是原图真实尺寸
                resolution = "${width}x${height}",
                md5 = result.second?.toHex() ?: "",
            )
        )
    }

    /**
     * 会话列表进来会带 readOnly；主页那类只有 uid 的入口则按对方昵称兜底判断，
     * 保证不管从哪条路进小秘书的会话，都发不出消息。
     */
    private fun applyReadOnly() {
        readOnly = intent.getBooleanExtra("readOnly", false) ||
                MessageKit.isSecretaryName(intent.getStringExtra("uname"))
        if (!readOnly) return
        binding.editText.isVisible = false
        binding.imageBtn.isVisible = false
        binding.sendBtn.isVisible = false
        binding.emojiBtn.isVisible = false
        hideEmojiPanel()
        binding.readOnlyTip.isVisible = true
    }

    /**
     * 输入栏底部内边距：键盘弹起时抬到键盘上方，平时让开导航栏。
     * 取 max 是为了兼容「系统真的 resize 了窗口」和「只发 ime insets」两种情况，避免双重留白。
     *
     * 表情面板开着的时候不一样：面板底边就贴着屏幕底，导航栏那段内边距归它（见下面），
     * 输入栏只留 6dp，不然两者之间会空出一条导航栏高的缝。
     */
    private fun applyInputBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.inputBar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (binding.emojiLayout.isVisible) {
                v.updatePadding(bottom = 6.dp)
                binding.emojiLayout.updatePadding(bottom = bars.bottom)
            } else {
                v.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) + 6.dp)
            }
            insets
        }
    }

    private fun initObserve() {
        viewModel.chatData.observe(this) { list ->
            adapter.submitList(list) {
                // diff 完之后再滚，否则位置还对不上旧列表
                binding.recyclerView.post {
                    if (list.isNotEmpty())
                        binding.recyclerView.scrollToPosition(list.size - 1)
                }
            }
        }

        viewModel.toastText.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        // 上传凭证就绪 → 直传 OSS → 传完把服务端分配的对象名当 message_pic 发出去
        viewModel.uploadImage.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { prepared ->
                val uri = picUri ?: return@let
                lifecycleScope.launch(Dispatchers.IO) {
                    ossUpload(
                        this@MessageDetailActivity, prepared, listOf(uri),
                        listOf(picType), listOf(picMd5),
                        iOnSuccess = {
                            // 第二个参数是 **OSS 对象名**（接口要的），第三个是**本地 uri**
                            // （乐观发送要立刻显示缩略图）—— 两者不能混 ✓
                            viewModel.sendImageMessage(
                                prepared.fileInfo.firstOrNull()?.uploadFileName.orEmpty(),
                                uri.toString()
                            )
                        },
                        iOnFailure = {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MessageDetailActivity, "图片上传失败", Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        // 聊天页没有上传进度弹窗，不用关
                        closeDialog = { }
                    )
                }
            }
        }

        // 「最近」用过的表情（跟回复页共用一张表）；第一次进来是空的就自动落到「默认」页
        viewModel.recentEmojiLiveData.observe(this) { list ->
            if (binding.emojiPanel.currentItem == 0 && recentEmojiList.isNotEmpty()) return@observe
            recentEmojiList.clear()
            if (list.isEmpty()) {
                if (viewModel.isEmojiInit) {
                    viewModel.isEmojiInit = false
                    binding.emojiPanel.setCurrentItem(1, false)
                }
                recentEmojiList.add(0, emptyList())
            } else {
                recentEmojiList.add(
                    0,
                    list.map {
                        Pair(it.data, EmojiUtils.emojiMap[it.data] ?: R.drawable.ic_logo)
                    }
                )
            }
            binding.emojiPanel.adapter?.notifyItemChanged(0)
        }
    }

    /** 滚到最底部（最后一条）*/
    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) binding.recyclerView.scrollToPosition(count - 1)
    }

    private fun send() {
        if (readOnly) return
        val text = binding.editText.text?.toString().orEmpty()
        if (text.isBlank()) return
        // 立刻清空输入框：消息已经乐观上屏（转圈），不用等网络回来 ✓
        binding.editText.setText("")
        viewModel.sendMessage(text)
    }

    /**
     * 发送失败后点红色感叹号 ⇒ 问是否重发。
     *
     * · 重发：走 [MessageDetailViewModel.retrySend]，内容不丢 ✓
     * · 删除：只从本地列表移掉（服务端本来没收到 ✓），走 [MessageDetailViewModel.discardFailed]
     */
    private fun askResend(item: ChatItem) {
        if (isFinishing || isDestroyed) return
        val preview = when {
            item.data.message.isNotBlank() -> "「" + item.data.message.take(24) + "…」"
            MessageKit.hasPic(item.data) -> "[图片]"
            else -> "这条消息"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("发送失败")
            .setMessage(preview + " 没有发送成功，是否重新发送？")
            .setPositiveButton("重新发送") { _, _ -> viewModel.retrySend(item) }
            .setNegativeButton("删除") { _, _ -> viewModel.discardFailed(item) }
            .setNeutralButton("取消", null)
            .show()
    }

    // ---------------- 表情面板（跟回复页同一套，只是键盘/面板切换是手动接的） ----------------

    /**
     * 表情键：面板开着就收起来换键盘，没开就抬起面板。
     * 键盘和面板不同时出现，是两个输入源互相顶掉的经典做法（跟微信一致）。
     */
    private fun toggleEmojiPanel() {
        if (binding.emojiLayout.isVisible) {
            hideEmojiPanel()
            showKeyboard()
        } else {
            binding.emojiLayout.isVisible = true
            binding.emojiBtn.setIconResource(R.drawable.ic_keyboard)
            hideKeyboard()
            // 面板占了底部，输入栏的内边距要跟着换一套算法
            ViewCompat.requestApplyInsets(binding.inputBar)
        }
    }

    private fun hideEmojiPanel() {
        if (!binding.emojiLayout.isVisible) return
        binding.emojiLayout.isVisible = false
        binding.emojiBtn.setIconResource(R.drawable.ic_emoji)
        ViewCompat.requestApplyInsets(binding.inputBar)
    }

    private fun showKeyboard() {
        binding.editText.requestFocus()
        imm.showSoftInput(binding.editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        imm.hideSoftInputFromWindow(binding.editText.windowToken, 0)
    }

    /**
     * 三页页签 + ViewPager2（代码建 View，跟回复页逐行对齐，改哪边都别忘另一边）。
     * 区别只有一个：这里的长按清空走 [MessageDetailViewModel.deleteAllEmoji]。
     */
    private fun initEmojiPanel() {
        for (i in 0..2) {
            binding.indicator.addView(
                TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        weight = 1f
                    }
                    gravity = Gravity.CENTER
                    text = listOf("最近", "默认", "酷币")[i]
                    background = getDrawable(R.drawable.selector_bg_trans)
                    setOnClickListener {
                        binding.emojiPanel.setCurrentItem(i, false)
                    }
                    if (i == 0 && BuildConfig.DEBUG) {
                        setOnLongClickListener {
                            viewModel.deleteAllEmoji()
                            true
                        }
                    }
                }
            )
            if (i != 2) {
                binding.indicator.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            1.dp,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(
                            MaterialColors.getColor(
                                this@MessageDetailActivity,
                                com.google.android.material.R.attr.colorSurfaceVariant, 0
                            )
                        )
                    }
                )
            }
        }
        binding.emojiPanel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                for (i in 0 until binding.indicator.childCount) {
                    with(binding.indicator.getChildAt(i)) {
                        if (this is TextView) {
                            background = getDrawable(
                                if (i / 2 == position) R.drawable.selector_emoji_indicator_selected
                                else R.drawable.selector_emoji_indicator
                            )
                            setTextColor(
                                if (i / 2 == position)
                                    MaterialColors.getColor(
                                        this@MessageDetailActivity,
                                        com.google.android.material.R.attr.colorOnPrimary, 0
                                    )
                                else
                                    MaterialColors.getColor(
                                        this@MessageDetailActivity,
                                        androidx.appcompat.R.attr.colorControlNormal, 0
                                    )
                            )
                        }
                    }
                }
            }
        })

        binding.emojiPanel.adapter = EmojiPagerAdapter(
            emojiPages,
            onClickEmoji = { emoji ->
                with(binding.editText) {
                    if (emoji == "[c001apk]") {
                        onBackSpace()
                    } else {
                        // 面板刚打开时光标可能还没落下来（selectionStart 会是 -1），兜一下
                        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
                        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
                        editableText.replace(start, end, emoji)
                        viewModel.updateRecentEmoji(emoji)
                    }
                }
            },
            onCountStart = {
                countDownTimer.start()
            },
            onCountStop = {
                countDownTimer.cancel()
            }
        )
    }

    private val countDownTimer: CountDownTimer = object : CountDownTimer(100000, 50) {
        override fun onTick(millisUntilFinished: Long) {
            onBackSpace()
        }

        override fun onFinish() {}
    }

    private fun onBackSpace() {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ViewCompat.performHapticFeedback(binding.editText, HapticFeedbackConstantsCompat.CONFIRM)
    }

}
