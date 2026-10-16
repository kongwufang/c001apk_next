package com.example.c001apk.desktop.core.auth

import com.example.c001apk.desktop.core.crypto.b64
import com.example.c001apk.desktop.core.crypto.md5Hex
import org.mindrot.jbcrypt.BCrypt
import java.util.Base64

/**
 * 酷安 `X-App-Token` v3 生成器。
 *
 * 与 Android 端 `TokenDeviceUtils.getTokenV3`（app/src/main/.../util/TokenDeviceUtils.kt）
 * 算法一致，并已用 **77 个真机抓包 token 全部复现验证**。
 *
 * ── 算法 ──
 * ```
 * off   = 4 * ((t + versionCode) % 100) + 128
 * seg   = KEY_BYTES[off, off + 128]        // KEY_BYTES = b64decode(KEY_B64)，930 字节
 * B2    = b64decode( seg 丢弃非 base64 字符 → 补齐到 4 的倍数 )
 * S     = packageName & B2 & md5hex(device) & t & versionCode   // 全程原始字节拼接
 * salt  = base64(hex(t) + "/" + md5hex(S))[:22]
 * key   = md5hex(base64(S))                // bcrypt 的 password（注意不是 md5hex(S)）
 * token = "v3" + base64(bcrypt(key, "$2y$04$" + salt))
 * ```
 *
 * ── 三个易错点（每一处都曾导致 token 被服务端拒为「请求验证错误」）──
 * 1. `off` 作用于 **解码后的 930 字节表**，不是 base64 文本。
 * 2. `seg` 切片后再做 base64 解码；`seg` 长度固定 128，但 **B2 长度可变**（1..96 字节），
 *    因为切片里可能含非 base64 字符。
 * 3. 故解码前 **必须丢弃非法字符并补齐到 4 的倍数**（等价 Python `base64.b64decode` 的宽松行为）。
 *    JVM 的 `Base64.getDecoder()` 是严格的，遇非法字符直接抛异常 —— 不处理就与真机不一致。
 *
 * ── 校验约束 ──
 * `versionCode` 必须与同一请求的 `X-App-Code` 一致，`device` 必须与 `X-App-Device` 一致。
 * 实测：二者不一致时服务端返回 HTTP 403；伪造/缺失 token 返回 `请求未验证`。
 *
 * KEY_B64 与骨架由 `_rev/win_ref/gen_v3kt.py` 程序化生成，请勿手改。
 */
object V3Token {

    const val APP_ID = "com.coolapk.market"

    /** libauth.so 中 930 字节的 KEY 表（base64 文本）。与 Android 端逐字符一致。 */
    private const val KEY_B64 =
        "VFRCVU9GUXNRMEVsTFVNa1dERWpRU0VzVXlFbUxETkFVeTFEUEZZdUl5MGlNVU01SXpCVUpGY3RNeWtoTVVRMUpTMURPU1V4TXpo" +
        "UUxWTXdWMDBzSXloU01UUXNXVEZEUkZFd1EwUlZNU1FrVVMwaktTTXRKRGhZTFZRa1VqRXpLU1FzTTBCVkxUUXdVaTAwTlNNc00y" +
        "QlhMVk1sSkMxRE5GbE5MQ1EwV1RFekxTUXhRMEVoTEVNbElqRXpLRmN3TXowbExpTkVWekVqUFNZdU5Ea2tMRk13Vml4VEtGa3RR" +
        "emhYTEZNc1dDNGpMU1l0VTJCVFRUQTBMRkV1TXpoVExqTWhJakZETUZNc0kwQlZMQ1FrVUN3ekpTSXNJekJTTUVNOFVDeEVMU0l0" +
        "STJCUUxVUW9VeTBqTEZNc0l5VWtNRlF3VmswdFJDeFlNVE1sSkM0ME5TRXdVMFJRTEZNaElpMGtOU013VTJCUUxqTmdVeXdqT0ZF" +
        "c05DeFRMRU13V0N4VUxTRXRJeVJUTEROQkl5MDBKRlpOTVVNc1VEQkVKRmd0TkMwa0xEUWtXQzFETUZNeFJDa2pMVk0wVml4VE1G" +
        "Z3hJeTBtTFZNeEpTMHpKRlF4TTJCUUxTUTRXVEJETUZNdUkwVWhUU3d6UEZndFJEQlJNRE5FVVMwak9GWXVKRGtsTVNRMFV6QXpO" +
        "U013UkRra01UTWtWU3hVTUZRdFJDeFlNVE5GSXl4RUxGWXNJelVoTGlRMFYwMHhOQ3hWTENNMFZ5NHpKRlV4TkN4VExGUTRWeTRq" +
        "WUZZeEpEVWpMVU13VXpGRFBGSXNNMEVsTERNc1ZEQTBPU011TkNrbUxFUWtWaXhFTVNKTkxUTW9VaXd6TEZjdFUwUlFMaVF3VlRG" +
        "REpTVXdVeWtqTVVNaEl5MHpJU1VzSTBFak1UTXdVUzFVTkZjeFJERWxMVVF0SlM0a05GSXNJemhaVFRCVFlGRXdReVJZTFNNbElT" +
        "NDBMRmdzVkRSWkxVUW9XVEJEUUZZdFJDeFdMak5FVkRCRE1Ga3RORFVsTEZRNFZTMVVKRmd3TXpSV0xEUTRVVTB3UXkwbUxVUTRX" +
        "UzFUTVNNc1V5VWlNVU5BVVN4VUxGWXVJMFJVTGlNc1dERkVMRk14STJCUUxWTTBXUzRrT0Zjd00wUlhMVk1vV0MwaktGRXhMQ00w" +
        "Vml4VE5GQXRSRFJXTFVRbEpDMVRKRkF3TkN4Z1lB"

