package com.example.c001apk.desktop.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 行内 HTML 解析测试。向量取自**真实接口响应**（已确认 `message` 字段的形态）。
 */
class HtmlTextTest {

    @Test
    fun `真实动态正文：标签被剥离，链接文字保留`() {
        // 实测样本（/v6/main/indexV8 的 message 字段）
        val html = "我一直好奇一个问题，当年为了兼顾续航上 1.5k 的屏幕，现在电池都这么大了，" +
            "为什么还 1.5k？ <a class=\"feed-link-tag\" href=\"/t/%E9%85%B7%E5%AE%89%E5%A4%9C" +
            "%E8%AF%9D?type=0\">#酷安夜话#</a>"

        val runs = HtmlText.parse(html)
        val plain = runs.joinToString("") { it.text }

        assertTrue("<a" !in plain, "不应残留标签：$plain")
        assertTrue("</a>" !in plain, "不应残留闭合标签")
        assertTrue("class=" !in plain, "不应残留属性")
        assertTrue(plain.contains("#酷安夜话#"), "链接文字应保留：$plain")
        assertTrue(plain.startsWith("我一直好奇"), "开头文本应保留")

        // 链接片段应带上 href
        val link = runs.firstOrNull { it.href != null }
        assertTrue(link != null, "应识别出链接片段")
        assertEquals("#酷安夜话#", link.text)
        assertTrue(link.href!!.startsWith("/t/"), "href 应被提取：${link.href}")
    }

    @Test
    fun `多个链接与普通文本交替`() {
        val html = "到了兄弟们 <a class=\"feed-link-tag\" href=\"/t/A?type=0\">#一加12#</a>" +
            " <a class=\"feed-link-tag\" href=\"/t/B?type=0\">#一加15#</a>" +
            " <a class=\"feed-link-tag\" href=\"/t/C?type=0\">#小米18ProMax#</a>"

        val runs = HtmlText.parse(html)
        val links = runs.filter { it.href != null }
        assertEquals(3, links.size, "应识别 3 个链接，实际 ${links.map { it.text }}")
        assertEquals(listOf("#一加12#", "#一加15#", "#小米18ProMax#"), links.map { it.text })
        assertEquals(listOf("/t/A?type=0", "/t/B?type=0", "/t/C?type=0"), links.map { it.href })
        assertEquals("到了兄弟们 ", runs.first().text.stripTrailing().let { runs.first().text })
    }

    @Test
    fun `实体被解码`() {
        assertEquals("a & b", HtmlText.toPlainText("a &amp; b"))
        assertEquals("<div>", HtmlText.toPlainText("&lt;div&gt;"))
        assertEquals("引号\"与'", HtmlText.toPlainText("引号&quot;与&#39;"))
        assertEquals("emoji \uD83D\uDE00", HtmlText.toPlainText("emoji &#x1F600;"))
        assertEquals("空格\u00A0here", HtmlText.toPlainText("空格&nbsp;here"))
    }

    @Test
    fun `br 与 p 转为换行`() {
        assertEquals("第一行\n第二行", HtmlText.toPlainText("第一行<br>第二行"))
        assertEquals("第一行\n第二行", HtmlText.toPlainText("第一行<br/>第二行"))
        assertEquals("甲\n乙", HtmlText.toPlainText("<p>甲</p><p>乙</p>").trim())
    }

    @Test
    fun `未知标签被丢弃且不粘连词语`() {
        val plain = HtmlText.toPlainText("前面<span class=\"x\">中间</span>后面")
        assertTrue("<" !in plain, "不应残留标签：$plain")
        assertTrue(plain.contains("前面"), plain)
        assertTrue(plain.contains("中间"), plain)
        assertTrue(plain.contains("后面"), plain)
    }

    @Test
    fun `非法尖括号不会吞掉内容或死循环`() {
        val plain = HtmlText.toPlainText("a < b 且 c > d")
        assertTrue(plain.contains("a"), plain)
        assertTrue(plain.contains("b"), plain)
        assertTrue(plain.contains("d"), plain)
    }

    @Test
    fun `properties 风格的 a 标签也支持单引号属性`() {
        val runs = HtmlText.parse("<a href='/t/X?type=0'>#话题#</a>")
        assertEquals(1, runs.size)
        assertEquals("/t/X?type=0", runs[0].href)
        assertEquals("#话题#", runs[0].text)
    }

    @Test
    fun `空输入返回空`() {
        assertTrue(HtmlText.parse(null).isEmpty())
        assertTrue(HtmlText.parse("").isEmpty())
        assertEquals("", HtmlText.toPlainText(null))
    }

    @Test
    fun `纯文本原样返回`() {
        val s = "这是一条没有任何标签的动态，包含标点，和 English words。"
        assertEquals(s, HtmlText.toPlainText(s))
        assertEquals(1, HtmlText.parse(s).size)
    }
}
