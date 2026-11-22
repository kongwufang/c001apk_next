package com.example.c001apk.util.emoji

import android.app.Application
import com.example.c001apk.MyApplication
import com.example.c001apk.ui.feed.reply.emoji.EmojiPanels
import com.example.c001apk.util.PrefManager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EmojiPackTest {
    @get:Rule val folder = TemporaryFolder()

    @Before fun setUp() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        MyApplication.context.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    private val index = """{"schema":1,"version":3,"count":2,"groups":[{"id":"default","title":"默认","codes":["[哈哈]"]},{"id":"niuniu","title":"牛牛","codes":["[牛牛点赞]"]}],"files":{"[哈哈]":"images/a.webp","[牛牛点赞]":"images/b.webp"}}"""

    private fun zip(index: String, files: Map<String, ByteArray> = mapOf("images/a.webp" to byteArrayOf(1), "images/b.webp" to byteArrayOf(2))): File {
        val target = folder.newFile("pack-${System.nanoTime()}.zip")
        ZipOutputStream(target.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("index.json"))
            out.write(index.toByteArray())
            out.closeEntry()
            files.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return target
    }

    @Test fun installsAndValidatesAWellFormedPack() {
        val context = MyApplication.context
        val root = EmojiPackStore.root(context)
        val (dir, pack) = EmojiPackInstaller.install(zip(index), 3, root)
        assertEquals(3, pack.version)
        assertEquals(listOf("[哈哈]", "[牛牛点赞]"), pack.codes)
        assertTrue(File(dir, "images/a.webp").isFile)
        assertEquals(pack, EmojiPackStore.load(context))
        assertEquals(listOf("最近", "默认", "牛牛"), EmojiPanels.titles())
        assertEquals(listOf(listOf("[哈哈]")), EmojiPanels.tabs(listOf("[哈哈]", "[未知]")).first())
        assertEquals(listOf(27, 1), EmojiPanels.pages(List(28) { "[$it]" }).map { it.size })

        val nextIndex = index.replace("\"version\":3", "\"version\":4")
        val oversized = zip(nextIndex, mapOf("images/a.webp" to ByteArray(EmojiPackInstaller.MAX_ENTRY_BYTES.toInt() + 1)))
        assertNull(runCatching { EmojiPackInstaller.install(oversized, 4, root) }.getOrNull())
        assertEquals(pack, EmojiPackStore.load(context))
    }

    @Test fun rejectsZipSlipEntryNames() {
        val bad = zip(index, mapOf("images/../../evil.webp" to byteArrayOf(1)))
        val root = folder.newFolder("root")
        runCatching { EmojiPackInstaller.install(bad, 3, root) }.onSuccess { throw AssertionError("accepted") }
    }

    @Test fun rejectsMissingImageOrVersionMismatch() {
        val root = folder.newFolder("root")
        val missing = zip(index, mapOf("images/a.webp" to byteArrayOf(1)))
        assertNull(runCatching { EmojiPackInstaller.install(missing, 3, root) }.getOrNull())
        val wrongVersion = zip(index)
        assertNull(runCatching { EmojiPackInstaller.install(wrongVersion, 4, root) }.getOrNull())
    }

    @Test fun parserRejectsDuplicateCodesAndUnsafePaths() {
        assertNotNull(EmojiPack.parse(index))
        assertNull(EmojiPack.parse(index.replace("[牛牛点赞]", "[哈哈]")))
        assertNull(EmojiPack.parse(index.replace("[哈哈]", "哈哈")))
        assertNull(EmojiPack.parse(index.replace("images/a.webp", "../a.webp")))
        assertNull(EmojiPack.parse(index.replace("\"count\":2", "\"count\":3")))
        assertNull(EmojiPack.parse("not json"))
    }

    @Test fun releaseMetadataRequiresHttpsHashAndSaneSize() {
        val good = """{"schema":1,"version":2,"url":"https://cdn.example.com/p.zip","sha256":"${"ab".repeat(32)}","sizeBytes":4096,"count":183}"""
        assertEquals(2, EmojiPackRelease.parse(good)?.version)
        assertNull(EmojiPackRelease.parse(good.replace("https://", "http://")))
        assertNull(EmojiPackRelease.parse(good.replace("cdn.example.com", "user:pass@cdn.example.com")))
        assertNull(EmojiPackRelease.parse(good.replace("ab".repeat(32), "zz")))
        assertNull(EmojiPackRelease.parse(good.replace("4096", "0")))
    }

    @Test fun storeLoadsTheNewestValidFolderAndIgnoresABrokenOne() {
        val context = MyApplication.context
        val root = EmojiPackStore.root(context)
        root.deleteRecursively()
        val v3 = File(root, "v3").apply { mkdirs() }
        File(v3, "index.json").writeText(index)
        File(v3, "images").mkdirs()
        File(v3, "images/a.webp").writeBytes(byteArrayOf(1))
        File(v3, "images/b.webp").writeBytes(byteArrayOf(2))
        val v4 = File(root, "v4").apply { mkdirs() }
        File(v4, "index.json").writeText(index.replace("\"version\":3", "\"version\":4"))
        assertEquals(3, EmojiPackStore.load(context)?.version)
        assertEquals(3, PrefManager.emojiPackVersion)
    }
}
