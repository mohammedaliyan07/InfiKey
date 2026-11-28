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
 *  - downloads the default LFM2.5-1.2B (731 MB) with progress + mirror fallback
 *    and cooperative cancellation ([cancelDownload]),
 *  - deletes models from disk ([delete]).
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

    /**
     * SAF import (Settings → AI → Import any .gguf). Copies to a .part file
     * first, validates the GGUF magic, then renames into place — a killed
     * import can never leave a broken model lying in the folder, and the
     * copy progress is reported so multi-GB files don't look frozen.
     */
    fun importFromUri(uri: Uri, onProgress: (Long) -> Unit = {}): Result<LocalModel> = runCatching {
        val resolver = appContext.contentResolver
        val suggested = queryDisplayName(uri) ?: "imported.gguf"
        val safeName = sanitizeModelName(suggested)
        val dst = File(modelsDir, safeName)
        // never silently overwrite an existing model with the same name
        val unique = if (dst.exists()) {
            val stem = dst.nameWithoutExtension
            var i = 2
            while (File(modelsDir, "$stem-$i.gguf").exists()) i++
            File(modelsDir, "$stem-$i.gguf")
        } else dst
        val tmp = File(modelsDir, unique.name + ".part")
        runCatching {
            var copied = 0L
            val ins = resolver.openInputStream(uri)
                ?: throw IllegalStateException("storage provider refused to open that file")
            ins.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1024 * 512)
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        copied += r
                        onProgress(copied)
                    }
                    out.flush()
                }
            }
            val err = validate(tmp)
            if (err != null) throw IllegalArgumentException(err)
            if (!tmp.renameTo(unique)) {
                // last resort on odd filesystems: copy over instead of failing
                tmp.copyTo(unique, overwrite = true)
                tmp.delete()
            }
        }.onFailure { e ->
            runCatching { tmp.delete() }
            throw e
        }
        LocalModel(unique, unique.name, unique.length(), unique.name.contains(DefaultModel.NAME_MARKER, true))
    }

    /** Strips path separators / illegal chars; guarantees a .gguf name. */
    private fun sanitizeModelName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9 ._()\\-\\[\\]]"), "_").trim()
            .ifBlank { "imported.gguf" }
        val capped = base.take(120)
        return if (capped.endsWith(".gguf", true)) capped else "$capped.gguf"
    }

    fun downloadTo(url: String, dst: File, onProgress: (Long, Long) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        val total = conn.contentLengthLong
        var received = 0L
        conn.inputStream.use { ins ->
            dst.outputStream().use { out ->
                val buf = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    if (cancelled) throw DownloadCancelledException(url)
                    val r = ins.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    copied += r
                    received = copied
                    if (total > 0) onProgress(copied, total)
                }
            }
        }
        if ((total > 0 && received != total) || dst.length() < 10L * 1024L * 1024L) throw IllegalStateException("download truncated")
    }

    /** Raised when the user stops a download; the partial .part file is cleaned up. */
    class DownloadCancelledException(val url: String) :
        Exception("download cancelled")

    @Volatile
    private var cancelled = false

    /** Stops any in-progress model download (stop button in Settings → AI). */
    fun cancelDownload() {
        cancelled = true
    }

    private fun resetCancel() {
        cancelled = false
    }

    suspend fun downloadDefault(
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<LocalModel> = runCatching {
        resetCancel()
        val dst = File(modelsDir, DefaultModel.FILE)
        val tmp = File(modelsDir, "${DefaultModel.FILE}.part")
        try {
            runCatching { downloadTo(DefaultModel.HF_PRIMARY, tmp, onProgress) }
                .onFailure { Log.w(TAG, "primary download failed, trying mirror", it) }
                .recoverCatching { downloadTo(DefaultModel.HF_MIRROR, tmp, onProgress) }
                .getOrThrow()
            // clean slate: replace any stale copy of the model
            runCatching { if (dst.exists()) dst.delete() }
            if (!tmp.renameTo(dst)) throw IllegalStateException("could not finalize download")
            LocalModel(dst, dst.name, dst.length(), true)
        } catch (t: Throwable) {
            // cancelled or failed — never leave a partial .part file behind
            runCatching { tmp.delete() }
            throw t
        }
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
