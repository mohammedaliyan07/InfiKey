package com.infikey.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.infikey.prefs.Prefs
import com.infikey.theme.ThemeEngine

/** Top strip: 3 suggestion chips + AI / clipboard / settings buttons. */
class SuggestionBar(context: Context) : LinearLayout(context) {

    interface Callbacks {
        fun onSuggestion(word: String)
        fun onAiTap()
        fun onClipboardTap()
        fun onSettingsTap()
        /** Tap on the smart context chip (calculator / undo / autofill / …). */
        fun onContextChip(label: String)
    }

    var callbacks: Callbacks? = null

    private val prefs = Prefs(context)
    private val theme get() = ThemeEngine(context).current()
    private val d = resources.displayMetrics.density

    private val chips = listOf(
        chip(1f), chip(1f), chip(1f)
    )

    private val contextChip = TextView(context).apply {
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.8f)
        gravity = Gravity.CENTER
        textSize = 14f
        setSingleLine()
        setPadding((10 * d).toInt(), (7 * d).toInt(), (10 * d).toInt(), (7 * d).toInt())
        background = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(withAlpha(theme.accent, 0.22f))
            setStroke((1 * d).toInt(), withAlpha(theme.accent, 0.55f))
        }
        visibility = GONE
        setOnClickListener { if (visibility == VISIBLE) callbacks?.onContextChip(text.toString()) }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((8 * d).toInt(), (5 * d).toInt(), (8 * d).toInt(), (5 * d).toInt())
        chips.forEach { addView(it) }
        addView(contextChip)
        addView(iconButton("\u2726", 0.62f) { callbacks?.onAiTap() })     // ✦ AI
        addView(iconButton("\uD83D\uDCCB", 0.62f) { callbacks?.onClipboardTap() }) // 📋
        addView(iconButton("\u2699", 0.62f) { callbacks?.onSettingsTap() }) // ⚙
    }

    private fun chip(weight: Float) = TextView(context).apply {
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, weight)
        gravity = Gravity.CENTER
        textSize = 15f
        setSingleLine()
        setPadding((10 * d).toInt(), (7 * d).toInt(), (10 * d).toInt(), (7 * d).toInt())
        setOnClickListener {
            callbacks?.onSuggestion(text.toString())
        }
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 12 * d
            setStroke((1 * d).toInt(), withAlpha(theme.hint, 0.35f))
        }
    }

    private fun iconButton(icon: String, weight: Float, onTap: () -> Unit) = TextView(context).apply {
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, weight)
        gravity = Gravity.CENTER
        text = icon
        textSize = 17f
        setPadding((4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
        setOnClickListener { onTap() }
    }

    private fun withAlpha(color: Int, alpha: Float) =
        ((alpha * 255).toInt() shl 24) or (color and 0xFFFFFF)

    fun setTypeface(tf: Typeface?) {
        chips.forEach { it.typeface = tf }
    }

    fun showSuggestions(words: List<String>) {
        if (!prefs.suggestionsEnabled) { hideAll(); return }
        chips.forEachIndexed { i, chipView ->
            val w = words.getOrNull(i)
            if (w.isNullOrEmpty()) {
                chipView.text = ""
                chipView.visibility = INVISIBLE
            } else {
                chipView.text = w
                chipView.visibility = VISIBLE
            }
        }
    }

    fun showContextChip(label: String?) {
        if (label.isNullOrEmpty() || !prefs.suggestionsEnabled) {
            contextChip.visibility = GONE
            return
        }
        contextChip.text = label
        contextChip.visibility = VISIBLE
    }

    fun hideAll() {
        chips.forEach { it.visibility = INVISIBLE }
    }
}
