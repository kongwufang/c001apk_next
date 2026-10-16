package com.example.c001apk.desktop.core.net

import com.example.c001apk.desktop.core.auth.V3Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 请求头与 Cookie 组装测试（离线，不发网络请求）。
 *
 * 校验 `AddCookiesInterceptor` 的等价逻辑 —— Android 端 `AddCookiesInterceptor.kt` 共 14 个
 * 固定头 + 可选 Cookie，这里逐项核对，防止移植时漏项/改名。
 */
class SessionHeadersTest {

    /** 真机抓包向量（来源 _rev har）：用它能确认头里的 token 与设备串口径一致 */
    private val device =
        "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4Ay" +
            "OpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZT" +
            "b0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR"

    private val expectToken =
        "v3JDJ5JDA0JE5tRmlOVEJoWlRJdllqQTFNak5sT09xcFVDUFhGRGxFUkpORnI1ZHkzR1psVkVEcWdoWjU2"

    @Test
    fun `游客态：14 个固定头齐全且无 Cookie`() {
        val s = Session(deviceCode = device)
        val h = s.headers(versionCode = "2609151", timestampSeconds = 1790249698L)

        val fixed = listOf(
            "User-Agent", "X-Requested-With", "X-Sdk-Int", "X-Sdk-Locale", "X-App-Id",
            "X-App-Token", "X-App-Version", "X-App-Code", "X-Api-Version", "X-App-Device",
            "X-Dark-Mode", "X-App-Channel", "X-App-Mode", "X-App-Supported", "Referer",
        )
        fixed.forEach { k -> assertNotNull(h[k], "缺少请求头 $k") }

        assertNull(h["Cookie"], "未登录不应带 Cookie")
        assertEquals(device, h["X-App-Device"])
        assertEquals("2609151", h["X-App-Code"])
        assertEquals("2609151", h["X-App-Supported"], "X-App-Supported 必须与 X-App-Code 一致")
        assertTrue(h["User-Agent"]!!.startsWith("Dalvik/"), "UA 必须以 Dalvik/ 开头（服务端要求）")
    }

    @Test
    fun `头里的 X-App-Token 与真机向量一致`() {
        val s = Session(deviceCode = device)
        val h = s.headers(versionCode = "2609151", timestampSeconds = 1790249698L)
        assertEquals(expectToken, h["X-App-Token"])
        // 与直接调用签名函数一致
        assertEquals(V3Token.gen(device, 1790249698L, 2609151L), h["X-App-Token"])
    }

    @Test
    fun `登录态：Cookie 为 uid username token 三字段`() {
        val s = Session(deviceCode = device)
        s.login(uid = "32864090", username = "%E6%99%9A%E4%B8%8A%E5%88%B7%E6%9C%BA", token = "FAKE_TOKEN")
        assertTrue(s.isLogin)

        val h = s.headers(versionCode = "2609291", timestampSeconds = 1790249698L)
        val ck = h["Cookie"]
        assertNotNull(ck)
        assertTrue(ck.startsWith("uid=32864090; username="), "Cookie 顺序/前缀不符：$ck")
        assertTrue(ck.endsWith("; token=FAKE_TOKEN"), "Cookie 结尾不符：$ck")
    }

    @Test
    fun `游客态可传 SESSID`() {
        val s = Session(deviceCode = device)
        val h = s.headers(versionCode = "2609291", timestampSeconds = 1790249698L, sessId = "ABC123")
        assertEquals("SESSID=ABC123", h["Cookie"])
    }

    @Test
    fun `登出后不再带 Cookie`() {
        val s = Session(deviceCode = device)
        s.login("1", "u", "t")
        assertNotNull(s.headers(versionCode = "2609291", timestampSeconds = 1L)["Cookie"])
        s.logout()
        assertNull(s.headers(versionCode = "2609291", timestampSeconds = 1L)["Cookie"])
    }
}
