package com.infikey.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var root: LinearLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var aiPanel: AiPanel
    private lateinit var emojiPanel: EmojiPanel
    private lateinit var clipboardPanel: ClipboardPanel
    private lateinit var inputLogic: InputLogic

    private val glide = GlideTracker()
    private var mode = MODE_LETTERS
    private var shiftOn = false
    private var capsLocked = false
    private var lastShiftTap = 0L
    private var lastSpaceTap = 0L
    private var fieldInfo: FieldGenius.FieldInfo? = null
    private var speech: SpeechRecognizer? = null

    // ---- lifecycle ----

    override fun onCreate() {
        super.onCreate()
        inputLogic = InputLogic(Global.prefs, Global.suggestions)
    }

    override fun onCreateInputView(): View {
        Global.themes // ensure singletons are alive after process death
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        suggestionBar = SuggestionBar(this).apply { callbacks = this@InfiKeyService }
        keyboardView = KeyboardView(this).apply {
            callbacks = this@InfiKeyService
        }
        aiPanel = AiPanel(this).apply { callbacks = this@InfiKeyService }
        emojiPanel = EmojiPanel(this).apply { callbacks = this@InfiKeyService }
        clipboardPanel = ClipboardPanel(this).apply { callbacks = this@InfiKeyService }

        root.addView(suggestionBar)
        root.addView(keyboardView)
        root.addView(aiPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(emojiPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(clipboardPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        applyLayout(prefs.activeLayoutId)
        keyboardView.refreshTypeface()
        return root
    }

    private val prefs get() = Global.prefs

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputLogic.attach(currentInputConnection, keepComposing = restarting)
        val pkg = attribute?.packageName?.toString() ?: ""
        fieldInfo = FieldGenius.analyse(
            attribute, pkg,
            FieldGenius.labelFromExtras(attribute?.extras)
        )
        // per-app memory: restore language automatically
        if (prefs.perAppAuto && pkg.isNotEmpty()) {
            Global.perApp.get(pkg)?.let { mem ->
                if (mem.layoutId != prefs.activeLayoutId) {
                    prefs.activeLayoutId = mem.layoutId
                    Global.suggestions.loadForLayout(mem.layoutId)
                    if (::keyboardView.isInitialized) applyLayout(mem.layoutId)
                }
            }
        }
        Global.suggestions.loadForLayout(prefs.activeLayoutId)
        updateSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        inputLogic.attach(null)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---- layout handling ----

    private fun applyLayout(id: String) {
        mode = MODE_LETTERS
        val layout = Layouts.byId(id)
        keyboardView.setLayout(layout)
        keyboardView.setCapsState(shiftOn, capsLocked)
        keyboardView.setOneHanded(prefs.oneHanded)
        prefs.activeLayoutId = id
        Global.suggestions.loadForLayout(id)
    }

    private fun currentBaseLayout(): com.infikey.layout.Layout = Layouts.byId(prefs.activeLayoutId)

    override fun onGlideStart(x: Float, y: Float) {
        if (!prefs.feature("glide")) return
        glide.start(x, y, System.currentTimeMillis())
    }

    override fun onGlideMove(x: Float, y: Float) {
        glide.move(x, y, System.currentTimeMillis()) { gx, gy -> keyboardView.letterUnder(gx, gy) }
    }

    override fun onGlideEnd(cancelled: Boolean) {
        if (cancelled) return
        val letters = glide.finish() ?: return
        val scored = Global.suggestions.scoreGlide(letters, 3)
        val best = scored.firstOrNull()
        if (best != null) {
            inputLogic.acceptSuggestion(best)
            suggestionBar.showSuggestions(scored)
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
                inputLogic.commitCurrentWord("\n")
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
            KeyAction.CLIPBOARD -> { clipboardPanel.rebuild(); togglePanel(clipboardPanel) }
            KeyAction.AI -> if (aiPanel.isOpen()) aiPanel.close() else { closePanels(); aiPanel.open() }
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
                inputLogic.appendLetter(output)
                updateSuggestions()
            }
        }
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 350) {
            capsLocked = !capsLocked
            shiftOn = capsLocked
        } else {
            shiftOn = !shiftOn
            if (capsLocked && !shiftOn) capsLocked = false
        }
        lastShiftTap = now
        keyboardView.setCapsState(shiftOn, capsLocked)
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
        if (inputLogic.tryMacro()) return
        inputLogic.commitCurrentWord(" ")
        updateSuggestions()
    }

    private fun showSymbolsPage(page: List<com.infikey.layout.KeyboardRow>) {
        keyboardView.setLayout(
            com.infikey.layout.Layout(
                id = "_symbols_$mode", name = "Symbols", script = "Symbols",
                hasCase = false, main = page
            )
        )
    }

    private fun cycleLayout() {
        val enabled = prefs.enabledLayouts().filter { Layouts.ALL.any { l -> l.id == it } }
            .ifEmpty { listOf(prefs.activeLayoutId) }
        val idx = enabled.indexOf(prefs.activeLayoutId)
        val next = enabled[(idx + 1).mod(enabled.size)]
        applyLayout(next)
        android.widget.Toast.makeText(
            this, Layouts.byId(next).name, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private var cursorPos = 0

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
        ic.setSelection(target, target)
        cursorPos = target
    }

    private fun closePanels() {
        emojiPanel.visibility = View.GONE
        clipboardPanel.visibility = View.GONE
        aiPanel.close()
    }

    private fun togglePanel(panel: View) {
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
        val word = inputLogic.composingText()
        val list = Global.suggestions.suggest(word, inputLogic.previousWord(), 3)
        suggestionBar.showSuggestions(list)
    }

    override fun onSuggestion(word: String) {
        inputLogic.acceptSuggestion(word)
        Global.suggestions.learn(word)
        updateSuggestions()
    }

    // ---- panels callbacks ----

    override fun onAiTap() = onKey("ai", KeyAction.AI)

    override fun onClipboardTap() {
        clipboardPanel.rebuild()
        togglePanel(clipboardPanel)
    }

    override fun onSettingsTap() = openSettings()

    override fun onEmoji(text: String) {
        inputLogic.commitString(text)
    }

    override fun onPaste(text: String) {
        inputLogic.commitString(text)
        togglePanel(clipboardPanel)
    }

    // ---- AI panel callbacks ----

    override fun commitText(text: String) {
        inputLogic.commitString(text)
        Global.clipboard.observeText(text)
    }

    override fun fieldContext(): Pair<String, String> {
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
        super.onDestroy()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (::keyboardView.isInitialized) {
            keyboardView.refreshTypeface()
            keyboardView.setOneHanded(prefs.oneHanded)
        }
    }
}
