package com.example.c001apk.ui.messagedetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c001apk.BR
import com.example.c001apk.databinding.ItemMessageChatBinding
import com.example.c001apk.ui.user.UserActivity
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.MessageKit

/**
 * 聊天记录列表。气泡靠左还是靠右，由消息的 fromuid 是否等于当前登录 uid 决定
 * （`/v6/message/chat` 里 fromuid = 发送者、uid = 接收者）。
 *
 * 头像不用消息自身的字段：聊天记录里的 `userAvatar` / `fromUserAvatar` 对应的是
 * 接收者 / 发送者，方向容易反，所以直接由会话信息传进来（跟桌面版一致）。
 * 头像右上角那枚认证角标同理：`/v6/message/chat` 不下发认证字段，只能拿会话里
 * 带过来的对方 uid 去查云端认证表（见 [com.example.c001apk.util.VerifyBadge]）。
 *
 * ## 2026-10-30 改成吃 [ChatItem]
 *
 * 原来直接吃 `MessageResponse.Data` ⇒ 只有服务端确认过的消息才上屏 ✗
 * 现在每项多带一个 `state`：
 * - `STATE_SENDING` ⇒ 气泡左侧转圈 ✓
 * - `STATE_FAILED`  ⇒ 换成红色感叹号，点一下回调 [onRetry] 让页面弹「是否重新发送」✓
 */