    /** 解码后的 930 字节 KEY 表 */
    private val KEY_BYTES: ByteArray = Base64.getDecoder().decode(KEY_B64)

    private val B64_ALPHABET: Set<Char> =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toSet()

    /** 生成 token。device 与 versionCode 必须与同一请求的请求头一致。 */
    fun gen(device: String, timestampSeconds: Long, versionCode: Long,
            packageName: String = APP_ID): String {
        val off = (4 * ((timestampSeconds + versionCode) % 100) + 128).toInt()

        val b2 = decodeLenientBase64(KEY_BYTES.copyOfRange(off, off + 128))

        val s = packageName.toByteArray(Charsets.UTF_8)
            .plus("&".toByteArray(Charsets.UTF_8))
            .plus(b2)
            .plus("&".toByteArray(Charsets.UTF_8))
            .plus(device.toByteArray(Charsets.UTF_8).md5Hex().toByteArray(Charsets.UTF_8))
            .plus("&".toByteArray(Charsets.UTF_8))
            .plus(timestampSeconds.toString().toByteArray(Charsets.UTF_8))
            .plus("&".toByteArray(Charsets.UTF_8))
            .plus(versionCode.toString().toByteArray(Charsets.UTF_8))

        val salt = Base64.getEncoder()
            .encodeToString(
                (timestampSeconds.toString(16) + "/" + s.md5Hex()).toByteArray(Charsets.UTF_8)
            )
            .substring(0, 22)

        // jbcrypt 不认 $2y 盐：用 $2a 生成后改写前缀（哈希本体相同，与官方实现逐字节一致）
        val hash = "\$2y" + BCrypt.hashpw(
            Base64.getEncoder().encodeToString(s).toByteArray(Charsets.UTF_8).md5Hex(),
            "\$2a\$04\$" + salt
        ).substring(3)

        return "v3" + hash.toByteArray(Charsets.UTF_8).b64()
    }

    /**
     * 宽松 base64 解码：丢弃非 base64 字母表字符，补齐到 4 的倍数后解码。
     * 等价 Python `base64.b64decode` 的容错行为。长度为 4k+1 时返回空数组。
     */
    private fun decodeLenientBase64(raw: ByteArray): ByteArray {
        val sb = StringBuilder(raw.size)
        for (b in raw) {
            val c = (b.toInt() and 0xff).toChar()
            if (c in B64_ALPHABET) sb.append(c)
        }
        while (sb.isNotEmpty() && sb[sb.length - 1] == '=') sb.setLength(sb.length - 1)
        when (sb.length % 4) {
            0 -> {}
            1 -> return ByteArray(0)
            2 -> sb.append("==")
            3 -> sb.append('=')
        }
        return Base64.getDecoder().decode(sb.toString())
    }
}
