package com.example.c001apk.desktop.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * v3 token 黄金向量测试 —— 向量全部来自**真机抓包**（_rev/har/*.har）。
 *
 * 向量可信性：token 的 bcrypt salt 明文形如 `hex(t) + "/" + md5(S)`，
 * 其中 t 就是签名当时的 Unix 秒，因此可从每个真机 token **反解出它自己的 timestamp**，
 * 再用 (device, timestamp, versionCode) 重算并断言与真机 token 逐字节相同。
 *
 * 这同时验证了 `X-App-Device` / `X-App-Code` 必须与签名参数一致这一服务端校验核心。
 *
 * 文件由 _rev/win_ref/make_golden_test.py 程序化生成，请勿手改。
 */
class V3TokenGoldenTest {

    private data class Vec(
        val name: String,
        val device: String,
        val ts: Long,
        val code: Long,
        val expect: String,
        val src: String,
    )

    private val vectors = listOf(
        Vec(
            name = "向量 01",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1790249698L,
            code = 2609151L,
            expect = "v3JDJ5JDA0JE5tRmlOVEJoWlRJdllqQTFNak5sT09xcFVDUFhGRGxFUkpORnI1ZHkzR1psVkVEcWdoWjU2",
            src = "cover.har",
        ),
        Vec(
            name = "向量 02",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1790249393L,
            code = 2609151L,
            expect = "v3JDJ5JDA0JE5tRmlOVEE1WWpFdk56VmpZbVZoWk8yWDR4U0EwTHhJWWxKczV2ckxzbTZEUHl1N0NCSWUu",
            src = "edit.har",
        ),
        Vec(
            name = "向量 03",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1790249383L,
            code = 2609151L,
            expect = "v3JDJ5JDA0JE5tRmlOVEE1WVRjdlpqY3hZV0l5WmVkUTBvSmltd0cvMUdKakpqb3ExVVRVYjFSVnRJcVNx",
            src = "edit.har",
        ),
        Vec(
            name = "向量 04",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1790249373L,
            code = 2609151L,
            expect = "v3JDJ5JDA0JE5tRmlOVEE1T1dRdlpHSTBaRGN5WWVYQ2MzSHdWdGR5WG1pYW1HbzNxQWRzWGZtaHBsRHlL",
            src = "edit.har",
        ),
        Vec(
            name = "向量 05",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789277823L,
            code = 2607021L,
            expect = "v3JDJ5JDA0JE5tRmhOak0yTjJZdk5EQmxaamhsTnVYekEzcTFqZmR1M1kzVDFkc21yLkhWS3NJNEc3bU9l",
            src = "open.e.kuaishou.com_2026_09_13_13_37_53.har",
        ),
        Vec(
            name = "向量 06",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789277820L,
            code = 2607021L,
            expect = "v3JDJ5JDA0JE5tRmhOak0yTjJNdk1UbGtZVFpsWk9heE1JSk1mYlVkMjNTYzJVMFQ5ZEpPT1JRRk9kVmZ5",
            src = "open.e.kuaishou.com_2026_09_13_13_37_53.har",
        ),
        Vec(
            name = "向量 07",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789277788L,
            code = 2607021L,
            expect = "v3JDJ5JDA0JE5tRmhOak0yTldNdk1EUmhPV0UzWmVyMVNubVZZOEE1YnBtNXAvVGphdDh5ZVBIZVFrTjdL",
            src = "open.e.kuaishou.com_2026_09_13_13_37_53.har",
        ),
        Vec(
            name = "向量 08",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789277785L,
            code = 2607021L,
            expect = "v3JDJ5JDA0JE5tRmhOak0yTlRrdllqZzJNV1UwWk9OV2xwd2xZOEdLMzloZEoyS01KakhnazhSSkJKMjMy",
            src = "open.e.kuaishou.com_2026_09_13_13_37_53.har",
        ),
        Vec(
            name = "向量 09",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789278434L,
            code = 2608212L,
            expect = "v3JDJ5JDA0JE5tRmhOak00WlRJdk9XVXpaVGsxTU8xV05uRkpkOVFubENZVkpyZGNHeG1QYU16M0RTQ3dp",
            src = "static.coolapk.com_2026_09_13_13_48_07.har",
        ),
        Vec(
            name = "向量 10",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789289089L,
            code = 2608212L,
            expect = "v3JDJ5JDA0JE5tRmhOall5T0RFdlptSTROamxtTWVqVEFFTC9pUmRtTWxPUTUvdDU4VTJjU2dISXFJdnFT",
            src = "static.coolapk.com_2026_09_13_16_44_57.har",
        ),
        Vec(
            name = "向量 11",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789748791L,
            code = 2608212L,
            expect = "v3JDJ5JDA0JE5tRmhaRFkyTXpjdlpEZGpZelV5TU9LTmZuWUo0MTJvNzB0LlRuNi5XcTVCRVBnMmdtTUFH",
            src = "119.28.28.89_2026_09_19_00_26_48.har",
        ),
        Vec(
            name = "向量 12",
            device = "lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR",
            ts = 1789748192L,
            code = 2608212L,
            expect = "v3JDJ5JDA0JE5tRmhaRFl6WlRBdllXSTVOR0ZqWU9NVUtNY0h3YllZTEl4bENnNmtWbWw1SjFMWkdMWVQu",
            src = "account.coolapk.com_2026_09_19_00_16_40.har",
        ),
    )

    @Test
    fun `真机黄金向量全部复现`() {
        assertEquals(12, vectors.size, "向量数不符")

        val failed = mutableListOf<String>()
        vectors.forEach { v ->
            val got = V3Token.gen(v.device, v.ts, v.code)
            if (got != v.expect) {
                failed += "  ${v.name} (${v.src})\n    期望=${v.expect}\n    实得=$got"
            }
        }

        println("── v3 token 黄金向量校验 ──")
        println("  总数 : ${vectors.size}")
        println("  通过 : ${vectors.size - failed.size}")
        println("  失败 : ${failed.size}")
        failed.forEach { println(it) }

        assertEquals(0, failed.size, "有 ${failed.size} 个向量未复现：\n" + failed.joinToString("\n"))
    }

    @Test
    fun `同一秒同版本号结果稳定`() {
        val v = vectors.first()
        assertEquals(V3Token.gen(v.device, v.ts, v.code), V3Token.gen(v.device, v.ts, v.code))
    }

    @Test
    fun `device 或 versionCode 不一致则结果不同`() {
        val v = vectors.first()
        val base = V3Token.gen(v.device, v.ts, v.code)
        check(base != V3Token.gen(v.device + "x", v.ts, v.code)) { "device 变化后 token 不应相同" }
        check(base != V3Token.gen(v.device, v.ts, v.code + 1)) { "versionCode 变化后 token 不应相同" }
    }
}
