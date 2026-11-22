package com.example.c001apk.util.emoji

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * Extracts a downloaded pack into `root/v<version>` after checking every entry.
 *
 * The caller verifies the archive's size and SHA-256 before calling this; here the archive is
 * untrusted input too: entry names are limited to `index.json` and WebP files directly under `images`, and
 * both per-entry and total output are bounded so a hostile archive cannot fill the disk.
 */
internal object EmojiPackInstaller {
    const val MAX_PACK_BYTES = 8L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 2L * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private val IMAGE_ENTRY = Regex("images/[a-z0-9][a-z0-9_.-]{0,63}\\.webp")

    fun install(archive: File, expectedVersion: Int, root: File): Pair<File, EmojiPack> {
        require(archive.isFile && archive.length() in 1..MAX_PACK_BYTES) { "bad archive size" }
        require(root.isDirectory || root.mkdirs()) { "no pack directory" }
        val target = File(root, "v$expectedVersion")
        val staging = File(root, ".install-$expectedVersion")
        check(staging.deleteRecursively() && staging.mkdirs()) { "cannot create staging directory" }
        try {
            val index = extract(archive, staging)
            val pack = EmojiPack.parse(index) ?: throw IOException("invalid index")
            check(pack.version == expectedVersion) { "pack version mismatch" }
            for ((code, path) in pack.files) {
                val file = File(staging, path)
                check(file.isFile && file.length() > 0) { "missing image for $code" }
            }
            File(staging, "index.json").writeText(index)
            check(target.deleteRecursively() && staging.renameTo(target)) { "cannot publish pack directory" }
            return target to pack
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extract(archive: File, target: File): String {
        ZipFile(archive).use { zip ->
            check(zip.size() <= MAX_ENTRIES) { "too many entries" }
            var index: String? = null
            var total = 0L
            for (entry in zip.entries()) {
                check(!entry.isDirectory) { "unexpected directory" }
                val name = entry.name
                check(name == "index.json" || IMAGE_ENTRY.matches(name)) { "unexpected entry" }
                val bytes = zip.getInputStream(entry).use { readBounded(it, min(MAX_ENTRY_BYTES, MAX_PACK_BYTES - total)) }
                total += bytes.size
                if (name == "index.json") {
                    check(index == null) { "duplicate index" }
                    index = String(bytes, Charsets.UTF_8)
                } else {
                    val file = File(target, name)
                    check(file.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                        "entry escapes pack"
                    }
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
            }
            return index ?: throw IOException("missing index")
        }
    }

    private fun readBounded(input: java.io.InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var read = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            read += count
            check(read <= limit) { "entry too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
