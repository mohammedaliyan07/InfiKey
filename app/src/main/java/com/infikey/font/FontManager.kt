package com.infikey.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Font system: system faces out of the box + user-imported TTF/OTF
 * (Settings → Fonts → pick file). One shared Typeface per font id.
 */
class FontManager(context: Context) {

    companion object {
        const val TAG = "FontManager"

        data class FontDef(val id: String, val label: String, val systemFamily: String?)

        val BUILT_INS = listOf(
            FontDef("system_sans", "System Sans", "sans-serif"),
            FontDef("system_medium", "System Medium", "sans-serif-medium"),
            FontDef("system_light", "System Light", "sans-serif-light"),
            FontDef("system_condensed", "Condensed", "sans-serif-condensed"),
            FontDef("serif", "Serif", "serif"),
            FontDef("monospace", "Monospace", "monospace")
        )
    }

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "fonts").apply { mkdirs() }
    private val cache = HashMap<String, Typeface>()

    data class UserFont(val id: String, val label: String, val file: File)

    fun listUserFonts(): List<UserFont> =
        dir.listFiles { f -> f.isFile && (f.extension.equals("ttf", true) || f.extension.equals("otf", true)) }
            ?.map { UserFont(it.nameWithoutExtension, it.nameWithoutExtension, it) }
            ?: emptyList()

    fun all(): List<Pair<String, String>> =
        BUILT_INS.map { it.id to it.label } + listUserFonts().map { it.id to it.label }

    fun labelOf(id: String): String =
        BUILT_INS.firstOrNull { it.id == id }?.label
            ?: listUserFonts().firstOrNull { it.id == id }?.label
            ?: id

    /** Resolves the Typeface for a font id; null → caller falls back to default. */
    fun typeface(id: String): Typeface? {
        cache[id]?.let { return it }
        val builtIn = BUILT_INS.firstOrNull { it.id == id }
        if (builtIn != null) {
            val tf = Typeface.create(builtIn.systemFamily, Typeface.NORMAL)
            cache[id] = tf
            return tf
        }
        val user = listUserFonts().firstOrNull { it.id == id } ?: return null
        return runCatching { Typeface.createFromFile(user.file) }
            .onSuccess { cache[id] = it }
            .onFailure { Log.w(TAG, "font ${user.file.name} failed to load", it) }
            .getOrNull()
    }

    /** Copies a picked TTF/OTF into the fonts dir. Returns its id. */
    fun importFromUri(uri: Uri): Result<String> = runCatching {
        val resolver = appContext.contentResolver
        val name = runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: "imported.ttf"
        val safe = name.substringAfterLast('/').ifBlank { "imported.ttf" }
        val dst = File(dir, safe)
        resolver.openInputStream(uri)!!.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
        // validate by loading
        Typeface.createFromFile(dst)
        dst.nameWithoutExtension
    }

    fun delete(id: String): Boolean {
        val f = listUserFonts().firstOrNull { it.id == id }?.file ?: return false
        cache.remove(id)
        return f.delete()
    }
}
