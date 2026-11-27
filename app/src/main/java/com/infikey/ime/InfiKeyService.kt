package com.infikey.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.LinearLayout
import com.infikey.Global
import com.infikey.feature.FieldGenius
import com.infikey.layout.KeyAction
import com.infikey.layout.Layouts
import com.infikey.layout.SymbolsLayout
import com.infikey.view.AiPanel
import com.infikey.view.ClipboardPanel
import com.infikey.view.EmojiPanel
import com.infikey.view.KeyboardView
import com.infikey.view.SuggestionBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * InfiKey IME: suggestion bar + keyboard + panels, all wired through
 * [InputLogic], [GlideTracker] and the AI stack in [Global].
 */
class InfiKeyService : InputMethodService(),
    KeyboardView.Callbacks, SuggestionBar.Callbacks, AiPanel.Callbacks,
    EmojiPanel.Callbacks, ClipboardPanel.Callbacks {

    companion object {
        /** Toggle symbols state while navigating pages. */
        private const val MODE_LETTERS = 0
        private const val MODE_SYM1 = 1
        private const val MODE_SYM2 = 2
        private const val TAG = "InfiKeyService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Panels are nullable and built independently: a crash in one panel's
    // constructor must never leave the user with NO keyboard when they switch
    // to InfiKey (reported bug — keyboard "doesn't even open").
    private var root: LinearLayout? = null
    private var keyboardView: KeyboardView? = null
    private var suggestionBar: SuggestionBar? = null
    private var aiPanel: AiPanel? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipboardPanel: ClipboardPanel? = null
    private lateinit var inputLogic: InputLogic

    private val glide = GlideTracker()
    private var mode = MODE_LETTERS
    private var shiftOn = false
    private var capsLocked = false
    private var lastShiftTap = 0L
    private var lastSpaceTap = 0L
    private var fieldInfo: FieldGenius.FieldInfo? = null
    private var showingNumpad = false
    private var speech: SpeechRecognizer? = null
    private var clipListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null

    // ---- lifecycle ----

    override fun onCreate() {
        super.onCreate()
        // re-arm singletons when the process is recreated for a direct IME bind
        (application as? com.infikey.InfiKeyApp)?.let { runCatching { Global.init(it) } }
        inputLogic = InputLogic(Global.prefs, Global.suggestions)
        // clipboard history (#73): watch the system clipboard while InfiKey runs
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            if (prefs.feature("clipboard_history") && !inputLogic.incognito)
                Global.clipboard.observe(cm?.primaryClip)
        }
        cm?.addPrimaryClipChangedListener(clipListener!!)
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root = container

        // every section is built inside its own runCatching — one broken panel
        // is skipped instead of killing the whole input view
        suggestionBar = runCatching {
            SuggestionBar(this).apply { callbacks = this@InfiKeyService }
        }.onFailure { Log.w(TAG, "suggestion bar unavailable", it) }.getOrNull()
        keyboardView = runCatching {
            KeyboardView(this).apply { callbacks = this@InfiKeyService }
        }.onFailure { Log.e(TAG, "keyboard surface failed", it) }.getOrNull()
        aiPanel = runCatching {
            AiPanel(this).apply { callbacks = this@InfiKeyService }
        }.onFailure { Log.w(TAG, "AI panel unavailable", it) }.getOrNull()
        emojiPanel = runCatching {
            EmojiPanel(this).apply { callbacks = this@InfiKeyService }
        }.onFailure { Log.w(TAG, "emoji panel unavailable", it) }.getOrNull()
        clipboardPanel = runCatching {
            ClipboardPanel(this).apply { callbacks = this@InfiKeyService }
        }.onFailure { Log.w(TAG, "clipboard panel unavailable", it) }.getOrNull()

        suggestionBar?.let {
            container.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        if (keyboardView != null) {
            container.addView(keyboardView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        } else {
            // absolute fallback so the IME window still opens with something in it
            container.addView(android.widget.TextView(this).apply {
                text = "InfiKey could not start its keyboard — please report this crash"
                setPadding(0, 40, 0, 40)
            })
        }
        aiPanel?.let {
            container.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        emojiPanel?.let {
            container.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        clipboardPanel?.let {
            container.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        runCatching { applyLayout(prefs.activeLayoutId) }
            .onFailure { Log.w(TAG, "layout apply failed", it) }
        keyboardView?.refreshTypeface()
        return container
    }

    private val prefs get() = Global.prefs

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // a misbehaving editor (bad extras, exotic inputType) must never take
        // down the input session — degrade instead of crashing the IME
        runCatching {
            inputLogic.attach(currentInputConnection, keepComposing = restarting)
            val pkg = attribute?.packageName?.toString() ?: ""
            fieldInfo = FieldGenius.analyse(
                attribute, pkg,
                FieldGenius.labelFromExtras(attribute?.extras)
            )
            // incognito: password/phone fields + safe apps learn & store nothing (#12, #77)
            val protectedField = fieldInfo?.kind?.privacy == FieldGenius.Privacy.BLOCKED ||
                (pkg.isNotEmpty() && pkg in prefs.safeApps)
            inputLogic.incognito = protectedField

            // per-app memory: restore language automatically
            if (prefs.perAppAuto && pkg.isNotEmpty()) {
                Global.perApp.get(pkg)?.let { mem ->
                    if (mem.layoutId != prefs.activeLayoutId) {
                        prefs.activeLayoutId = mem.layoutId
                        Global.suggestions.loadForLayout(mem.layoutId)
                        applyLayout(mem.layoutId)
                    }
                }
            }
            Global.suggestions.loadForLayout(prefs.activeLayoutId)

            // dedicated number pad (#15): swap in the keypad for numeric fields
            val numeric = fieldInfo?.kind == FieldGenius.Kind.NUMBER ||
                fieldInfo?.kind == FieldGenius.Kind.PHONE
            if (numeric && prefs.feature("numpad") && !showingNumpad) {
                applyLayout(com.infikey.layout.Layouts.NUMPAD, persist = false)
            } else if ((!numeric || !prefs.feature("numpad")) && showingNumpad) {
                applyLayout(prefs.activeLayoutId)
            }
            updateSuggestions()
        }.onFailure { Log.w(TAG, "onStartInput degraded", it) }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        inputLogic.attach(null)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---- layout handling ----

    private fun applyLayout(id: String, persist: Boolean = true) {
        mode = MODE_LETTERS
        val layout = Layouts.byId(id)
        keyboardView?.setLayout(layout)
        keyboardView?.setCapsState(shiftOn, capsLocked)
        keyboardView?.setOneHanded(prefs.oneHanded)
        showingNumpad = id == com.infikey.layout.Layouts.NUMPAD
        if (persist) prefs.activeLayoutId = id
        Global.suggestions.loadForLayout(id)
    }

    private fun currentBaseLayout(): com.infikey.layout.Layout = Layouts.byId(prefs.activeLayoutId)

    override fun onGlideStart(x: Float, y: Float) {
        if (!prefs.feature("glide")) return
        glide.start(x, y, System.currentTimeMillis())
    }

    override fun onGlideMove(x: Float, y: Float) {
        glide.move(x, y, System.currentTimeMillis()) { gx, gy -> keyboardView?.letterUnder(gx, gy) }
    }

    override fun onGlideEnd(cancelled: Boolean) {
        if (cancelled) return
        val letters = glide.finish() ?: return
        val scored = Global.suggestions.scoreGlide(letters, 3)
        val best = scored.firstOrNull()
        if (best != null) {
            inputLogic.acceptSuggestion(best)
            suggestionBar?.showSuggestions(scored)
        }
    }

    override fun onHideRequest() {
        requestHideSelf(0)
    }

    // ---- key routing ----

    override fun onKey(output: String, action: KeyAction) {
        when (action) {
            KeyAction.CHAR -> handleChar(output)
            KeyAction.SHIFT -> handleShift()
            KeyAction.BACKSPACE -> {
                inputLogic.backspace()
                updateSuggestions()
            }
            KeyAction.ENTER -> {
                // auto-emoji (#79): a fitting emoji lands at the end of the sentence
                if (prefs.feature("auto_emoji") && !inputLogic.incognito) {
                    val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
                    SmartText.autoEmoji(before)?.let { inputLogic.commitString(it) }
                }
                val marker = inputLogic.listContinuation()
                inputLogic.commitCurrentWord("\n")
                if (marker != null) inputLogic.commitString(marker)
                updateSuggestions()
            }
            KeyAction.SPACE -> handleSpace()
            KeyAction.SYMBOLS -> when (output) {
                "more" -> { mode = MODE_SYM2; showSymbolsPage(SymbolsLayout.PAGE2) }
                "more2" -> { mode = MODE_SYM1; showSymbolsPage(SymbolsLayout.PAGE1) }
                else -> { mode = MODE_SYM1; showSymbolsPage(SymbolsLayout.PAGE1) }
            }
            KeyAction.ALPHABET -> applyLayout(prefs.activeLayoutId)
            KeyAction.GLOBE -> cycleLayout()
            KeyAction.EMOJI -> togglePanel(emojiPanel)
            KeyAction.CLIPBOARD -> { clipboardPanel?.rebuild(); togglePanel(clipboardPanel) }
            // AI chat panel (#42): the whole overlay honours its feature toggle
            KeyAction.AI -> if (prefs.feature("ai_chat")) {
                if (aiPanel?.isOpen() == true) aiPanel?.close() else { closePanels(); aiPanel?.open() }
            }
            KeyAction.SETTINGS -> openSettings()
            KeyAction.VOICE -> startVoice()
            KeyAction.NONE -> when (output) {
                "delete_word" -> inputLogic.deleteWord()
                "cursor_left" -> sendCursor(-1)
                "cursor_right" -> sendCursor(1)
                "clear" -> inputLogic.deleteWord()
            }
            else -> Unit
        }
        // remember language per app as soon as the user switches
        val pkg = currentInputEditorInfo?.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty()) Global.perApp.remember(pkg, prefs.activeLayoutId)
    }

    private fun handleChar(output: String) {
        when (output) {
            "globe" -> cycleLayout()
            else -> {
                var out = output
                // smart shift: auto-capitalise sentence starts (#5)
                if (prefs.autoCaps && out.length == 1 && out[0].isLowerCase() &&
                    inputLogic.shouldAutoCap() && !shiftOn && !capsLocked
                ) {
                    out = out.uppercase()
                }
                inputLogic.appendLetter(out)
                updateSuggestions()
            }
        }
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 350 && prefs.feature("caps_lock")) {
            capsLocked = !capsLocked
            shiftOn = capsLocked
        } else {
            shiftOn = !shiftOn
            if (capsLocked && !shiftOn) capsLocked = false
        }
        if (!shiftOn) selAnchor = -1
        lastShiftTap = now
        keyboardView?.setCapsState(shiftOn, capsLocked)
    }

    private fun handleSpace() {
        // double-tap space → period
        val now = System.currentTimeMillis()
        if (prefs.feature("double_space") && now - lastSpaceTap < 320 &&
            inputLogic.composingText().isEmpty()
        ) {
            inputLogic.commitString(". ")
            lastSpaceTap = 0L
            return
        }
        lastSpaceTap = now
        if (prefs.feature("macros") && inputLogic.tryMacro()) return
        inputLogic.commitCurrentWord(" ")
        updateSuggestions()
    }

    private fun showSymbolsPage(page: List<com.infikey.layout.KeyboardRow>) {
        keyboardView?.setLayout(
            com.infikey.layout.Layout(
                id = "_symbols_$mode", name = "Symbols", script = "Symbols",
                hasCase = false, main = page
            )
        )
    }

    private fun cycleLayout() {
        // language toggles (#25–#36, #16, #17) decide which scripts appear
        val enabled = prefs.enabledLayouts()
            .filter { Layouts.ALL.any { l -> l.id == it } && prefs.layoutEnabled(it) }
            .ifEmpty { listOf(prefs.activeLayoutId) }
        val idx = enabled.indexOf(prefs.activeLayoutId)
        val next = enabled[(idx + 1).mod(enabled.size)]
        applyLayout(next)
        android.widget.Toast.makeText(
            this, Layouts.byId(next).name, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private var cursorPos = 0
    private var selAnchor = -1

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        cursorPos = maxOf(newSelStart, newSelEnd)
    }

    /** Spacebar slide: move the caret without losing the keyboard. */
    private fun sendCursor(dir: Int) {
        val ic = currentInputConnection ?: return
        val target = (cursorPos + dir).coerceAtLeast(0)
        // selection mode (#14): hold shift and slide to extend a selection
        if (prefs.feature("selection_mode") && shiftOn) {
            if (selAnchor < 0) selAnchor = cursorPos
            ic.setSelection(minOf(selAnchor, target), maxOf(selAnchor, target))
        } else {
            selAnchor = -1
            ic.setSelection(target, target)
        }
        cursorPos = target
    }

    private fun closePanels() {
        emojiPanel?.visibility = View.GONE
        clipboardPanel?.visibility = View.GONE
        aiPanel?.close()
    }

    private fun togglePanel(panel: View?) {
        if (panel == null) return
        val showing = panel.visibility == View.VISIBLE
        closePanels()
        panel.visibility = if (showing) View.GONE else View.VISIBLE
    }

    private fun openSettings() {
        val intent = Intent(this, com.infikey.settings.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requestHideSelf(0)
    }

    // ---- suggestions ----

    private fun updateSuggestions() {
        val bar = suggestionBar ?: return
        val word = inputLogic.composingText()
        val list = Global.suggestions.suggest(word, inputLogic.previousWord(), 3)
        bar.showSuggestions(list)
        contextChips = buildContextChips(word)
        bar.showContextChip(contextChips.firstOrNull()?.first)
    }

    private var contextChips: List<Pair<String, String?>> = emptyList()

    override fun onContextChip(label: String) {
        val chip = contextChips.firstOrNull { it.first == label } ?: return
        when {
            label.startsWith("↺") -> {
                val chunk = inputLogic.popUndo()
                if (chunk != null) {
                    inputLogic.commitString(chunk)
                    android.widget.Toast.makeText(this, "Restored: $chunk", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            label.startsWith("⇄") -> {
                val id = chip.second
                if (id != null && Layouts.ALL.any { it.id == id } && prefs.layoutEnabled(id)) {
                    applyLayout(id)
                    android.widget.Toast.makeText(this, Layouts.byId(id).name, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            else -> chip.second?.let { inputLogic.commitString(it) }
        }
        updateSuggestions()
    }

    override fun onShape(shape: String) {
        when (shape) {
            "caret" -> if (prefs.feature("ai_chat")) { closePanels(); aiPanel?.open() }
            "circle" -> {
                val ic = currentInputConnection ?: return
                val all = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString() ?: return
                ic.setSelection(0, all.length)
            }
        }
    }

    /** Smart chip: calculator, lakh grouping, Indic digits, case cycle,
     *  emoji-from-text, Field Genius autofill, undo, dates, script switch. */
    private fun buildContextChips(word: String): List<Pair<String, String?>> {
        val chips = mutableListOf<Pair<String, String?>>()
        val protected = inputLogic.incognito
        if (!protected) {
            if (word.length >= 3) SmartText.calc(word)?.let { chips.add("= $it" to it) }
            // Indic number formatting (#39): lakh/crore grouping + native numerals
            if (prefs.feature("indic_digits")) {
                if (word.length in 4..12 && word.matches(Regex("\\d+"))) {
                    SmartText.lakh(word).let { chips.add("₹ $it" to it) }
                }
                SmartText.indicDigits(word, prefs.activeLayoutId)?.let { chips.add("$word → $it" to it) }
            }
            if (prefs.feature("text_to_emoji")) {
                SmartText.emojiFor(word)?.let { chips.add("$it emoji" to it) }
            }
            if (word.length >= 2 && word.any { it.isLetter() }) {
                SmartText.caseCycle(word).let { chips.add("Aa $it" to it) }
            }
            // Field Genius chips (#49) + form auto-fill (#48): saved facts for this field type
            if (prefs.feature("field_genius") && prefs.feature("form_fill") &&
                fieldInfo?.kind?.privacy == FieldGenius.Privacy.SUGGEST
            ) {
                val key = when (fieldInfo?.kind) {
                    FieldGenius.Kind.EMAIL -> "email"
                    FieldGenius.Kind.NAME -> "name"
                    else -> ""
                }
                if (key.isNotEmpty()) prefs.facts()[key]?.takeIf { it.isNotBlank() }?.let {
                    chips.add("⚡ $it" to it)
                }
            }
        }
        if (prefs.feature("undo_stack") && inputLogic.undoAvailable() && word.isEmpty()) {
            chips.add("↺ undo" to null)
        }
        if (word.isEmpty() && !protected) {
            if (prefs.feature("date_chips")) {
                SmartText.dateChips().take(1).forEach { (l, v) -> chips.add(l to "$v ") }
            }
            if (prefs.feature("greetings_pack")) {
                SmartText.greetings().take(2).forEach { (l, v) -> chips.add(l to v) }
            }
            val last = inputLogic.lastScriptHint
            if (last != null && last != prefs.activeLayoutId &&
                Layouts.ALL.any { it.id == last } && prefs.layoutEnabled(last) &&
                !prefs.feature("translit", false)
            ) chips.add("⇄ ${Layouts.byId(last).name}?" to last)
        }
        return chips
    }

    override fun onSuggestion(word: String) {
        inputLogic.acceptSuggestion(word)
        Global.suggestions.learn(word)
        updateSuggestions()
    }

    // ---- panels callbacks ----

    override fun onAiTap() = onKey("ai", KeyAction.AI)

    override fun onClipboardTap() {
        clipboardPanel?.rebuild()
        togglePanel(clipboardPanel)
    }

    override fun onSettingsTap() = openSettings()

    override fun onEmoji(text: String) {
        prefs.addEmojiRecent(text)
        inputLogic.commitString(text)
    }

    override fun onPaste(text: String) {
        inputLogic.commitString(text)
        togglePanel(clipboardPanel)
    }

    override fun onClean(text: String) {
        inputLogic.commitString(SmartText.cleanText(text))
        togglePanel(clipboardPanel)
    }

    override fun onToScript(text: String, lang: String) {
        inputLogic.commitString(com.infikey.layout.Transliteration.translate(text, lang))
        togglePanel(clipboardPanel)
    }

    override fun onAskAi(text: String) {
        togglePanel(clipboardPanel)
        aiPanel?.runExternalTask(com.infikey.ai.AiPrompts.tldr(text))
    }

    // ---- AI panel callbacks ----

    override fun commitText(text: String) {
        inputLogic.commitString(text)
        if (!inputLogic.incognito && prefs.feature("clipboard_history")) Global.clipboard.observeText(text)
    }

    override fun appPackage(): String =
        currentInputEditorInfo?.packageName?.toString().orEmpty()

    override fun fieldContext(): Pair<String, String> {
        if (inputLogic.incognito) return "Protected field — AI off" to ""
        val ic = currentInputConnection ?: return (fieldInfo?.hint ?: "") to ""
        val before = ic.getTextBeforeCursor(600, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(200, 0)?.toString() ?: ""
        val selected = ic.getSelectedText(0)?.toString() ?: ""
        val text = selected.ifBlank { (before + after).trim() }
        return (fieldInfo?.hint ?: "") to text
    }

    override fun onPanelClosed() {
        // keyboard focus returns to typing
    }

    // ---- voice typing ----

    private fun startVoice() {
        if (!prefs.feature("voice")) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.widget.Toast.makeText(this, "No speech service installed", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        inputLogic.commitString(text)
                        updateSuggestions()
                    }
                }

                override fun onError(error: Int) {
                    android.widget.Toast.makeText(this@InfiKeyService, "Voice error $error", android.widget.Toast.LENGTH_SHORT).show()
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            })
        }
    }

    override fun onDestroy() {
        speech?.destroy()
        aiPanel?.destroy() // stop streaming coroutines + TTS when the IME dies
        getSystemService(android.content.ClipboardManager::class.java)
            ?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (keyboardView != null) {
            keyboardView?.refreshTypeface()
            keyboardView?.setOneHanded(prefs.oneHanded)
        }
    }
}
