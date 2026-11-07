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
 * | `notice` | **直接下发一条公告卡**（进侧栏 ✓ 可选立刻弹一下）| `card` | 低 |
 * | `update` | **直接下发更新信息**（不请求接口 ✓）或触发查更新 | `update`（可选）| 低 |
 * | `clearCache` | 清 Glide 图片缓存 | — | 低 |
 * | `reload` | 重建当前页面（recreate）| — | 中 |
 * | `open` | 打开一个链接（**只允许白名单域名** ✗）| `url` `title` | **中高** |
 * | `log` | 上报诊断信息（A+B+C 三层 ✓）| — | 低 |
 * | `noop` | 什么都不做（服务器试探 / 埋点）| — | 无 |
 *
 * ## `notice`：直接把公告卡塞给客户端
 *
 * ```json
 * // ① 进侧栏 + 立刻弹一条 toast（**默认就是这种** ✓ popup 省略 = true）
 * {"id":"n1","op":"notice",
 *  "card":{"id":"card-20261107-a","title":"维护公告","body":"今晚 2 点重启"}}
 *
 * // ② 只进侧栏，当场不打扰（用户下次开侧栏才看到）
 * {"id":"n2","op":"notice","popup":false,
 *  "card":{"id":"card-20261107-b","title":"…","body":"…"}}
 * ```
 *
 * ⚠️ 卡片 id 必须**唯一且稳定** ✓
 *   · 客户端按 id 去重、也按 id 记「已读」（用户点叉后不再显示 ✓）
 *   · 想再推一次同样的内容 ⇒ **换 id** ✓（例如带上日期）
 *   · 卡片字段与 `/notice/` 的卡完全一致（title/body/bgColor/accent/buttons/time/priority ✓）
 *   · 推送的卡会**持久保存在本机** ✓ 和服务器下发的卡合并显示 ✓
 *
 * ## `update`：直接把更新信息塞给客户端
 *
 * ```json
 * // ① 直接下发（推荐：不占用 /update/ 的 60 秒缓存 ✓ 立刻弹窗）
 * {"id":"u1","op":"update",
 *  "update":{"versionName":"V1.1.0","versionCode":10110,"changelog":"…",
 *            "lines":[{"name":"GitHub","url":"https://…"}]}}
 *
 * // ② 只说「去查一下」（沿用 /update/ 的缓存与签名校验）
 * {"id":"u2","op":"update"}
 * ```
 *
 * ⚠️ `versionCode` 必须**大于**当前包才会弹 ✓
 *   （推了个比当前还旧的会被静默忽略 ✓ 防止手滑把用户"降级提示"）
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
        // ★ 2026-11-07 用户要求：只有开了「加入用户改善计划」才接受下发事件 ✓
        //   关着时**连 toast 都不弹** ✓（心跳照常上报 ✗ 统计不受影响 ✓）
        if (!PrefManager.joinImprovePlan) {
            AppLog.d(LogModule.HB, "HeartbeatEvents", "未加入用户改善计划，跳过 ${root.optJSONArray("events")?.length() ?: 0} 条事件")
            return
        }
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

            // ---------- 直接下发一条公告卡（不请求 /notice/ ✓） ----------
            OP_NOTICE -> {
                val card = o.optJSONObject("card") ?: return true   // 没 card 就当没事发生 ✓
                val ok = NoticeRepo.pushCard(card)
                // ★ popup **默认 true**（2026-11-07 用户反馈「卡片没有显示」）
                //   ⇒ 推送公告时**立刻弹一条 toast** ✓ 用户当场就知道有内容了 ✓
                //   ⇒ 想安静地只进侧栏 ⇒ 事件里写 "popup": false ✓
                if (ok && o.optBoolean("popup", true)) {
                    val title = card.optString("title").trim()
                    val body = card.optString("body").trim()
                    val show = if (title.isNotEmpty()) "$title\n$body" else body
                    if (show.isNotBlank()) {
                        onMain { Toast.makeText(ctx, show, Toast.LENGTH_LONG).show() }
                    }
                }
                ok
            }

            // ---------- 直接下发更新信息（不请求 /update/ ✓）或只说"去查" ----------
            OP_UPDATE -> {
                val inline = o.optJSONObject("update")
                if (inline == null) {
                    // 只说「去查一下」⇒ 走原有流程（有新版本才弹 ✓）
                    Thread {
                        runCatching {
                            val info = kotlinx.coroutines.runBlocking { UpdateChecker.fetchUpdate() }
                                ?: return@Thread
                            if (!info.isNewer) return@Thread
                            val act = SzlmHint.current() ?: return@Thread
                            onMain { runCatching { UpdateChecker.showUpdateDialog(act, info) } }
                        }
                    }.start()
                    return true
                }
                val info = UpdateChecker.parsePushedUpdate(inline)
                if (info == null) return false
                if (!info.isNewer) return true   // 推了个比当前旧的 ⇒ 忽略 ✓
                val act = SzlmHint.current()
                if (act == null) {
                    onMain {
                        Toast.makeText(ctx, "发现新版本 ${info.versionName}", Toast.LENGTH_LONG).show()
                    }
                    return true
                }
                onMain { runCatching { UpdateChecker.showUpdateDialog(act, info) } }
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
                    AppLog.w(LogModule.HB, "HeartbeatEvents", "open 拒绝非白名单域名: $host")
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
                AppLog.d(LogModule.HB, "HeartbeatEvents", "跳过未知 op=$op")
                true
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
