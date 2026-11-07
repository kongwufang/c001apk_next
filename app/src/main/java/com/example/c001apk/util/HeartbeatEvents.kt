package com.example.c001apk.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
 * ## 协议（服务器 → 客户端）
 * ```json
 * {
 *   "interval": 60,
 *   "events": [
 *     { "id": "e-20261107-1", "op": "toast", "text": "服务器在线 ✓" },
 *     { "id": "e-20261107-2", "op": "gates" },
 *     { "id": "e-20261107-3", "op": "notice" }
 *   ]
 * }
 * ```
 *
 * | op | 作用 | 字段 |
 * |---|---|---|
 * | `toast` | 弹一条 toast | `text` 必填 |
 * | `gates` | 立刻重拉云控开关（`CloudGates`）| — |
 * | `notice` | 立刻重拉公告（下次开侧栏就是新内容 ✓）| — |
 * | `noop`  | 什么都不做（服务器试探 / 埋点用）| — |
 *
 * ## 三条原则
 * · **按 id 去重** ✓ 同一条指令只执行一次（`PrefManager` 里存已执行 id ✓ 只留最近 50 个 ✗）
 * · **失败静默** ✓ 任何解析/执行异常都不能影响心跳 ✗
 * · **不认识就跳过** ✓ 老客户端遇到新 op 不能崩 ✗（forward compatible ✓）
 */
object HeartbeatEvents {

    /** 支持的操作 */
    private const val OP_TOAST = "toast"
    private const val OP_GATES = "gates"
    private const val OP_NOTICE = "notice"
    private const val OP_NOOP = "noop"

    /** 已执行 id 最多保留多少个（防 prefs 无限涨 ✗） */
    private const val MAX_SEEN = 50

    /**
     * 解析并执行心跳响应里的事件
     *
     * @param root 心跳响应的 JSON 根对象（没有 events 字段就什么都不做 ✓）
     */
    fun dispatch(ctx: Context, root: JSONObject) {
        val arr = root.optJSONArray("events") ?: return
        if (arr.length() == 0) return

        val handled = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            // 没 id 就没法去重 ✗ 一条都不执行 ✓（防止服务器手滑造成无限 toast ✗）
            if (id.isEmpty()) continue
            if (PrefManager.isHeartbeatEventDone(id)) continue

            val ok = runCatching { run(ctx, o) }.getOrDefault(false)
            // 只有执行成功才记下来 ✓ 失败的下次心跳还会重试 ✓
            if (ok) handled.add(id)
        }
        if (handled.isNotEmpty()) PrefManager.markHeartbeatEventsDone(handled, MAX_SEEN)
    }

    /** 单条执行；返回 true = 执行成功（可以记为已做 ✓） */
    private fun run(ctx: Context, o: JSONObject): Boolean {
        return when (val op = o.optString("op").trim()) {
            OP_TOAST -> {
                val text = o.optString("text").trim()
                if (text.isEmpty()) return false
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
                    }
                }
                true
            }

            OP_GATES -> {
                // 云控开关：下次读的时候自然会刷新（这里主动清一下快照）
                runCatching { CloudGates.invalidate() }
                true
            }

            OP_NOTICE -> {
                // 公告没有内存缓存（每次开侧栏都拉 ✓）⇒ 这里只把「已读」清掉不合适 ✗
                // ⇒ 什么都不用做，但保留 op 让服务器能"标记一次拉取"✓
                true
            }

            OP_NOOP -> true

            else -> {
                // 不认识的 op：跳过 ✓ 不当作失败 ✗（否则每次心跳都重试 ✗）
                android.util.Log.d("HeartbeatEvents", "跳过未知 op=$op")
                true
            }
        }
    }
}
