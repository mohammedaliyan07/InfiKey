package com.infikey.ime

import android.view.inputmethod.InputConnection
import com.infikey.layout.Layouts
import com.infikey.layout.Transliteration
import com.infikey.prefs.Prefs

/**
 * All text mutation between the keyboard and the focused field:
 * composing region, autocorrect, transliteration preview, macro expansion,
 * auto-caps and word-aware backspace.
 */
class InputLogic(private val prefs: Prefs, private val suggestions: SuggestionEngine) {

    companion object {
        private const val MACRO_PREFIX = "/"
        private const val MAX_COMPOSING = 40
    }

    private var ic: InputConnection? = null
    private var composing = StringBuilder()
    private var prevWord: String? = null

    /** #12/#77 — nothing learned, predicted or stored while incognito. */
    @Volatile
    var incognito: Boolean = false

    /** #45 — script of the last committed word (layout id or null). */
    var lastScriptHint: String? = null

    // #53 — undo history: recently deleted chunks, newest last
    private val undoStack = ArrayDeque<String>()

    fun undoAvailable(): Boolean = undoStack.isNotEmpty()

    fun pushUndo(chunk: String) {
        if (chunk.isNotBlank()) {
            undoStack.addLast(chunk)
            if (undoStack.size > 20) undoStack.removeFirst()
        }
    }

    fun popUndo(): String? = undoStack.removeLastOrNull()

    /** Called by the service whenever the editor or cursor moves. */
    fun attach(connection: InputConnection?, keepComposing: Boolean = false) {
        ic = connection
        if (!keepComposing) {
            finishComposing()
            prevWord = null
        }
    }

    fun composingText(): String = composing.toString()
    fun previousWord(): String? = prevWord
    fun hasComposing(): Boolean = composing.isNotEmpty()

    private fun finishComposing() {
        if (composing.isNotEmpty() && ic != null) {
            ic?.finishComposingText()
            commitToHistory(composing.toString())
        }
        composing.clear()
    }

    private fun commitToHistory(word: String) {
        if (incognito) return
        val w = word.trim()
        SmartText.scriptOf(w)?.let { lastScriptHint = it }
        if (w.isNotEmpty()) {
            suggestions.learn(w)
            prevWord?.let { suggestions.learnBigram(it, w) }
            prevWord = w
        }
    }

    fun commitChar(c: Char) = commitString(c.toString())

    fun commitString(text: String, finish: Boolean = true) {
        if (finish) finishComposing()
        ic?.commitText(text, 1)
    }

    /** A letter key was pressed. */
    fun appendLetter(c: String) {
        val conn = ic ?: return
        val translitActive = prefs.translitLang.isNotEmpty() &&
            Layouts.byId(prefs.translitLang).let { Transliteration.supports(it.id) }
        if (composing.length >= MAX_COMPOSING) {
            finishComposing()
        }
        composing.append(c)
        if (translitActive && composing.any { it.isLetter() && it.code < 0x0900 }) {
            // live preview: show transliterated output under the cursor
            val preview = Transliteration.translate(composing.toString(), prefs.translitLang)
            conn.setComposingText(preview, 1)
        } else {
            conn.setComposingText(composing.toString(), 1)
        }
    }

    /** Space / punctuation ends the current word: applies autocorrect + translit commit. */
    fun commitCurrentWord(replacement: String = " ") {
        val conn = ic ?: return
        val raw = composing.toString()
        if (raw.isEmpty()) {
            ic?.commitText(replacement, 1)
            return
        }
        val translitActive = prefs.translitLang.isNotEmpty() &&
            Transliteration.supports(prefs.translitLang)
        // autocorrect runs a dictionary scan — do it once, not twice
        val fixed = if (prefs.autoCorrect) suggestions.correct(raw) else null
        val final = when {
            translitActive -> Transliteration.translate(raw, prefs.translitLang)
            fixed != null -> fixed
            else -> raw
        }
        conn.finishComposingText()
        ic?.commitText(final, 1)
        commitToHistory(final)
        composing.clear()
        if (replacement.isNotEmpty()) ic?.commitText(replacement, 1)
    }

