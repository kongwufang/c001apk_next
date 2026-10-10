package com.example.c001apk.ui.article

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.c001apk.BuildConfig
import com.example.c001apk.MyApplication
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityArticlePublishBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.feed.reply.attopic.AtTopicActivity
import com.example.c001apk.ui.feed.reply.emoji.EmojiPagerAdapter
import com.example.c001apk.util.EmojiUtils
import com.example.c001apk.util.ImageUtil.getImageDimensionsAndMD5
import com.example.c001apk.util.ImageUtil.toHex
import com.example.c001apk.util.TransitionAnim
import com.example.c001apk.util.dp
import com.example.c001apk.util.makeToast
import com.example.c001apk.util.ossUpload
import com.example.c001apk.view.EmojiTextWatcher
import com.example.c001apk.view.FastDeleteAtUserKeyListener
import com.example.c001apk.view.OnTextInputListener
import com.example.c001apk.view.SmoothInputLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 发布图文（酷安「图文」= type feed + is_html_article=1）。
 * 结构：标题 + 封面（固定 1600:719 裁剪）+ 正文 + 查看权限。
 *
 * 正文是一个整块的输入框：图片以缩略图的形式**插在光标处**，跟着文字走，
 * 编辑体验和写一条带图的动态一样。官方要的是「text / image 块交替」的 message
 * 数组，这个转换放在发布时做（见 [buildMsgList]），输入过程中不维护块结构。
 *
 * 单张图的说明（image 块的 description）不在输入框里显示，靠**点击缩略图**弹窗填写，
 * 存在 span 上，发布时一起写进 message（见 [editDescription]）。
 */
