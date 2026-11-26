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
        val w = word.trim()
        if (w.isNotEmpty()) {
            suggestions.learn(w)
            prevWord?.let { suggestions.learnBigram(it, w) }
            prevWord = w
        }
    }

    fun commitChar(c: Char) = commitString(c.toString())

    fun commitString(text: String, finish: Boolean = true) {
        if (finish) finishComposing()
        ic?.commitText(text, true)
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
            ic?.commitText(replacement, true)
            return
        }
        val translitActive = prefs.translitLang.isNotEmpty() &&
            Transliteration.supports(prefs.translitLang)
        val final = when {
            translitActive -> Transliteration.translate(raw, prefs.translitLang)
            prefs.autoCorrect && suggestions.correct(raw) != null -> suggestions.correct(raw)!!
            else -> raw
        }
        conn.finishComposingText()
        ic?.commitText(final, true)
        commitToHistory(final)
        composing.clear()
        if (replacement.isNotEmpty()) ic?.commitText(replacement, true)
    }

    /** Smart backspace: kill composing char first, else delete word with accents kept. */
    fun backspace() {
        val conn = ic ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            conn.setComposingText(composing.toString(), 1)
            return
        }
        val before = conn.getTextBeforeCursor(2, 0) ?: ""
        if (before.isEmpty()) return
        // swallow surrogate pairs (emoji) as one unit
        val last = before.last()
        if (Character.isLowSurrogate(last)) {
            conn.deleteSurroundingText(2, 0)
        } else {
            conn.deleteSurroundingText(1, 0)
        }
    }

    fun deleteWord() {
        val conn = ic ?: return
        if (composing.isNotEmpty()) {
            composing.clear()
            conn.setComposingText("", 1)
            return
        }
        val before = conn.getTextBeforeCursor(80, 0) ?: ""
        val trimmed = before.trimEnd()
        val cut = before.length - trimmed.length // trailing spaces to remove
        val wordLen = trimmed.length - (trimmed.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1)
        conn.deleteSurroundingText(wordLen + cut, 0)
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
        ic?.commitText(word, true)
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
        val macro = prefs.readMacros()[raw] ?: return false
        composing.clear()
        ic?.setComposingText("", 1)
        ic?.commitText(macro, true)
        return true
    }

    fun reset() {
        composing.clear()
        prevWord = null
    }
}
