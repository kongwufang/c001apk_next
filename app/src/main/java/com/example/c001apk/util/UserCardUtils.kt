package com.example.c001apk.util

import java.util.Locale

/** 个人主页展示文案，全部 @JvmStatic 便于在 DataBinding 表达式里调用 */
object UserCardUtils {

    /** 0 = 女，1 = 男，其他给空串 */
    @JvmStatic
    fun genderText(gender: Int?): String = when (gender) {
        0 -> "女"
        1 -> "男"
        else -> ""
    }

    /** 「广东 深圳」，缺一取另一个 */
    @JvmStatic
    fun regionText(province: String?, city: String?): String {
        val p = province.orEmpty()
        val c = city.orEmpty()
        return when {
            p.isEmpty() -> c
            c.isEmpty() -> p
            p == c -> p
            else -> "$p $c"
        }
    }

    /** 1207831 -> 120.8万 */
    @JvmStatic
    fun countText(count: String?): String {
        val n = count?.toLongOrNull() ?: return count.orEmpty()
        return if (n >= 10000) String.format(Locale.US, "%.1f万", n / 10000.0)
        else n.toString()
    }

    /** 装备条右侧文案（左侧已有「他的装备」，这里只补数量，别重复「装备」二字） */
    @JvmStatic
    fun equipText(count: Int?): String =
        if (count == null || count <= 0) "" else "${count} 件"

    /** 点评星级：star 为 1~5，补空心星 */
    @JvmStatic
    fun starText(star: Int?): String {
        val s = (star ?: 0).coerceIn(0, 5)
        return "\u2605".repeat(s) + "\u2606".repeat(5 - s)
    }

    /** 点评得分：rating_score_old 是 10 分制 */
    @JvmStatic
    fun scoreText(score: Double?): String =
        if (score == null || !score.isFinite() || score <= 0) ""
        else "${score.toBigDecimal().stripTrailingZeros().toPlainString()} 分"

    /** 「平均 8.4」这类前缀文案 */
    @JvmStatic
    fun prefixText(prefix: String, text: String?): String =
        if (text.isNullOrEmpty()) "" else prefix + text

    /** 优点 / 缺点 / 总结 */
    @JvmStatic
    fun commentText(label: String, text: String?): String =
        if (text.isNullOrEmpty()) "" else "${label}：${text}"
}
