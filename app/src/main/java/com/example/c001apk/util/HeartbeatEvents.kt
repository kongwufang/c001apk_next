package com.example.c001apk.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

/**
 * 心跳「顺带下发」的事件执行器（2026-11-07 ✓）
 *
 * ## 为什么放心跳里（用户原话）
 * > 「心跳接口可以理解为是一个可以即时与服务器交换信息的通道，
 * >   所以心跳接口允许"顺带"其他接口的返回，触发其他接口的事件」
 *
 * ⇒ 心跳每 30~60 秒一次、App 在前台就跑 ✓
 *   把它当成**长轮询的轻量替代**：服务器在任何一次心跳响应里都能塞指令 ✓
 *
 * ## 支持的 op
 *
 * | op | 作用 | 需要字段 | 风险 |
 * |---|---|---|---|
 * | `toast` | 弹一条 toast | `text` | 低 |
 * | `dialog` | 弹一个对话框（标题+正文+「知道了」）| `title` `text` | 低 |
 * | `gates` | 立刻重拉云控开关 | — | 低 |
 * | `notice` | 预留（公告每次开侧栏都拉 ✓）| — | 低 |
 * | `update` | 立刻检查更新并弹更新框 | — | 低 |
 * | `clearCache` | 清 Glide 图片缓存 | — | 低 |
 * | `reload` | 重建当前页面（recreate）| — | 中 |
 * | `open` | 打开一个链接（**只允许白名单域名** ✗）| `url` `title` | **中高** |
 * | `log` | 上报诊断信息（A+B+C 三层 ✓）| — | 低 |
 * | `noop` | 什么都不做（服务器试探 / 埋点）| — | 无 |
 *
 * ## 四条原则
 * · **按 id 去重** ✓ 同一条只执行一次（`PrefManager` 存最近 50 个 ✓）
 * · **失败静默** ✓ 任何异常都不能影响心跳 ✗
 * · **不认识就跳过** ✓ 老客户端遇到新 op 不能崩 ✗
 * · **危险操作只给白名单** ✗ `open` 只允许 houlangs.cn / coolapk.com ✓
 */
object HeartbeatEvents {

    private const val OP_TOAST = "toast"
    private const val OP_DIALOG = "dialog"
    private const val OP_GATES = "gates"
    private const val OP_NOTICE = "notice"
    private const val OP_UPDATE = "update"
    private const val OP_CLEAR_CACHE = "clearCache"
    private const val OP_RELOAD = "reload"
    private const val OP_OPEN = "open"
    private const val OP_LOG = "log"
    private const val OP_NOOP = "noop"

    /** `open` 允许的域名后缀（防止被当成任意跳板 ✗） */
    private val OPEN_ALLOW = listOf("houlangs.cn", "coolapk.com", "github.com")

    private const val MAX_SEEN = 50

    private val main = Handler(Looper.getMainLooper())

    fun dispatch(ctx: Context, root: JSONObject) {
        val arr = root.optJSONArray("events") ?: return
        if (arr.length() == 0) return

        val handled = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            if (PrefManager.isHeartbeatEventDone(id)) continue
            val ok = runCatching { run(ctx, o) }.getOrDefault(false)
            if (ok) handled.add(id)
        }
        if (handled.isNotEmpty()) PrefManager.markHeartbeatEventsDone(handled, MAX_SEEN)
    }

    private fun run(ctx: Context, o: JSONObject): Boolean {
        return when (val op = o.optString("op").trim()) {

            OP_TOAST -> {
                val text = o.optString("text").trim()
                if (text.isEmpty()) return false
                onMain { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
                true
            }

            OP_DIALOG -> {
                val title = o.optString("title").trim().ifEmpty { "来自服务器的消息" }
                val text = o.optString("text").trim()
                if (text.isEmpty()) return false
                val act = SzlmHint.current() ?: run {
                    // 没有前台 Activity ⇒ 退化成 toast ✓ 不丢消息 ✓
                    onMain { Toast.makeText(ctx, "$title\n$text", Toast.LENGTH_LONG).show() }
                    return true
                }
                onMain {
                    runCatching {
                        MaterialAlertDialogBuilder(act)
                            .setTitle(title)
                            .setMessage(text)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                true
            }

            OP_GATES -> {
                runCatching { CloudGates.invalidate() }
                true
            }

            OP_NOTICE -> true   // 预留：公告本身无缓存，开侧栏就是新的 ✓

            OP_UPDATE -> {
                // 后台拉一次更新信息；有新版本才弹框 ✓
                Thread {
                    runCatching {
                        val info = kotlinx.coroutines.runBlocking { UpdateChecker.fetchUpdate() }
                            ?: return@Thread
                        if (!info.isNewer) return@Thread
                        val act = SzlmHint.current() ?: return@Thread
                        onMain { runCatching { UpdateChecker.showUpdateDialog(act, info) } }
                    }
                }.start()
                true
            }

            OP_CLEAR_CACHE -> {
                Thread {
                    runCatching {
                        val glide = com.bumptech.glide.Glide.get(ctx)
                        glide.clearMemory()
                        glide.clearDiskCache()
                    }
                }.start()
                true
            }

            OP_RELOAD -> {
                val act = SzlmHint.current() ?: return true
                onMain { runCatching { act.recreate() } }
                true
            }

            OP_OPEN -> {
                val url = o.optString("url").trim()
                if (url.isEmpty()) return false
                // ✗ 只允许白名单域名 ⇒ 防止服务器被拿来当任意跳板
                val host = runCatching { android.net.Uri.parse(url).host?.lowercase() }.getOrNull()
                if (host.isNullOrEmpty() || OPEN_ALLOW.none { host == it || host.endsWith(".$it") }) {
                    android.util.Log.w("HeartbeatEvents", "open 拒绝非白名单域名: $host")
                    return true   // 记为已处理 ✓ 别让它每次心跳都重试 ✗
                }
                onMain {
                    runCatching { NetWorkUtil.openLink(ctx, url, o.optString("title").ifEmpty { null }) }
                }
                true
            }

            OP_LOG -> {
                // 诊断上报：A（设备快照）+ B（崩溃摘要）+ C（请求日志聚合）✓
                Thread { runCatching { DiagReport.upload(ctx) } }.start()
                true
            }

            OP_NOOP -> true

            else -> {
                android.util.Log.d("HeartbeatEvents", "跳过未知 op=$op")
                true
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
