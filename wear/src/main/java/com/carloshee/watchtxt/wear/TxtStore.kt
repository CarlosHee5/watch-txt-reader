package com.carloshee.watchtxt.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream

/** Stores received .txt files in filesDir/txt. [version] bumps on every change so the UI refreshes. */
object TxtStore {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private const val MAX_CHUNK = 1500

    fun dir(context: Context): File = File(context.filesDir, "txt").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && !f.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun safeName(raw: String): String {
        val cleaned = raw.substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\u0000]"), "_")
            .trim()
            .trimStart('.')
            .take(120)
        return cleaned.ifEmpty { "未命名.txt" }
    }

    @Synchronized
    fun save(context: Context, name: String, input: InputStream) {
        val d = dir(context)
        val tmp = File(d, ".partial_" + System.nanoTime())
        try {
            tmp.outputStream().use { out -> input.copyTo(out) }
            val target = File(d, safeName(name))
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        _version.value = _version.value + 1
    }

    fun delete(context: Context, name: String): Boolean {
        val ok = File(dir(context), safeName(name)).delete()
        context.getSharedPreferences(READER_PREFS, Context.MODE_PRIVATE)
            .edit().remove(positionKey(name)).apply()
        _version.value = _version.value + 1
        return ok
    }

    /** Splits text into paragraphs; very long paragraphs are chunked so the lazy list stays smooth. */
    fun readParagraphs(context: Context, name: String): List<String> {
        val f = File(dir(context), safeName(name))
        if (!f.exists()) return emptyList()
        val text = f.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val result = ArrayList<String>()
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val p = line.trimEnd()
            if (p.isBlank()) continue
            if (p.length <= MAX_CHUNK) {
                result.add(p)
            } else {
                var i = 0
                while (i < p.length) {
                    val end = minOf(i + MAX_CHUNK, p.length)
                    result.add(p.substring(i, end))
                    i = end
                }
            }
        }
        return result
    }

    const val READER_PREFS = "reader"
    const val KEY_FONT = "font_size"
    fun positionKey(name: String) = "pos_$name"
}