@AndroidEntryPoint
class ArticlePublishActivity : BaseActivity<ActivityArticlePublishBinding>(),
    View.OnClickListener, SmoothInputLayout.OnVisibilityChangeListener {

    private val viewModel: ArticlePublishViewModel by viewModels()

    /** 表情面板数据：最近 / 默认 / 酷币 三页，切法与发表动态页一样 */
    private val dataList by lazy { EmojiUtils.emojiMap.toList() }
    private val recentList = ArrayList<List<Pair<String, Int>>>()
    private val emojiList = ArrayList<List<Pair<String, Int>>>()
    private val coolBList = ArrayList<List<Pair<String, Int>>>()
    private val list = listOf(recentList, emojiList, coolBList)
    private lateinit var atTopicResultLauncher: ActivityResultLauncher<Intent>

    /** 刚敲下「@」就跳去选人：回来要把这个「@」替换掉，而不是追在它后面 */
    private var isFromAt = false

    init {
        // 前 4 个（置顶/楼主/层主/图片）不进面板，之后 27 个一页
        for (i in 0..3) {
            emojiList.add(dataList.subList(i * 27 + 4, (i + 1) * 27 + 4))
        }
        coolBList.add(dataList.subList(112, 139))
        coolBList.add(dataList.subList(139, 155))
    }

    private var coverUri: Uri? = null
    private var coverMd5Byte: ByteArray? = null
    private var coverMd5 = ""
    private var coverName = ""

    private lateinit var pickCoverLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var pickBodyLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var cropLauncher: ActivityResultLauncher<Intent>

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, v.paddingTop + top, v.paddingRight, v.paddingBottom)
            insets
        }

        initLauncher()
        initView()
        initEditText()
        initEmojiPanel()
        initAtTopic()
        initObserve()
    }

    private fun initView() {
        binding.back.setOnClickListener { finish() }
        binding.coverContainer.setOnClickListener { pickCover() }
        binding.addImage.setOnClickListener { pickBody() }
        binding.publish.setOnClickListener { publish() }
        // 三样齐了才点亮发布：标题、封面、正文（与 publish() 的校验同一套条件）
        binding.articleTitle.doAfterTextChanged { updatePublishState() }
        binding.emojiBtn.setOnClickListener(this)
        binding.atBtn.setOnClickListener(this)
        binding.tagBtn.setOnClickListener(this)
        binding.main.setOnVisibilityChangeListener(this)
        updatePublishState()
        // 正文里的缩略图可点：点了弹窗写这张图的说明（对应 message 里 image 块的 description）
        binding.articleBody.setOnTouchListener { _, event ->
            val edit = binding.articleBody
            // 长按选词那一套走的是选择状态，别把松手当成点图片
            val span = if (event.action == MotionEvent.ACTION_UP &&
                edit.selectionStart == edit.selectionEnd
            ) imageAt(event.x, event.y) else null
            if (span == null) {
                false
            } else {
                editDescription(span)
                true
            }
        }
    }

    /**
     * 正文输入框的所见即所得装饰：`[微笑]` 渲染成表情图、`@某人` 与 `#话题#` 上主题色，
     * 退格能把它们整块删掉。与发表动态页同一套控件。
     */
    private fun initEditText() {
        binding.articleBody.apply {
            highlightColor = ColorUtils.setAlphaComponent(
                MaterialColors.getColor(
                    this@ArticlePublishActivity,
                    androidx.appcompat.R.attr.colorPrimaryDark,
                    0
                ), 128
            )
            // 正文每次变化都刷一次发布按钮（插图/删图也会走到这里，占位符算正文）
            addTextChangedListener(
                EmojiTextWatcher(this@ArticlePublishActivity, textSize) { updatePublishState() }
            )
            addTextChangedListener(OnTextInputListener("@") {
                isFromAt = true
                launchAtTopic("user")
            })
            setOnKeyListener(FastDeleteAtUserKeyListener())
        }
    }

    /** 表情面板：最近 / 默认 / 酷币 三页，适配器直接复用动态页的 */
    private fun initEmojiPanel() {
        for (i in 0..2) {
            binding.indicator.addView(
                TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply { weight = 1f }
                    gravity = Gravity.CENTER
                    text = listOf("最近", "默认", "酷币")[i]
                    background = getDrawable(R.drawable.selector_bg_trans)
                    setOnClickListener {
                        binding.emojiPanel.setCurrentItem(i, false)
                    }
                    if (i == 0 && BuildConfig.DEBUG) {
                        setOnLongClickListener {
                            viewModel.deleteAll()
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
                                this@ArticlePublishActivity,
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
                                        this@ArticlePublishActivity,
                                        com.google.android.material.R.attr.colorOnPrimary, 0
                                    )
                                else
                                    MaterialColors.getColor(
                                        this@ArticlePublishActivity,
                                        androidx.appcompat.R.attr.colorControlNormal, 0
                                    )
                            )
                        }
                    }
                }
            }
        })
        binding.emojiPanel.adapter = EmojiPagerAdapter(
            list,
            onClickEmoji = {
                with(binding.articleBody) {
                    if (it == "[c001apk]") {
                        // 面板右下角那格是退格，长按走 countDownTimer 连删
                        onBackSpace()
                    } else {
                        editableText.replace(selectionStart, selectionEnd, it)
                        viewModel.updateRecentEmoji(it)
                    }
                }
            },
            onCountStart = { countDownTimer.start() },
            onCountStop = { countDownTimer.cancel() }
        )
    }

    /** @人 / #话题：选择页把结果回填到正文光标处 */
    private fun initAtTopic() {
        atTopicResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val text = result.data?.getStringExtra("data") ?: return@registerForActivityResult
            val edit = binding.articleBody
            if (isFromAt) {
                // 敲「@」跳过去的：把光标前那个「@」换成选中的结果
                isFromAt = false
                val end = edit.selectionStart.coerceAtLeast(1)
                edit.editableText.replace(end - 1, end, text)
            } else {
                val at = edit.selectionStart.coerceIn(0, edit.editableText.length)
                edit.editableText.insert(at, text)
            }
            // 本页不会一进来就弹键盘，只有从选择页回来这条路径补一次
            lifecycleScope.launch(Dispatchers.Main) {
                delay(150)
                binding.main.showKeyboard()
            }
        }
    }

    private fun launchAtTopic(type: String) {
        atTopicResultLauncher.launch(
            Intent(this, AtTopicActivity::class.java).putExtra("type", type),
            TransitionAnim.enterOptionsCompat(this)
        )
    }

    private fun showInput() {
        binding.main.showKeyboard()
    }

    private fun showEmoji() {
        binding.main.showEmojiPanel(true)
    }

    private fun initLauncher() {
        pickCoverLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    cropLauncher.launch(
                        Intent(this, CropImageActivity::class.java)
                            .putExtra(CropImageActivity.EXTRA_URI, uri.toString())
                    )
                }
            }
        cropLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.getStringExtra(CropImageActivity.RESULT_URI)
                        ?.let { Uri.parse(it) }
                    if (uri != null) {
                        coverUri = uri
                        coverName = "${UUID.randomUUID().toString().replace("-", "")}.jpg"
                        val res = getImageDimensionsAndMD5(contentResolver, uri)
                        coverMd5Byte = res.second
                        coverMd5 = res.second?.toHex() ?: ""
                        Glide.with(this).load(uri).into(binding.coverImage)
                        binding.coverImage.isVisible = true
                        binding.coverHint.isVisible = false
                        updatePublishState()
                    }
                }
            }
        pickBodyLauncher =
            registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
                for (uri in uris) {
                    if (bodyImages().size >= MAX_BODY_IMAGES) {
                        makeToast("正文最多插入${MAX_BODY_IMAGES}张图片")
                        break
                    }
                    insertImage(uri)
                }
            }
    }

    private fun initObserve() {
        viewModel.recentEmojiLiveData.observe(this) {
            // 用户正停在「最近」页翻着，就别打断他
            if (binding.emojiPanel.currentItem == 0 && recentList.isNotEmpty())
                return@observe
            recentList.clear()
            if (it.isNullOrEmpty()) {
                // 第一次用、最近还是空的：默认停到「默认」页，别停在一个空页上
                if (viewModel.isInit) {
                    viewModel.isInit = false
                    binding.emojiPanel.setCurrentItem(1, false)
                }
                recentList.add(0, emptyList())
            } else {
                recentList.add(0, it.map { item ->
                    Pair(item.data, EmojiUtils.emojiMap[item.data] ?: R.drawable.ic_logo)
                })
            }
            binding.emojiPanel.adapter?.notifyItemChanged(0)
        }
        viewModel.toastText.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                closeDialog()
                makeToast(it)
            }
        }
        viewModel.over.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                closeDialog()
                makeToast("发布成功")
                finish()
            }
        }
        viewModel.uploadImage.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { responseData ->
                val prefix = responseData.uploadPrepareInfo.uploadImagePrefix
                val fileInfo = responseData.fileInfo

                // message JSON 数组：text/image 块交错；fileInfo[0]=封面，fileInfo[1..]=正文图。
                // 图片顺序直接从输入框里现算（谁在文本里靠前谁编号小），不再维护块列表
                val images = bodyImages().map { it.first.block }
                viewModel.feedData["message"] =
                    Gson().toJson(buildMsgList(prefix, fileInfo.map { it.uploadFileName }))
                viewModel.feedData["message_cover"] = prefix + "/" + fileInfo[0].uploadFileName

                // 上传列表与 uploadFileList 顺序一致：封面 + 正文图
                val uriList = ArrayList<Uri>().apply {
                    add(coverUri!!)
                    images.forEach { add(it.uri) }
                }
                val typeList = ArrayList<String>().apply {
                    add("image/jpeg")
                    images.forEach { add(it.type) }
                }
                val md5List = ArrayList<ByteArray?>().apply {
                    add(coverMd5Byte)
                    images.forEach { add(it.md5Byte) }
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    ossUpload(
                        this@ArticlePublishActivity, responseData, uriList, typeList, md5List,
                        iOnSuccess = { index ->
                            if (index == uriList.lastIndex) {
                                viewModel.onPostCreateFeed()
                            }
                        },
                        iOnFailure = {
                            closeDialog()
                            makeToast("图片上传失败")
                        },
                        closeDialog = { closeDialog() }
                    )
                }
            }
        }
    }

    private fun pickCover() {
        pickCoverLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun pickBody() {
        pickBodyLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** 正文里已插入的图，按在文本里出现的先后排序 */
    private fun bodyImages(): List<Pair<ArticleImageSpan, Int>> {
        val text = binding.articleBody.text ?: return emptyList()
        if (text.isEmpty()) return emptyList()
        return text.getSpans(0, text.length, ArticleImageSpan::class.java)
            .map { it to text.getSpanStart(it) }
            .sortedBy { it.second }
    }

    /**
     * 触摸点落在正文的某张图上就返回它。
     *
     * 光用 [EditText.getOffsetForPosition] 不够：点在图片左边的空白也会算成图片那个字符，
     * 所以拿到 offset 后再拿 Layout 里这一行的水平范围比一次 x。
     * 事件坐标相对 View 左上角，Layout 的原点在文本区（padding 之内，还要补上横向滚动）。
     */
    private fun imageAt(x: Float, y: Float): ArticleImageSpan? {
        val edit = binding.articleBody
        val layout = edit.layout ?: return null
        val offset = edit.getOffsetForPosition(x, y)
        val textX = x - edit.totalPaddingLeft + edit.scrollX
        return bodyImages().firstOrNull { (_, start) ->
            val end = start + PLACEHOLDER.length
            offset in start until end &&
                    textX >= layout.getPrimaryHorizontal(start) &&
                    textX <= layout.getPrimaryHorizontal(end)
        }?.first
    }

    /**
     * 单张图片的说明弹窗。说明只挂在 span 上（输入框里不显示），
     * 发布时由 [buildMsgList] 写进 message 的 image 块；留空即不带说明。
     */
    private fun editDescription(span: ArticleImageSpan) {
        val editText = EditText(this).apply {
            setText(span.block.description)
            hint = "给这张图配个说明（可留空）"
            filters = arrayOf(InputFilter.LengthFilter(DESC_MAX_LENGTH))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(editText)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("图片说明")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                span.block.description = editText.text.toString().trim()
            }
            .setNeutralButton("清除") { _, _ -> span.block.description = "" }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 把图片插到**当前光标处**：插入一个 [PLACEHOLDER] 占位符，再给它挂上缩略图 span。
     * 之后接着打字，图片就留在原地；想换位置就直接删掉它（退格删掉那个字符）再重新插。
     */
    private fun insertImage(uri: Uri) {
        val res = getImageDimensionsAndMD5(contentResolver, uri)
        val md5Byte = res.second
        val width = res.first?.first ?: 0
        val height = res.first?.second ?: 0
        val type = res.first?.third ?: "image/jpeg"
        val ext = if (type.startsWith("image/")) type.substringAfterLast("/") else "jpg"

        val thumbnail = decodeThumbnail(uri) ?: run {
            makeToast("图片读取失败")
            return
        }
        val block = ArticleBlock.Image(
            uri = uri,
            name = "${UUID.randomUUID().toString().replace("-", "")}.$ext",
            resolution = "${width}x${height}",
            md5 = md5Byte?.toHex() ?: "",
            type = type,
            md5Byte = md5Byte,
        )

        val edit = binding.articleBody
        val editable = edit.text ?: Editable.Factory.getInstance().newEditable("").also {
            edit.setText(it)
        }
        val at = edit.selectionStart.coerceIn(0, editable.length)
        editable.insert(at, PLACEHOLDER)
        editable.setSpan(
            ArticleImageSpan(thumbnail, block),
            at,
            at + PLACEHOLDER.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // 光标落到图片之后，继续选图就是依次往后排
        edit.setSelection(at + PLACEHOLDER.length)
    }

    /**
     * 输入框里显示的缩略图：固定 140dp 高、宽度按原图比例。
     * 竖长图会窄一些、横图会宽一些，都不会把一行撑满整屏。
     *
     * 解码时先按比例降采样再精确缩放，避免原图（可能几千万像素）直接进内存。
     */
    private fun decodeThumbnail(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetHeight = (THUMB_HEIGHT_DP * resources.displayMetrics.density).roundToInt()
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val targetWidth =
            (targetHeight.toFloat() * decoded.width / decoded.height).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        // BitmapDrawable 会按「目标密度 / bitmap 密度」再缩一次，这里把密度对齐成屏幕密度，
        // 尺寸就正好是上面算出来的像素值
        scaled.density = resources.displayMetrics.densityDpi
        return scaled
    }

    /**
     * 把输入框里的「文字 + 内嵌图片」还原成官方要的 message 结构：
     * 以每个图片占位符为切点，切点前的文字攒成一个 text 块，图片本身是一个 image 块。
     * [fileNames] 是上传后拿到的文件名（`[0]` 是封面），正文图从 `[1]` 起按出现顺序对应。
     */
    private fun buildMsgList(prefix: String, fileNames: List<String>): List<Map<String, String>> {
        val text = binding.articleBody.text ?: return emptyList()
        val msgList = ArrayList<Map<String, String>>()
        var cursor = 0
        var bodyIndex = 0
        bodyImages().forEach { (span, start) ->
            val chunk = text.subSequence(cursor, start).toString().trim()
            if (chunk.isNotEmpty()) {
                msgList.add(mapOf("type" to "text", "message" to chunk))
            }
            msgList.add(
                mapOf(
                    "type" to "image",
                    "url" to prefix + "/" + fileNames.getOrNull(1 + bodyIndex),
                    "description" to span.block.description
                )
            )
            bodyIndex++
            cursor = text.getSpanEnd(span)
        }
        val tail = text.subSequence(cursor, text.length).toString().trim()
        if (tail.isNotEmpty()) {
            msgList.add(mapOf("type" to "text", "message" to tail))
        }
        return msgList
    }

    /**
     * 正文里的图片占位：用一个 [PLACEHOLDER] 字符占地，绘制时显示缩略图。
     *
     * 图片的元数据（上传文件名 / 分辨率 / md5 / 原图 uri）挂在这个 span 上 ——
     * 用户在输入框里怎么改文字都不影响它，发布时按 span 的位置把富文本切回块结构。
     */
    private class ArticleImageSpan(
        thumbnail: Bitmap,
        val block: ArticleBlock.Image,
    ) : ImageSpan(MyApplication.context, thumbnail, DynamicDrawableSpan.ALIGN_BOTTOM)

    override fun onClick(view: View) {
        when (view.id) {
            R.id.emojiBtn -> {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                if (binding.emojiBtn.isSelected) showInput() else showEmoji()
            }

            R.id.atBtn -> {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                launchAtTopic("user")
            }

            R.id.tagBtn -> {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                launchAtTopic("topic")
            }
        }
    }

    /** 表情面板展开/收起：按钮图标在「表情」和「键盘」之间切 */
    override fun onVisibilityChange(visibility: Int) {
        binding.emojiBtn.isSelected = visibility == View.VISIBLE
        binding.emojiBtn.setImageResource(
            if (visibility == View.VISIBLE) R.drawable.ic_keyboard else R.drawable.ic_emoji
        )
    }

    /** 面板右下角那格退格：给输入框发一个 DEL，再补一次震动 */
    private fun onBackSpace() {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ViewCompat.performHapticFeedback(
            binding.articleBody, HapticFeedbackConstantsCompat.CONFIRM
        )
    }

    /** 长按退格连删 */
    private val countDownTimer: CountDownTimer = object : CountDownTimer(100000, 50) {
        override fun onTick(millisUntilFinished: Long) {
            onBackSpace()
        }

        override fun onFinish() {}
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer.cancel()
    }

    /**
     * 发布按钮的可用态：标题、封面、正文（有文字或有图）三样齐了才点成主题色。
     *
     * 与 [publish] 的校验同一套条件，所以按钮亮着就一定能发出去，不用再靠 toast 提示缺什么。
     * 用 isClickable 而不是 isEnabled —— 和发表动态页那个同款按钮一致（ReplyActivity 里
     * 也是这么做的），isEnabled=false 会连 foreground 的点击反馈一起压暗。
     */
    private fun updatePublishState() {
        val ready = binding.articleTitle.text.toString().trim().isNotEmpty() &&
                coverUri != null &&
                (!binding.articleBody.text.isNullOrBlank() || bodyImages().isNotEmpty())
        binding.publish.isClickable = ready
        binding.publish.setTextColor(
            if (ready)
                MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0)
            else
                getColor(android.R.color.darker_gray)
        )
    }

    private fun publish() {
        val title = binding.articleTitle.text.toString().trim()
        if (title.isEmpty()) {
            makeToast("请输入标题")
            return
        }
        if (coverUri == null) {
            makeToast("请选择封面图")
            return
        }
        val images = bodyImages().map { it.first.block }
        if (binding.articleBody.text.isNullOrBlank() && images.isEmpty()) {
            makeToast("请输入正文")
            return
        }

        // uploadFileList：封面 + 正文图
        val uploadFiles = ArrayList<ArticleUploadFile>()
        uploadFiles.add(ArticleUploadFile(coverName, "1600x719", coverMd5, 0))
        images.forEach {
            uploadFiles.add(ArticleUploadFile(it.name, it.resolution, it.md5, 0))
        }

        viewModel.feedData.apply {
            put("id", "")
            put("type", "feed")
            put("status", "1")
            put("publish_status", if (binding.checkBox.isChecked) "1" else "0")
            put("message_title", title)
            put("is_html_article", "1")
            put("pic", "")
        }

        viewModel.onPostOSSUploadPrepare(uploadFiles)
        showDialog()
    }

    @SuppressLint("InflateParams")
    private fun showDialog() {
        dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_MaterialAlertDialog_Rounded
        ).apply {
            setView(
                LayoutInflater.from(this@ArticlePublishActivity)
                    .inflate(R.layout.dialog_refresh, null, false)
            )
            setCancelable(false)
        }.create()
        dialog?.show()
    }

    private fun closeDialog() {
        dialog?.dismiss()
        dialog = null
    }

    companion object {
        /** 图片占位符：U+FFFC OBJECT REPLACEMENT CHARACTER，文本里一个字符代表一张图 */
        private const val PLACEHOLDER = "\uFFFC"

        /** 服务端上限：正文最多 20 张图 */
        private const val MAX_BODY_IMAGES = 20

        /** 输入框里缩略图的显示高度（宽度按原图比例算） */
        private const val THUMB_HEIGHT_DP = 140f

        /** 单张图片的说明最长多少字（与旧版块式布局的 maxLength 一致） */
        private const val DESC_MAX_LENGTH = 200
    }
}
