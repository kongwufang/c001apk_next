package com.example.c001apk.desktop.core.crypto

import java.security.MessageDigest

/**
 * MD5 十六进制小写。
 *
 * 对应 Android 端 `TokenDeviceUtils.md5Hex` / `Utils.getMD5`。
 * Android 用 `android.util.Base64` + 自写 hex，这里用 JDK 原生实现，逐字节等价。
 */
fun ByteArray.md5Hex(): String {
    val digest = MessageDigest.getInstance("MD5").digest(this)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xff
        sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
    }
    return sb.toString()
}

fun String.md5Hex(): String = toByteArray(Charsets.UTF_8).md5Hex()

private val HEX = "0123456789abcdef".toCharArray()

/** 标准 Base64（无换行），等价 Android 的 `Base64.NO_WRAP` */
fun ByteArray.b64(): String = java.util.Base64.getEncoder().encodeToString(this)

/** 标准 Base64 解码，等价 Android 的 `Base64.DEFAULT`（容忍换行） */
fun b64Decode(s: String): ByteArray = java.util.Base64.getMimeDecoder().decode(s)
