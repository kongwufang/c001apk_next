package com.example.c001apk.desktop.core.net

import com.example.c001apk.desktop.core.model.Feed
import com.example.c001apk.desktop.core.model.HomeCard
import com.example.c001apk.desktop.core.model.LoginInfo
import com.example.c001apk.desktop.core.model.Resp
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
 * ⚠️ 该测试会**真实访问酷安接口**，请勿在循环里反复跑 ——
 * 详情接口有风控限流（返回 5902），高频调用会把环境打成 403。
 * 详见 _rev/win_ref/DETAIL_5902.md。
 */
class LiveApiTest {

    private val enabled: Boolean = System.getProperty("runLiveTests", "false").toBoolean()

    private fun client(): Client = Client(session = Session())

    /** 返回 (uid, username, token)；未提供则返回 null 并打印跳过原因 */
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

    private fun checkStatus(resp: Resp<*>, what: String) {
        val st = resp.status ?: 0
        assertEquals(0, st, "$what 失败：status=$st message=${resp.message}")
    }

    // ─────────────── 公开接口 ───────────────

    @Test
    fun `公开接口 首页头条`() {
        if (!enabled) return
        // indexV8 返回【卡片信封】列表，真实内容在 entities 里（实测）
        val resp: Resp<List<HomeCard>> = client().api.indexV8(page = 1).executeSync()
        checkStatus(resp, "首页头条")
        val cards = resp.data
        assertNotNull(cards, "首页 data 为空")
        assertTrue(cards.isNotEmpty(), "首页返回空列表")
        assertNotNull(cards.first().entityType, "卡片缺少 entityType")

        val entityCount = cards.sumOf { it.entities?.size ?: 0 }
        assertTrue(entityCount > 0, "所有卡片都没有 entities —— 结构可能变了")
        println("首页：${cards.size} 张卡片，共 $entityCount 个 entity")
    }

    @Test
    fun `公开接口 话题列表`() {
        if (!enabled) return
        val resp = client().api.dataList(
            url = "/topic/hotTagList", hotType = "total", recommend = 1
        ).executeSync()
        checkStatus(resp, "话题列表")
        val topics = resp.data
        assertTrue(!topics.isNullOrEmpty(), "话题列表为空")
        println("话题条数：${topics.size}")
    }

    // ─────────────── 登录态 ───────────────

    @Test
    fun `登录态 自检与业务接口`() {
        if (!enabled) return
        val (uid, name, token) = cred() ?: return
        val c = Client(session = Session(uid = uid, username = name, token = token))

        val info: Resp<LoginInfo> = c.api.checkLoginInfo().executeSync()
        checkStatus(info, "登录自检")
        val d = info.data
        assertNotNull(d, "自检 data 为空 —— 可能是「登录信息有误」（凭据失效）")
        assertEquals(uid, d.uid, "自检返回的 uid 与凭据不符")
        println("自检通过：uid=${d.uid} level=${d.level} adminType=${d.adminType}")

        val count = c.api.checkCount().executeSync()
        checkStatus(count, "未读计数")
        val cnt = count.data
        assertNotNull(cnt, "未读计数 data 为空")
        println("未读：badge=${cnt.badge} message=${cnt.message}")

        val feeds = c.api.getUserFeedList(uid = uid, page = 1).executeSync()
        checkStatus(feeds, "我的动态")
        val myFeeds = feeds.data
        assertTrue(!myFeeds.isNullOrEmpty(), "我的动态为空")
        println("我的动态条数：${myFeeds.size}")

        val collections = c.api.getCollectionList().executeSync()
        checkStatus(collections, "收藏夹")
        println("收藏夹数量：${collections.data?.size}")

        val profile = c.api.getUserProfile(uid = uid).executeSync()
        checkStatus(profile, "用户资料")
        val p = profile.data
        assertNotNull(p, "用户资料 data 为空")
        assertEquals(uid, p.uid)
        println("用户资料：${p.username} level=${p.level}")
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
        checkStatus(feeds, "我的动态（取测试 id）")
        val fid = feeds.data?.firstOrNull()?.id ?: fail("拿不到用于测试的动态 id")
        println("测试动态 id = $fid")

        val detail: Resp<Feed> = c.api2.getFeedContent(id = fid).executeSync()
        checkStatus(detail, "动态详情")
        val f = detail.data
        assertNotNull(f, "详情 data 为空")
        assertEquals(fid, f.id, "详情返回的 id 与请求不符")
        println("详情：id=${f.id} 点赞=${f.likenum} 回复=${f.replynum}")

        val replies = c.api2.getFeedContentReply(id = fid).executeSync()
        checkStatus(replies, "评论列表")
        println("评论条数：${replies.data?.size}")
    }
}
