package com.infikey.ime

import android.content.ClipData
import android.content.Context
import android.util.Log

/**
 * Rolling clipboard history with pinning, captured by the IME service
 * whenever the focused window offers a new primary clip.
 */
class ClipboardHistory(context: Context) {

    companion object {
        const val TAG = "ClipboardHistory"
        private const val MAX_UNPINNED = 30
        private const val MAX_TEXT = 20_000
    }

    data class Entry(
        val text: String,
        val timeMs: Long,
        val pinned: Boolean = false
    ) {
        fun preview(): String = text.take(120).replace('\n', ' ')
        fun isSensitive() =
            text.contains("password", true) || text.contains("otp", true) ||
                text.contains("token", true) || Regex("\\b\\d{4}[ -]?\\d{4}").containsMatchIn(text.take(24))
    }

    private val appContext = context.applicationContext
    private val sp = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)
    private val entries = mutableListOf<Entry>()
    private var lastRaw: String? = null

    init { loadPersisted() }

    private fun loadPersisted() {
        runCatching {
            val arr = org.json.JSONArray(sp.getString("clipboard_history", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries.add(
                    Entry(
                        o.getString("t").take(MAX_TEXT),
                        o.optLong("time", 0L),
                        o.optBoolean("pinned", false)
                    )
                )
            }
        }.onFailure { Log.w(TAG, "clipboard history restore failed", it) }
    }

    private fun persist() {
        runCatching {
            val arr = org.json.JSONArray()
            entries.take(40).forEach { e ->
                arr.put(org.json.JSONObject().put("t", e.text).put("time", e.timeMs).put("pinned", e.pinned))
            }
            sp.edit().putString("clipboard_history", arr.toString()).apply()
        }
    }

    /** Called from the service when a new primary clip is observed. */
    fun observe(clip: ClipData?) {
        if (clip == null || clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(appContext)?.toString() ?: return
        observeText(text)
    }

    fun observeText(text: String) {
        if (text.isEmpty() || text.length > 100_000) return
        if (text == lastRaw) return
        lastRaw = text
        // de-dup: newer copy replaces the old unpinned copy
        entries.removeAll { !it.pinned && it.text == text }
        entries.add(0, Entry(text, System.currentTimeMillis()))
        trim()
        persist()
    }

    private fun trim() {
        val pinnedCount = entries.count { it.pinned }
        var unpinned = 0
        val keep = mutableListOf<Entry>()
        for (e in entries) {
            if (e.pinned) keep.add(e)
            else if (unpinned < MAX_UNPINNED) { keep.add(e); unpinned++ }
        }
        if (keep.size > pinnedCount + MAX_UNPINNED) keep.subList(pinnedCount + MAX_UNPINNED, keep.size).clear()
        entries.clear()
        entries.addAll(keep)
    }

    fun all(): List<Entry> = entries.toList().sortedWith(
        compareByDescending<Entry> { it.pinned }.thenByDescending { it.timeMs }
    )

    fun latest(): Entry? = entries.firstOrNull()

    fun pin(entry: Entry) {
        val i = entries.indexOfFirst { it.text == entry.text }
        if (i >= 0) { entries[i] = entry.copy(pinned = !entry.pinned); persist() }
    }

    fun delete(entry: Entry) {
        entries.removeAll { it.text == entry.text }
        persist()
    }

    fun clearUnpinned() {
        entries.removeAll { !it.pinned }
        persist()
    }

    fun clearAll() {
        entries.clear()
        lastRaw = null
        persist()
    }
}
