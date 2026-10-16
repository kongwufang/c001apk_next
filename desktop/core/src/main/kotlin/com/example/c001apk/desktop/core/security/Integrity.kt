package com.example.c001apk.desktop.core.security

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * 完整性自校验：确认安装目录下的文件没有被篡改。
 *
 * ── 机制 ──
 * 构建时（CI）：
 *   1. 遍历 jpackage 产出的应用镜像，对**每个文件**算 SHA-256；
 *   2. 用发行私钥（Ed25519）对该清单签名；
 *   3. 把「清单 + 签名」写入 `integrity.sig`，连同公钥一起放进应用目录，
 *      同步生成 `IntegrityPublicKey.kt`（公钥硬编码进程序）。
 *
 * 启动时（本类）：
 *   1. 读 `integrity.sig`；
 *   2. 用内嵌公钥验签 —— 验不过直接判定被篡改（防止攻击者顺手改哈希）；
 *   3. 重新计算所有文件哈希并与清单比对。
 *
 * ── 强度边界（务必知悉）──
 * 这是**客户端自校验**，能防住「顺手改文件/被第三方魔改后重新分发」，
 * 但防不住有心人直接 patch 校验代码本身或替换内嵌公钥
 * —— 那是客户端校验的固有极限，真正的边界需要服务端参与或 TPM。
 *
 * ── 逃生口 ──
 * 命令行加 `--skip-integrity-check` 可跳过（构建流程出问题时的应急手段）。
 * 开发环境（未生成清单）自动跳过，不阻塞本地调试。
 */
object Integrity {

    const val MANIFEST_FILE_NAME = "integrity.sig"
    const val SKIP_FLAG = "--skip-integrity-check"

    /** 清单里每个文件的记录 */
    data class Entry(val path: String, val sha256: String, val size: Long)

    data class Manifest(val version: Int, val algorithm: String, val entries: List<Entry>)

    data class Result(
        val ok: Boolean,
        val skipped: Boolean = false,
        val reason: String? = null,
        val checked: Int = 0,
        val mismatch: List<String> = emptyList(),
        val missing: List<String> = emptyList(),
        val extra: List<String> = emptyList(),
    )

    /**
     * 校验安装目录。
     *
     * @param appDir 应用目录（含 jar 与 `integrity.sig`）。桌面端由 `app/` 与 `runtime/` 两层组成，
     *               一般传安装根目录（jpackage 的 `--app-image` 输出目录）即可，本方法递归处理。
     * @param publicKeyBase64 内嵌公钥（X.509 SubjectPublicKeyInfo 的 base64）。
     *               为空 = 开发构建，直接跳过。
     */
    fun verify(appDir: Path, publicKeyBase64: String?, args: List<String> = emptyList()): Result {
        if (SKIP_FLAG in args) {
            return Result(ok = true, skipped = true, reason = "命令行指定 $SKIP_FLAG")
        }
        if (publicKeyBase64.isNullOrBlank()) {
            return Result(ok = true, skipped = true, reason = "开发构建：未内嵌公钥")
        }

        val manifestPath = appDir.resolve(MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestPath)) {
            return Result(ok = true, skipped = true, reason = "未找到 $MANIFEST_FILE_NAME（开发构建）")
        }

        val raw = Files.readString(manifestPath)
        val manifest = parse(raw)
        val signatureB64 = extractSignature(raw)
            ?: return Result(false, reason = "清单缺少 signature 字段")

        // ① 验签：确认清单本身没被改
        val payload = canonicalPayload(manifest)
        if (!verifySignature(payload, signatureB64, publicKeyBase64)) {
            return Result(false, reason = "清单签名校验失败（清单已被篡改或公钥不匹配）")
        }

        // ② 逐文件比对哈希
        val expected = manifest.entries.associateBy { it.path }
        val actual = hashAll(appDir)

