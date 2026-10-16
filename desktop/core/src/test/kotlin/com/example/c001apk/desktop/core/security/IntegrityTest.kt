package com.example.c001apk.desktop.core.security

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 完整性自校验测试。
 *
 * 覆盖：
 *   * 正常清单 → 通过
 *   * 文件被改（哈希不符）→ 失败并指出具体文件
 *   * 文件被删 → 失败
 *   * 清单被篡改（改哈希但没重签）→ 验签失败
 *   * 未内嵌公钥 / 未找到清单 → 跳过（不阻塞开发构建）
 *   * canonicalPayload 的格式与构建端（Python make_integrity.py）一致
 */
class IntegrityTest {

    private val kp: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val pubB64: String =
        Base64.getEncoder().encodeToString(kp.public.encoded)

    private fun tempApp(vararg files: Pair<String, String>): Path {
        val dir = Files.createTempDirectory("c001apk-integrity-")
        files.forEach { (rel, content) ->
            val p = dir.resolve(rel)
            p.parent?.createDirectories()
            p.writeText(content)
        }
        return dir
    }

    /** 用与构建端相同的规范化格式生成并签名清单 */
    private fun writeManifest(dir: Path, key: KeyPair = kp) {
        val entries = Integrity.hashAll(dir).entries
            .sortedBy { it.key }
            .map { (path, sha) ->
                Integrity.Entry(path, sha, Files.size(dir.resolve(path)))
            }
        val manifest = Integrity.Manifest(1, "Ed25519", entries)
        val payload = Integrity.canonicalPayload(manifest)
        val sig = Signature.getInstance("Ed25519").apply {
            initSign(key.private)
            update(payload)
        }.sign()
        val sigB64 = Base64.getEncoder().encodeToString(sig)

        val json = buildString {
            appendLine("{")
            appendLine("  \"version\": 1,")
            appendLine("  \"algorithm\": \"Ed25519\",")
            appendLine("  \"signature\": \"$sigB64\",")
            appendLine("  \"entries\": [")
            entries.forEachIndexed { i, e ->
                val comma = if (i < entries.size - 1) "," else ""
                appendLine("    {\"path\": \"${e.path}\", \"sha256\": \"${e.sha256}\", \"size\": ${e.size}}$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
        dir.resolve(Integrity.MANIFEST_FILE_NAME).writeText(json)
    }

    @Test
    fun `正常清单校验通过`() {
        val dir = tempApp(
            "app/a.jar" to "hello",
            "app/b.jar" to "world",
            "runtime/lib/x.dll" to "dll-content",
        )
        writeManifest(dir)

        val r = Integrity.verify(dir, pubB64)
        assertTrue(r.ok, "应通过，实际：${r.reason}")
        assertEquals(3, r.checked, "应校验 3 个文件")
        assertTrue(r.mismatch.isEmpty() && r.missing.isEmpty())
    }

    @Test
    fun `文件被篡改时失败并指出文件`() {
        val dir = tempApp(
            "app/a.jar" to "hello",
            "app/b.jar" to "world",
        )
        writeManifest(dir)
        // 篡改其中一个文件
        dir.resolve("app/b.jar").writeText("TAMPERED")

        val r = Integrity.verify(dir, pubB64)
        assertFalse(r.ok, "被篡改应失败")
        assertTrue(r.mismatch.contains("app/b.jar"), "应指出 app/b.jar，实际 ${r.mismatch}")
    }

    @Test
    fun `文件缺失时失败`() {
        val dir = tempApp(
            "app/a.jar" to "hello",
            "app/b.jar" to "world",
        )
        writeManifest(dir)
        Files.delete(dir.resolve("app/a.jar"))

        val r = Integrity.verify(dir, pubB64)
        assertFalse(r.ok, "文件缺失应失败")
        assertTrue(r.missing.contains("app/a.jar"), "应指出缺失 app/a.jar，实际 ${r.missing}")
    }

    @Test
    fun `清单被篡改但未重签时验签失败`() {
        val dir = tempApp("app/a.jar" to "hello")
        writeManifest(dir)

        // 攻击者改了文件，并把清单里的哈希也改成新值（但没有私钥，无法重签）
        dir.resolve("app/a.jar").writeText("TAMPERED")
        val newHash = Integrity.sha256(dir.resolve("app/a.jar"))
        val mf = dir.resolve(Integrity.MANIFEST_FILE_NAME)
        val tampered = Files.readString(mf).replace(
            Regex("\"sha256\"\\s*:\\s*\"[0-9a-f]{64}\""),
            "\"sha256\": \"$newHash\"",
        )
        mf.writeText(tampered)

        val r = Integrity.verify(dir, pubB64)
        assertFalse(r.ok, "清单被篡改应失败")
        assertTrue(
            r.reason?.contains("签名") == true,
            "应报签名校验失败，实际：${r.reason}",
        )
    }

    @Test
    fun `公钥不匹配时验签失败`() {
        val dir = tempApp("app/a.jar" to "hello")
        writeManifest(dir)

        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val otherPub = Base64.getEncoder().encodeToString(other.public.encoded)

        val r = Integrity.verify(dir, otherPub)
        assertFalse(r.ok, "换了公钥应验签失败")
    }

    @Test
    fun `未内嵌公钥时跳过`() {
        val dir = tempApp("app/a.jar" to "hello")
        writeManifest(dir)
        val r = Integrity.verify(dir, "")
        assertTrue(r.ok && r.skipped, "开发构建应跳过")
    }

    @Test
    fun `没有清单时跳过`() {
        val dir = tempApp("app/a.jar" to "hello")
        val r = Integrity.verify(dir, pubB64)
        assertTrue(r.ok && r.skipped, "无清单应跳过")
    }

    @Test
    fun `命令行跳过开关生效`() {
        val dir = tempApp("app/a.jar" to "hello")
        writeManifest(dir)
        dir.resolve("app/a.jar").writeText("TAMPERED")   // 即使被改
        val r = Integrity.verify(dir, pubB64, listOf(Integrity.SKIP_FLAG))
        assertTrue(r.ok && r.skipped, "--skip-integrity-check 应跳过")
    }

    /**
     * 锁定 canonicalPayload 的格式 —— **必须与构建端 Python `make_integrity.py` 一致**。
     * 任何一侧改了格式，这个用例都会失败。
     */
    @Test
    fun `canonicalPayload 格式与构建端一致`() {
        val m = Integrity.Manifest(
            version = 1,
            algorithm = "Ed25519",
            entries = listOf(
                Integrity.Entry("app/b.jar", "b".repeat(64), 200),
                Integrity.Entry("app/a.jar", "a".repeat(64), 100),
            ),
        )
        val expected = buildString {
            append("version=1\n")
            append("algorithm=Ed25519\n")
            append("app/a.jar\t" + "a".repeat(64) + "\t100\n")   // 按 path 排序
            append("app/b.jar\t" + "b".repeat(64) + "\t200\n")
        }
        assertEquals(expected, String(Integrity.canonicalPayload(m), Charsets.UTF_8))
    }

    /** 发布构建里 `IntegrityPublicKey` 会被 CI 覆盖成真实公钥；此处只确认它存在且可读。 */
    @Test
    fun `内嵌公钥常量可读`() {
        val v = IntegrityPublicKey.BASE64
        // 仓库占位为空串；打包时由 CI 注入真实公钥
        assertTrue(v.isEmpty() || v.length > 40, "公钥格式可疑：len=${v.length}")
    }
}
