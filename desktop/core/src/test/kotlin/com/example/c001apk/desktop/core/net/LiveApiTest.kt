package com.example.c001apk.desktop.core.net

import com.example.c001apk.desktop.core.model.Resp
import com.example.c001apk.desktop.core.model.Feed
import com.example.c001apk.desktop.core.model.HomeEntity
import com.example.c001apk.desktop.core.model.LoginInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 真实接口联调测试（默认跳过）。
 *
 * 开启方式：
 *   ./gradlew :core:test -DrunLiveTests=true
 *   未登录态即可跑「公开接口」用例；
 *   若要跑登录态用例，再带上（凭据只从 system property 读，不落盘）：
 *     -Dc001apk.uid=...  -Dc001apk.username=...  -Dc001apk.token=...
 *
 * 注意：该测试会**真实访问酷安接口**，请勿在循环里反复跑 ——
 * 详情接口有风控限流（返回 5902），高频调用会把环境打成 403。
 */
class LiveApiTest {

    private val enabled: Boolean = System.getProperty("runLiveTests", "false").toBoolean()

    private fun client(): Client = Client(session = Session())

    private fun cred(): Triple<String, String?, String>? {
        val uid = System.getProperty("c001apk.uid")
        val name = System.getProperty("c001apk.username")
        val token = System.getProperty("c001apk.token")
        if (uid.isNullOrBlank() || token.isNullOrBlank()) {
            println("未提供 -Dc001apk.uid / -Dc001apk.token，跳过登录态联调")
            return null
        }
        return Triple(uid, name, token)
    }

    @Test
    fun `公开接口 首页头条`() {
        if (!enabled) return
        val resp: Resp<List<HomeEntity>> = client().api.indexV8(page = 1).executeSync()
        assertEquals(0, resp.status ?: -1, "status 非 0：${resp.message}")
        val list = resp.data
        assertNotNull(list, "data 为空")
        assertTrue(list.isNotEmpty(), "首页返回空列表")
        assertNotNull(list.first().entityType, "条目缺少 entityType")
    }

    @Test
    fun `公开接口 话题列表`() {
        if (!enabled) return
        val resp = client().api.dataList(
            url = "/topic/hotTagList", hotType = "total", recommend = 1
        ).executeSync()
        assertEquals(0, resp.status ?: -1, "status 非 0：${resp.message}")
        assertTrue(!resp.data.isNullOrEmpty(), "话题列表为空")
    }

    @Test
    fun `登录态 自检与业务接口`() {
        if (!enabled) return
        val (uid, name, token) = cred() ?: return

        val c = Client(session = Session(uid = uid, username = name, token = token))

        val info: Resp<LoginInfo> = c.api.checkLoginInfo().executeSync()
        assertEquals(0, info.status ?: -1, "自检失败：${info.message}")
        val d = info.data
        assertNotNull(d, "自检 data 为空 —— 可能是登录信息有误")
        assertEquals(uid, d.uid, "自检返回的 uid 与凭据不符")

        val count = c.api.checkCount().executeSync()
        assertEquals(0, count.status ?: -1, "未读计数失败：${count.message}")
        assertNotNull(count.data, "未读计数 data 为空")
        println("未读：badge=${count.data.badge} message=${count.data.message}")

        val feeds = c.api.getUserFeedList(uid = uid, page = 1).executeSync()
        assertEquals(0, feeds.status ?: -1, "我的动态失败：${feeds.message}")
        println("我的动态条数：${feeds.data?.size}")

        val collections = c.api.getCollectionList().executeSync()
        assertEquals(0, collections.status ?: -1, "收藏夹失败：${collections.message}")
        println("收藏夹数量：${collections.data?.size}")
    }

    /**
     * 详情 + 评论（走 api2）。
     *
     * ⚠️ 该接口有风控限流：若返回 `403 ... (5902)`，说明当前环境被限流，
     *    不是客户端问题 —— 等几分钟再试（见 _rev/win_ref/DETAIL_5902.md）。
     */
    @Test
    fun `登录态 详情与评论（api2）`() {
        if (!enabled) return
        val (uid, name, token) = cred() ?: return

        val c = Client(session = Session(uid = uid, username = name, token = token))

        val feeds = c.api.getUserFeedList(uid = uid, page = 1).executeSync()
        val fid = feeds.data?.firstOrNull()?.id ?: fail("拿不到用于测试的动态 id")
        println("测试动态 id = $fid")

        val detail: Resp<Feed> = c.api2.getFeedContent(id = fid).executeSync()
        if ((detail.status ?: 0) != 0) {
            fail("详情失败：status=${detail.status} message=${detail.message}（5902 表示被限流）")
        }
        assertNotNull(detail.data, "详情 data 为空")
        assertEquals(fid, detail.data.id)

        val replies = c.api2.getFeedContentReply(id = fid).executeSync()
        if ((replies.status ?: 0) != 0) {
            fail("评论失败：status=${replies.status} message=${replies.message}")
        }
        println("评论条数：${replies.data?.size}")
    }
}