class MessageDetailAdapter(
    private val myUid: String,
    private val myAvatar: String,
    private val partnerAvatar: String,
    /** 对方的 uid，只给认证角标用（气泡左右由 fromuid 判断，用不到它） */
    private val partnerUid: String,
    /**
     * 图片消息的地址要现问 `showImage` 换签名地址（CDN 的裸地址会被 auth_key 挡），
     * adapter 自己没有协程作用域，所以这件事交给页面 / ViewModel 做，结果回调回来。
     * 回调里除了地址还有图片原始宽高（气泡按它撑开），同一条消息只会问一次（ViewModel 有缓存）。
     *
     * ⚠️ 本地乐观消息（[MessageKit.isLocalPic]）不走这里 —— 它的图是相册 uri，直接给 Glide ✓
     */
    private val loadPic: (id: String, onReady: (MessageKit.MessagePic?) -> Unit) -> Unit,
    /** 点红色感叹号：页面上弹「是否重新发送」 */
    private val onRetry: (ChatItem) -> Unit = {},
    /**
     * 气泡图片**尺寸确定之后**回调（见下方 bind 里的 pic 回调）。
     *
     * 为什么要这个：进入页面时图片还在异步换地址 + 加载 ⇒ 气泡先是小占位 ✗
     * 图片回来把气泡撑大 ⇒ 上方内容整体变高 ⇒ 列表就不在底部了 ✗
     * 页面收到这个回调后，只要用户没主动往上滑，就再贴一次底 ✓
     */
    private val onPicSized: () -> Unit = {}
) : ListAdapter<ChatItem, MessageDetailAdapter.ChatViewHolder>(ChatDiffCallback()) {

    class ChatViewHolder(
        val binding: ItemMessageChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ChatItem,
            isMe: Boolean,
            myAvatar: String,
            partnerAvatar: String,
            partnerUid: String,
            loadPic: (String, (MessageKit.MessagePic?) -> Unit) -> Unit,
            onRetry: (ChatItem) -> Unit,
            onPicSized: () -> Unit
        ) {
            val data = item.data
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.isMe, isMe)
            binding.setVariable(BR.myAvatar, myAvatar)
            binding.setVariable(BR.partnerAvatar, partnerAvatar)
            binding.setVariable(BR.partnerUid, partnerUid)
            // 0=已发送 1=发送中（转圈）2=失败（红叹号），布局里按它切可见性
            binding.setVariable(BR.state, item.state)
            binding.executePendingBindings()

            // 失败时可以点感叹号重发；其它状态不挂点击（避免误触）
            binding.sendFailed.setOnClickListener {
                if (item.isFailed) onRetry(item)
            }

            // 长按 ⇒ 复制正文（2026-10-30 用户要求）
            //
            // ⚠️ 必须挂在**正文 LinkTextView 自己**身上，不能只挂 itemView ✗
            //   气泡正文是 LinkTextView（clickable + 自己的 MovementMethod）⇒
            //   触摸事件先被它消费掉，父容器 itemView 的 onLongClick 永远不触发 ✗
            //   （实测：只在 itemView 上挂时，长按毫无反应 ✓）
            // 正文是 HTML ⇒ 走 MessageKit.copyText 降级成纯文本 ✓
            // 纯图片消息没有文字 ⇒ 提示一下，别静默无反应 ✗
            val copyLongClick = View.OnLongClickListener {
                val text = MessageKit.copyText(data)
                if (text.isBlank()) {
                    Toast.makeText(itemView.context, "这条没有可复制的文字", Toast.LENGTH_SHORT).show()
                } else {
                    val cm = itemView.context
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("私信", text))
                    Toast.makeText(itemView.context, "已复制", Toast.LENGTH_SHORT).show()
                }
                true
            }
            binding.textLeft.setOnLongClickListener(copyLongClick)
            binding.textRight.setOnLongClickListener(copyLongClick)
            itemView.setOnLongClickListener(copyLongClick)

            // 点**对方头像** ⇒ 跳他的主页 ✓（2026-10-30 用户要求）
            // 列表项会被复用 ⇒ 每次 bind 都重设一遍，别让旧监听留着 ✓
            // 优先用会话带进来的 partnerUid；万一为空，退回这条消息的 fromuid
            // （自己发的那侧头像本来就 GONE，不会被点到 ✓）
            binding.avatarLeft.setOnClickListener {
                val target = partnerUid.takeIf { it.isNotBlank() }
                    ?: data.fromuid.takeIf { it.isNotBlank() }
                    ?: return@setOnClickListener
                IntentUtil.startActivity<UserActivity>(binding.avatarLeft.context) {
                    putExtra("id", target)
                }
            }

            val view = if (isMe) binding.picRight else binding.picLeft
            // 本地乐观消息：图片是相册 uri，直接给 Glide，别去问 showImage（那个要服务端 id ✗）
            if (MessageKit.isLocalPic(data)) {
                view.tag = null
                view.setOnClickListener(null)
                view.layoutParams = view.layoutParams.apply {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                Glide.with(view).load(Uri.parse(data.messagePic)).into(view)
                return
            }

            val id = if (MessageKit.hasPic(data)) MessageKit.picId(data) else ""
            // 换地址是异步的，等回调回来时 holder 可能已经复用了，
            // 用 tag 记住当前这个 ImageView 摊到的是哪条消息，对不上就丢掉。
            view.tag = id
            view.setImageDrawable(null)
            // 复用过来时上一条消息的点击监听可能还在，先摘掉，免得点空图点开上一张
            view.setOnClickListener(null)
            // 上一条消息定死的宽高也不能留下来，否则新图先按旧比例显示一帧
            view.layoutParams = view.layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            if (id.isEmpty()) return
            loadPic(id) { pic ->
                if (view.tag == id && pic != null) {
                    if (pic.width > 0 && pic.height > 0) {
                        // 量到了原始像素：按真实比例把占位尺寸定死，再按同一尺寸取图。
                        // 占位尺寸先定下来，图片回来时不会带着列表再跳一下。
                        val (width, height) =
                            ImageUtil.chatImageSize(view.context, pic.width, pic.height)
                        view.layoutParams = view.layoutParams.apply {
                            this.width = width
                            this.height = height
                        }
                        ImageUtil.showChatIMG(view, pic.url, width, height)
                    } else {
                        // 没量出来（CDN 那边解不出宽高）时**不要**塞方形占位：
                        // 交回 wrap_content，让 layout 的 adjustViewBounds 跟着实际
                        // 解码出来的位图走，怎么算都是图片自己的比例。
                        ImageUtil.showChatIMG(view, pic.url)
                    }
                    // 点一下全屏看大图（long click 存图由 Mojito 那侧接管）
                    view.setOnClickListener {
                        ImageUtil.startChatBigImgViewSimple(view, pic.url)
                    }
                    // 尺寸刚定下来 ⇒ 通知页面：如果用户还在底部，就再贴一次底 ✓
                    // 必须 post：此刻 layoutParams 还没走完测量/布局 ✗
                    binding.root.post { onPicSized() }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        return ChatViewHolder(
            ItemMessageChatBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = currentList[position]
        holder.bind(
            item,
            item.data.fromuid == myUid,
            myAvatar,
            partnerAvatar,
            partnerUid,
            loadPic,
            onRetry,
            onPicSized
        )
    }

}

class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
    /**
     * 主键：本地乐观消息用**不变的** `localId` ✓（换成服务端 id 会让该项被当成
     * 删除+新增，列表闪一下 ✗）；服务端消息用 `entityId` → `id` → 兜底串。
     */
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean =
        oldItem.key == newItem.key

    /**
     * 内容比较要**包含 state** ✓ —— `ChatItem` 是 data class，直接 `==` 就够；
     * 否则发送中 → 已发送的状态变化不会触发重绑，转圈会一直挂着 ✗
     */
    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean =
        oldItem == newItem
}