    /** Smart backspace: kill composing char first, else delete one grapheme cluster. */
    fun backspace() {
        val conn = ic ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            conn.setComposingText(composing.toString(), 1)
            return
        }
        val before = conn.getTextBeforeCursor(12, 0)?.toString() ?: ""
        if (before.isEmpty()) return
        // safe grapheme delete (#19): emoji / Indic clusters stay whole when on
        val n = if (prefs.feature("cursor_cluster")) SmartText.clusterLen(before)
        else if (Character.isLowSurrogate(before.last())) 2 else 1
        conn.deleteSurroundingText(n.coerceAtMost(before.length), 0)
    }

    fun deleteWord() {
        val conn = ic ?: return
        if (composing.isNotEmpty()) {
            pushUndo(composing.toString())
            composing.clear()
            conn.setComposingText("", 1)
            return
        }
        val before = conn.getTextBeforeCursor(80, 0) ?: ""
        val trimmed = before.trimEnd()
        val cut = before.length - trimmed.length // trailing spaces to remove
        val wordLen = trimmed.length - (trimmed.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1)
        if (wordLen > 0) pushUndo(trimmed.substring(trimmed.length - wordLen))
        conn.deleteSurroundingText(wordLen + cut, 0)
    }

    /** ENTER after "1. " or "- " continues the list automatically (#33). */
    fun listContinuation(): String? {
        val conn = ic ?: return null
        val before = conn.getTextBeforeCursor(200, 0) ?: return null
        val nl = before.lastIndexOf('\n')
        val line = if (nl >= 0) before.substring(nl + 1) else before
        val numbered = Regex("^(\\s*)(\\d+)[.)]\\s+").find(line)
        return when {
            numbered != null ->
                numbered.groupValues[1] + ((numbered.groupValues[2].toIntOrNull() ?: 0) + 1) + ". "
            Regex("^\\s*[-*•]\\s+").containsMatchIn(line) -> "- "
            else -> null
        }
    }

    /** Accepts a suggestion, replacing the composing word. */
    fun acceptSuggestion(word: String) {
        val conn = ic ?: return
        val wasComposing = composing.isNotEmpty()
        conn.finishComposingText()
        if (wasComposing) {
            // remove raw composing text, insert suggestion
            conn.deleteSurroundingText(composing.length, 0)
        }
        composing.clear()
        ic?.commitText(word, 1)
        commitToHistory(word)
    }

    /** Caps state for the next letter: start of sentence or after ". ". */
    fun shouldAutoCap(): Boolean {
        val conn = ic ?: return false
        val before = conn.getTextBeforeCursor(3, 0) ?: return false
        if (before.isEmpty()) return true
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        return last == '.' || last == '!' || last == '?' || last == '\n' ||
            (trimmed.length >= 2 && last == ' ' && trimmed[trimmed.length - 2] in ".!?")
    }

    /** Expands "/macro" when the user commits space after it. Returns true if expanded. */
    fun tryMacro(): Boolean {
        val raw = composing.toString()
        if (!raw.startsWith(MACRO_PREFIX) || raw.length < 2) return false
        val template = prefs.readMacros()[raw] ?: return false
        // #47 — {{name}}/{{email}}/{{phone}}/{{address}}/{{date}}/{{time}} resolve
        // from saved facts; unknown placeholders pass through untouched.
        val expanded = template.replace(Regex("\\{\\{(\\w+)}}")) { m ->
            when (m.groupValues[1].lowercase()) {
                "date" -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
                "time" -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                else -> prefs.facts()[m.groupValues[1].lowercase()] ?: m.value
            }
        }
        composing.clear()
        ic?.setComposingText("", 1)
        ic?.commitText(expanded, 1)
        return true
    }

    fun reset() {
        composing.clear()
        prevWord = null
    }
}
