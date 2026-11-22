package com.example.c001apk.util.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.example.c001apk.util.PrefManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The installed pack on disk, plus a small bitmap cache.
 *
 * The pack lives in `filesDir/emoji_pack/v<version>/`; the version is recorded in preferences
 * only after a complete, validated extraction, and [load] falls back to the newest valid folder
 * so an interrupted install cannot leave the app without emoji.
 */
internal object EmojiPackStore {
    private val bitmaps = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }
    private val mutableState = MutableStateFlow<EmojiPack?>(null)
    val state: StateFlow<EmojiPack?> = mutableState.asStateFlow()

    @Volatile private var directory: File? = null
    @Volatile private var current: EmojiPack? = null

    fun root(context: Context) = File(context.filesDir, "emoji_pack")

    /** Loads the newest valid pack. Cheap enough for application start: one small JSON file. */
    @Synchronized
    fun load(context: Context): EmojiPack? {
        val root = root(context)
        val folders = root.listFiles()
            ?.filter { it.isDirectory && it.name.matches(Regex("v[0-9]{1,9}")) }
            ?.sortedByDescending { it.name.removePrefix("v").toIntOrNull() ?: -1 }
            .orEmpty()
        for (folder in folders) {
            val pack = read(folder)
            if (pack != null) {
                if (PrefManager.emojiPackVersion != pack.version) PrefManager.emojiPackVersion = pack.version
                activate(context, folder, pack)
                return pack
            }
        }
        bitmaps.evictAll()
        directory = null
        current = null
        if (PrefManager.emojiPackVersion != 0) PrefManager.emojiPackVersion = 0
        mutableState.value = null
        return null
    }

    @Synchronized
    fun activate(context: Context, folder: File, pack: EmojiPack) {
        bitmaps.evictAll()
        directory = folder
        current = pack
        mutableState.value = pack
        // Keep only the active folder; stale versions waste storage and are never read again.
        root(context).listFiles()?.filter { it.isDirectory && it != folder }?.forEach { it.deleteRecursively() }
    }

    fun version(): Int = current?.version ?: 0

    fun groups(): List<EmojiPack.Group> = current?.groups.orEmpty()

    fun codes(): List<String> = current?.codes.orEmpty()

    fun contains(code: String): Boolean = current?.files?.containsKey(code) == true

    fun drawable(context: Context, code: String): Drawable? {
        val bitmap = bitmap(code) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    fun bitmap(code: String): Bitmap? {
        val pack = current ?: return null
        val folder = directory ?: return null
        bitmaps.get(code)?.let { return it }
        val path = pack.files[code] ?: return null
        val file = File(folder, path)
        if (!file.isFile) return null
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull() ?: return null
        synchronized(bitmaps) { bitmaps.put(code, bitmap) }
        return bitmap
    }

    private fun read(folder: File): EmojiPack? {
        val index = File(folder, "index.json")
        if (!index.isFile || index.length() > 512 * 1024) return null
        val pack = runCatching { EmojiPack.parse(index.readText()) }.getOrNull() ?: return null
        if (pack.files.values.any { !File(folder, it).isFile }) return null
        return pack
    }
}
