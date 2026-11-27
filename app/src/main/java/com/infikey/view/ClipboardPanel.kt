package com.infikey.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.infikey.Global
import com.infikey.theme.ThemeEngine

/** Clipboard history panel: entries, tap = paste, long-press = pin, ✕ = delete. */
class ClipboardPanel(context: Context) : ScrollView(context) {

    interface Callbacks {
        fun onPaste(text: String)
        /** Strip zero-width junk and insert (#75). */
        fun onClean(text: String)
        /** Convert romanised text to an Indic script (#81). */
        fun onToScript(text: String, lang: String)
        /** Ask the AI about the copied text (TL;DR) (#70). */
        fun onAskAi(text: String)
    }
    var callbacks: Callbacks? = null

    private val theme get() = ThemeEngine(context).current()
    private val prefs = Global.prefs
    private val d = resources.displayMetrics.density
    private val list = LinearLayout(context)

    init {
        // panels start hidden — the keyboard must open as JUST the keyboard;
        // clipboard history used to render below the emoji grid on first open
        visibility = GONE
        setBackgroundColor(theme.bg)
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rebuild()
    }

    fun rebuild() {
        list.removeAllViews()
        list.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        list.orientation = LinearLayout.VERTICAL

        val header = TextView(context).apply {
            text = "Clipboard history"
            textSize = 14f
            setTextColor(theme.hint)
            setPadding(0, (6 * d).toInt(), 0, (10 * d).toInt())
        }
        list.addView(header)

        // tools row: clean (#75) · ask AI (#78) · clipboard → script (#76)
        val top = runCatching { Global.clipboard.all() }.getOrDefault(emptyList())
            .firstOrNull { !it.isSensitive() }
        val tools = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun tool(label: String, onTap: () -> Unit) = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(theme.text)
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.setMargins(0, 0, (8 * d).toInt(), (8 * d).toInt())
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * d
                setColor(theme.keySpecial)
            }
            setOnClickListener { if (top == null) return@setOnClickListener; onTap() }
        }
        if (prefs.feature("clipboard_plain")) tools.addView(tool("🧹 Clean") { callbacks?.onClean(top!!.text) })
        if (prefs.feature("clipboard_ai")) tools.addView(tool("🤖 Ask AI") { callbacks?.onAskAi(top!!.text) })
        for ((lang, label) in listOf("devanagari" to "→ देवनागरी", "tamil" to "→ தமிழ்",
                "telugu" to "→ తెలుగు", "bengali" to "→ বাংলা", "gujarati" to "→ ગુજરાતી", "gurmukhi" to "→ ਪੰਜਾਬੀ")) {
            if (prefs.feature("clipboard_translate")) tools.addView(tool(label) { callbacks?.onToScript(top!!.text, lang) })
        }
        list.addView(tools)

        val entries = runCatching { Global.clipboard.all() }.getOrDefault(emptyList())
        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "Nothing copied yet.\nCopy text anywhere — it lands here."
                textSize = 14f
                setTextColor(theme.hint)
                gravity = Gravity.CENTER
                setPadding(0, (30 * d).toInt(), 0, (30 * d).toInt())
            })
            return
        }
        for (e in entries) {
            if (e.isSensitive()) continue // never display passwords/OTP rows
            list.addView(entryRow(e))
        }
    }

    private fun entryRow(e: com.infikey.ime.ClipboardHistory.Entry): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(theme.key)
                cornerRadius = 12 * d
            }
        }
        val body = TextView(context).apply {
            text = (if (e.pinned) "📌 " else "") + e.preview()
            textSize = 14f
            setTextColor(theme.text)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                setClip(e.text)
                callbacks?.onPaste(e.text)
            }
            // pin clipboard items (#74): long-press keeps the row on top
            if (prefs.feature("clipboard_pin"))
                setOnLongClickListener { Global.clipboard.pin(e); rebuild(); true }
        }
        row.addView(body)
        // edit before paste (#77): tweak the snippet inline
        if (prefs.feature("clipboard_edit")) {
            val edit = TextView(context).apply {
                text = "✎"
                textSize = 14f
                setTextColor(theme.hint)
                setPadding((10 * d).toInt(), 0, (4 * d).toInt(), 0)
                setOnClickListener {
                    val box = android.widget.EditText(context).apply { setText(e.text) }
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Edit before paste")
                        .setView(box)
                        .setPositiveButton("Paste") { _, _ ->
                            val t = box.text.toString()
                            setClip(t)
                            callbacks?.onPaste(t)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            row.addView(edit)
        }
        val del = TextView(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(theme.hint)
            setPadding((10 * d).toInt(), 0, 0, 0)
            setOnClickListener { Global.clipboard.delete(e); rebuild() }
        }
        row.addView(del)
        return row
    }

    private fun setClip(text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("InfiKey", text))
        }
    }
}
