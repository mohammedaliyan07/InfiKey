package com.infikey.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object DefaultModel {
    const val REPO = "LiquidAI/LFM2.5-1.2B-Instruct-GGUF"
    const val FILE = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
    const val SIZE_BYTES = 731L * 1024L * 1024L // ~0.73 GB
    const val HF_PRIMARY =
        "https://huggingface.co/$REPO/resolve/main/$FILE"
    const val HF_MIRROR =
        "https://huggingface.co/unsloth/LFM2.5-1.2B-Instruct-GGUF/resolve/main/$FILE"
    const val NAME_MARKER = "LFM2.5"
}

/**
 * Central GGUF hub:
 *  - scans the app models folder (also the folder a user can drop files into from a PC),
 *  - validates GGUF magic,
 *  - imports any .gguf from SAF,
 *  - downloads the default LFM2.5-1.2B (731 MB) with progress + mirror fallback,
 *  - extracts a model bundled in the APK (bundled CI build).
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext

    val modelsDir: File =
        (appContext.getExternalFilesDir(null) ?: appContext.filesDir).let { base ->
            File(base, "models").apply { mkdirs() }
        }

    data class LocalModel(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val isDefault: Boolean
    ) {
        fun sizeLabel(): String {
            val gb = sizeBytes / 1024.0 / 1024.0 / 1024.0
            val mb = sizeBytes / 1024.0 / 1024.0
            return if (gb >= 0.5) String.format("%.2f GB", gb) else String.format("%.0f MB", mb)
        }
    }

    fun scan(): List<LocalModel> =
        modelsDir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
            ?.map { LocalModel(it, it.name, it.length(), it.name.contains(DefaultModel.NAME_MARKER, true)) }
            ?.sortedByDescending { it.sizeBytes }
            ?: emptyList()

    fun byPath(path: String): LocalModel? = scan().firstOrNull { it.file.absolutePath == path }

    /** null = valid, otherwise error message. */
    fun validate(file: File): String? {
        if (!file.exists()) return "file not found"
        if (file.length() < 5L * 1024L * 1024L) return "file too small to be a model"
        runCatching {
            file.inputStream().use { ins ->
                val magic = ByteArray(4)
                if (ins.read(magic) != 4) return "unreadable file"
                val tag = String(magic, Charsets.US_ASCII)
                if (tag != "GGUF") return "not a GGUF file (found \"$tag\")"
            }
        }.onFailure { return "cannot read file" }
        return null
    }

    fun importFromUri(uri: Uri, onProgress: (Long) -> Unit = {}): Result<LocalModel> = runCatching {
        val resolver = appContext.contentResolver
        val suggested = queryDisplayName(uri) ?: "imported.gguf"
        val safeName = if (suggested.endsWith(".gguf", true)) suggested else "$suggested.gguf"
        val dst = File(modelsDir, safeName)
        var copied = 0L
        resolver.openInputStream(uri)!!.use { ins ->
            dst.outputStream().use { out ->
                val buf = ByteArray(1024 * 512)
                while (true) {
                    val r = ins.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    copied += r
                    onProgress(copied)
                }
            }
        }
        val err = validate(dst)
        if (err != null) {
            dst.delete()
            throw IllegalArgumentException(err)
        }
        LocalModel(dst, dst.name, dst.length(), dst.name.contains(DefaultModel.NAME_MARKER, true))
    }

    fun downloadTo(url: String, dst: File, onProgress: (Long, Long) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        val total = conn.contentLengthLong
        conn.inputStream.use { ins ->
            dst.outputStream().use { out ->
                val buf = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    val r = ins.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    copied += r
                    if (total > 0) onProgress(copied, total)
                }
            }
        }
        if (dst.length() < 10L * 1024L * 1024L) throw IllegalStateException("download truncated")
    }

    suspend fun downloadDefault(
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<LocalModel> = runCatching {
        val dst = File(modelsDir, DefaultModel.FILE)
        val tmp = File(modelsDir, "${DefaultModel.FILE}.part")
        try {
            runCatching { downloadTo(DefaultModel.HF_PRIMARY, tmp, onProgress) }
                .onFailure { Log.w(TAG, "primary download failed, trying mirror", it) }
                .recoverCatching { downloadTo(DefaultModel.HF_MIRROR, tmp, onProgress) }
                .getOrThrow()
        } finally {
            runCatching { if (dst.exists()) dst.delete() }
        }
        tmp.renameTo(dst)
        LocalModel(dst, dst.name, dst.length(), true)
    }

    /** Returns the file if a model was bundled inside the APK, extracted once to modelsDir. */
    fun extractBundledIfAny(): LocalModel? {
        val prefs = appContext.getSharedPreferences("infikey", Context.MODE_PRIVATE)
        val marker = "bundled_extracted"
        return runCatching {
            val names = appContext.assets.list("models") ?: return null
            val gguf = names.firstOrNull { it.endsWith(".gguf") } ?: return null
            if (prefs.getBoolean(marker, false)) return scan().firstOrNull { it.name == gguf }
            val dst = File(modelsDir, gguf)
            appContext.assets.open("models/$gguf").use { ins ->
                dst.outputStream().use { out -> ins.copyTo(out, 1024 * 1024) }
            }
            prefs.edit().putBoolean(marker, true).apply()
            LocalModel(dst, dst.name, dst.length(), true)
        }.getOrNull()
    }

    fun pickDefault(): LocalModel? =
        scan().firstOrNull { it.isDefault } ?: scan().firstOrNull()

    fun delete(model: LocalModel): Boolean = model.file.delete()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    companion object { const val TAG = "ModelManager" }
}
