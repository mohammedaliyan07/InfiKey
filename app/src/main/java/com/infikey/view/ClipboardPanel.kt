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

    interface Callbacks { fun onPaste(text: String) }
    var callbacks: Callbacks? = null

    private val theme get() = ThemeEngine(context).current()
    private val d = resources.displayMetrics.density
    private val list = LinearLayout(context)

    init {
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
            setOnLongClickListener { Global.clipboard.pin(e); rebuild(); true }
        }
        row.addView(body)
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