        val mismatch = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for ((path, e) in expected) {
            val a = actual[path]
            if (a == null) {
                missing.add(path)
            } else if (a != e.sha256) {
                mismatch.add(path)
            }
        }
        val extra = actual.keys.filter { it !in expected }

        val ok = mismatch.isEmpty() && missing.isEmpty()
        return Result(
            ok = ok,
            reason = if (ok) null else "有文件与清单不一致",
            checked = expected.size,
            mismatch = mismatch,
            missing = missing,
            extra = extra,
        )
    }

    // ─────────────── 清单解析 / 规范化 ───────────────

    /**
     * 清单格式（自写极简 JSON，避免依赖顺序问题；构建端由 Python 生成）：
     * ```json
     * {
     *   "version": 1,
     *   "algorithm": "Ed25519",
     *   "signature": "base64…",
     *   "entries": [{"path":"app/x.jar","sha256":"…","size":123}, …]
     * }
     * ```
     */
    internal fun parse(raw: String): Manifest {
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toInt() ?: 1
        val algorithm = Regex("\"algorithm\"\\s*:\\s*\"([^\"]+)\"").find(raw)
            ?.groupValues?.get(1) ?: "Ed25519"
        val entries = mutableListOf<Entry>()
        // 逐条匹配 entries 里的对象
        val entryRe = Regex(
            "\\{\\s*\"path\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*" +
                "\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"\\s*,\\s*" +
                "\"size\"\\s*:\\s*(\\d+)\\s*\\}"
        )
        for (m in entryRe.findAll(raw)) {
            entries.add(Entry(m.groupValues[1], m.groupValues[2].lowercase(), m.groupValues[3].toLong()))
        }
        return Manifest(version, algorithm, entries)
    }

    internal fun extractSignature(raw: String): String? =
        Regex("\"signature\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").find(raw)?.groupValues?.get(1)

    /**
     * 生成**待签名载荷**：与构建端约定一致的规范化文本。
     * 只包含版本、算法与按 path 排序的条目，不含 signature 本身。
     */
    internal fun canonicalPayload(m: Manifest): ByteArray {
        val sb = StringBuilder()
        sb.append("version=").append(m.version).append('\n')
        sb.append("algorithm=").append(m.algorithm).append('\n')
        m.entries.sortedBy { it.path }.forEach {
            sb.append(it.path).append('\t').append(it.sha256).append('\t').append(it.size).append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ─────────────── 哈希 / 验签 ───────────────

    /** 递归计算目录下所有常规文件的 SHA-256；key 为相对目录的 path（用 '/' 分隔） */
    internal fun hashAll(root: Path, excludeNames: Set<String> = setOf(MANIFEST_FILE_NAME)): Map<String, String> {
        val files = mutableListOf<Path>()
        Files.walk(root).use { st ->
            st.filter { it.isRegularFile() }.forEach { p ->
                val rel = p.relativeTo(root).toString().replace('\\', '/')
                if (rel.substringAfterLast('/') !in excludeNames) files.add(p)
            }
        }

        val out = java.util.concurrent.ConcurrentHashMap<String, String>()
        val pool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        )
        try {
            files.forEach { p ->
                pool.submit {
                    val rel = p.relativeTo(root).toString().replace('\\', '/')
                    out[rel] = sha256(p)
                }
            }
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.MINUTES)
        } finally {
            pool.shutdownNow()
        }
        return out
    }

    internal fun sha256(p: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(p).use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val d = md.digest()
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            val v = b.toInt() and 0xff
            sb.append("0123456789abcdef"[v ushr 4]).append("0123456789abcdef"[v and 0x0f])
        }
        return sb.toString()
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val der = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(der))
    }

    private fun verifySignature(payload: ByteArray, signatureB64: String, publicKeyBase64: String): Boolean =
        runCatching {
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(decodePublicKey(publicKeyBase64))
            sig.update(payload)
            sig.verify(Base64.getDecoder().decode(signatureB64))
        }.getOrDefault(false)
}
