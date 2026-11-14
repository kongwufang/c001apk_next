package com.example.c001apk.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Wire format adapted from kongwufang/szlm_res (MIT; see assets/third_party).
 * The local derivation namespace is public and independent of the former server's private seed.
 * Only the app-scoped SSAID is used as identity input; model, time and user settings cannot change it.
 */
internal object LocalDuidProtocol {
    const val URL = "https://auni.telecome.cn/a/mdna/report?v=8.7&t=m" +
        "&p=F9BD0FBDACFE80FCF34370FBCFEE1A98" +
        "&r=5bb7efdd4c794f0e9563317b7802b995&n=0&l=2&h=0"
    const val MAX_RESPONSE_BYTES = 64 * 1024
    private val xorKey = "xp7j@&!v5]2k#+3z{n9\$q^r%[8f1*d4e:c>0l?m/6)yb-=w}".toByteArray()
    private const val ALPHABET = "!#\$%&'()*+,-./0123456789:;<=>?@" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun fingerprint(rawSsaid: String): Map<String, String> {
        val ssaid = rawSsaid.trim().lowercase(Locale.ROOT)
        require(ssaid.matches(Regex("[0-9a-f]{16}")) && ssaid.any { it != '0' } &&
            ssaid != "9774d56d682e549c") { "应用 SSAID 不可用" }
        val base = hmac("c001apk-local-duid-v1".toByteArray(), "ssaid|$ssaid")
        fun stream(label: String, count: Int): ByteArray {
            val output = ByteArrayOutputStream()
            var index = 0
            while (output.size() < count) output.write(hmac(base, "$label|${index++}"))
            return output.toByteArray().copyOf(count)
        }
        fun hex(label: String, length: Int) = stream(label, (length + 1) / 2)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(length)
        fun uint32(bytes: ByteArray, offset: Int): Long = (offset until offset + 4)
            .fold(0L) { value, i -> (value shl 8) or (bytes[i].toLong() and 0xff) }
        val ts = stream("2cO", 8)
        val seconds = 1791105420L - uint32(ts, 0) % 31536000L
        val nanos = (uint32(ts, 4) % 1000000000L).toString().padStart(9, '0')
        return linkedMapOf(
            "sfOo" to hex("sfOo", 7).uppercase(Locale.ROOT),
            "foO" to stream("foO", 15).map { ALPHABET[(it.toInt() and 0xff) % ALPHABET.length] }.joinToString(""),
            "2cO" to "$seconds.$nanos",
            "w91" to hex("w91", 32).uppercase(Locale.ROOT),
            "gEd" to hex("gEd", 32),
            "6yY" to hex("6yY", 64),
        )
    }

    fun payload(template: String, ssaid: String): JsonObject =
        JsonParser.parseString(template).asJsonObject.apply {
            fingerprint(ssaid).forEach { (name, value) -> addProperty(name, value) }
        }

    fun encode(payload: JsonObject): ByteArray {
        // libdu/PHP style: compact UTF-8 JSON, unescaped Unicode, escaped forward slashes.
        val json = gson.toJson(payload).replace("/", "\\/").toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(6)
        try {
            DeflaterOutputStream(compressed, deflater).use { it.write(json) }
        } finally {
            deflater.end()
        }
        return xor(compressed.toByteArray())
    }

    /** Accept the upstream's compressed and plain-XOR JSON forms; never accept a zero/invalid ID. */
    fun decode(raw: ByteArray): String {
        if (raw.isEmpty() || raw.size > MAX_RESPONSE_BYTES) throw IOException("取号响应无效")
        val decoded = xor(raw)
        val json = try {
            InflaterInputStream(decoded.inputStream()).use { readBounded(it) }
        } catch (e: ResponseTooLargeException) {
            throw e
        } catch (_: IOException) {
            decoded
        }
        val id = try {
            JsonParser.parseString(json.toString(Charsets.UTF_8)).asJsonObject["n_a"]
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        } catch (_: RuntimeException) {
            null
        }
        if (id == null || !id.matches(Regex("DU[A-Za-z0-9_-]{34}")) || id.drop(2).all { it == '0' }) {
            throw IOException("上游未签发有效的数字联盟 ID")
        }
        return id
    }

    fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            if (output.size() + size > MAX_RESPONSE_BYTES) throw ResponseTooLargeException()
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }

    private class ResponseTooLargeException : IOException("取号响应过大")

    private fun xor(bytes: ByteArray) = ByteArray(bytes.size) {
        (bytes[it].toInt() xor xorKey[it % xorKey.size].toInt()).toByte()
    }

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray(Charsets.UTF_8))
    }
}
