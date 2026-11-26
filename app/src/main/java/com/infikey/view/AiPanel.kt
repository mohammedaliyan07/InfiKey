package com.infikey.view

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.infikey.Global
import com.infikey.ai.AiPrompts
import com.infikey.ai.AiRouter
import com.infikey.ai.GenOptions
import com.infikey.ai.RewriteStyles
import com.infikey.theme.ThemeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The InfiKey AI overlay: quick actions, tone chips, streaming output and
 * "Type it" commit. Generation runs through [AiRouter] (on-device or cloud).
 */
class AiPanel(context: Context) : LinearLayout(context) {

    interface Callbacks {
        /** Commit generated text into the focused field (bypasses paste). */
        fun commitText(text: String)
        /** Returns (fieldHint, fieldText) for context-aware actions. */
        fun fieldContext(): Pair<String, String>
        fun onPanelClosed()
    }

    var callbacks: Callbacks? = null

    private val theme get() = ThemeEngine(context).current()
    private val prefs = Global.prefs
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val d = resources.displayMetrics.density

    private var activeJob: Job? = null

    private val status = TextView(context)
    private val output = TextView(context)
    private val busy = ProgressBar(context)
    private val actionGrid = LinearLayout(context)
    private val toneRow = LinearLayout(context)
    private val variantRow = LinearLayout(context)
    private val outputScroll = android.widget.ScrollView(context)

    private var selectedTone = "natural"
    private var lastResult = ""

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.bg)
        setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        visibility = GONE

        // header
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status.textSize = 13f
        status.setTextColor(theme.accent)
        status.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        header.addView(status)
        busy.layoutParams = LayoutParams((18 * d).toInt(), (18 * d).toInt())
        busy.visibility = GONE
        header.addView(busy)
        val close = TextView(context).apply {
            text = "✕"; textSize = 15f; setTextColor(theme.hint)
            setPadding((10 * d).toInt(), 0, 0, 0)
            setOnClickListener { close() }
        }
        header.addView(close)
        addView(header)

        // action chips
        actionGrid.orientation = HORIZONTAL
        addView(actionGrid)
        addView(toneRow)
        addView(variantRow)

        // output box
        outputScroll.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (140 * d).toInt())
        output.textSize = 14f
        output.setTextColor(theme.text)
        output.setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
        outputScroll.addView(output)
        addView(outputScroll)

        // footer: Type it / Copy / Continue
        val footer = LinearLayout(context).apply { orientation = HORIZONTAL }
        footer.addView(button("↺ Retry") { retry() }, 1f)
        footer.addView(button("⧉ Copy") {
            copyToClipboard(lastResult); toast("Copied")
        }, 1f)
        footer.addView(button("⌨ Type it", accent = true) {
            if (lastResult.isNotBlank()) callbacks?.commitText(lastResult)
            close()
        }, 1.4f)
        addView(footer)

        refreshStatus()
        showActions()
    }

    fun refreshStatus() {
        val router = Global.router
        status.text = when {
            router.isModelLoaded() ->
                "✦ On-device: ${router.currentModelPath.substringAfterLast('/').substringBefore(".gguf")}"
            Global.prefs.aiMode != "offline" && router.activeCloud() != null ->
                "✦ Cloud: ${router.activeCloud()!!.name} (${router.activeCloud()!!.model})"
            else -> "⚠ No AI loaded — add a model or cloud API in settings"
        }
    }

    // ---- UI helpers ----

    private fun chip(label: String, onTap: () -> Unit, accent: Boolean = false) = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(if (accent) 0xFFFFFFFF.toInt() else theme.text)
        setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
        }
        background = GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(if (accent) theme.accent else theme.key)
        }
        setOnClickListener { onTap() }
    }

    private fun button(label: String, accent: Boolean = false, onTap: () -> Unit) = Button(context).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(if (accent) 0xFFFFFFFF.toInt() else theme.text)
        background = GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(if (accent) theme.accent else theme.keySpecial)
        }
        setOnClickListener { onTap() }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("InfiKey AI", text))
        }
    }

    private fun clearDynamic() {
        actionGrid.removeAllViews()
        toneRow.removeAllViews()
        variantRow.removeAllViews()
        output.text = ""
        lastResult = ""
        outputScroll.visibility = GONE
    }

    private fun setTypefaceAll(tf: Typeface?) {
        status.typeface = tf
        output.typeface = tf
    }

    // ---- flows ----

    private fun showActions() {
        clearDynamic()
        actionGrid.addView(chip("✍ Compose") { showCompose() })
        actionGrid.addView(chip("↻ Rewrite") { runRewrite() })
        actionGrid.addView(chip("✓ Fix") { runTask(AiPrompts.fix(fieldText())) })
        actionGrid.addView(chip("… Continue") { runTask(AiPrompts.continueWriting(fieldText(), fieldHint())) })
        actionGrid.addView(chip("💬 Reply ×3") { runReplyChips() })
        actionGrid.addView(chip("TL;DR") { runTask(AiPrompts.tldr(fieldText())) })
        actionGrid.addView(chip("🌐 Translate") { showTranslate() })
        actionGrid.addView(chip("🎂 Festival") { showFestival() })
        showTones()
    }

    private fun showTones() {
        toneRow.removeAllViews()
        toneRow.addView(TextView(context).apply {
            text = "Tone:"; textSize = 12f; setTextColor(theme.hint)
            setPadding(0, 0, (6 * d).toInt(), 0)
            gravity = Gravity.CENTER
        })
        for (t in listOf("natural", "formal", "casual", "friendly", "genz")) {
            toneRow.addView(chip(t.replaceFirstChar { it.uppercase() }, accent = t == selectedTone) {
                selectedTone = t
                showActions()
            })
        }
    }

    private fun showCompose() {
        clearDynamic()
        actionGrid.addView(TextView(context).apply {
            text = "What should InfiKey write?"
            textSize = 14f; setTextColor(theme.text)
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        })
        val input = android.widget.EditText(context).apply {
            hint = "e.g. WhatsApp reply to boss: taking leave tomorrow"
            textSize = 14f
            maxLines = 3
            setTextColor(theme.text)
            setHintTextColor(theme.hint)
            background = GradientDrawable().apply { cornerRadius = 12 * d; setColor(theme.key) }
            setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
        }
        actionGrid.addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        actionGrid.addView(chip("⚡ Generate", accent = true) {
            val what = input.text.toString().trim()
            if (what.isNotBlank()) runTask(AiPrompts.compose(what, selectedTone, fieldHint(), prefs.readStyleDna()))
        })
        actionGrid.addView(chip("← Back") { showActions() })
    }

    private fun showTranslate() {
        clearDynamic()
        for (lang in listOf("English", "हिंदी Hindi", "اردو Urdu", "தமிழ் Tamil", "తెలుగు Telugu", "বাংলা Bengali",
                "मराठी Marathi", "ಕನ್ನಡ Kannada", "മലയാളം Malayalam", "ગુજરાતી Gujarati", "ਪੰਜਾਬੀ Punjabi", "اَلْعَرَبِيَّة Arabic")) {
            variantRow.addView(chip(lang) {
                val code = when {
                    lang.contains("English") -> "English"
                    lang.contains("Hindi") || lang.contains("Marathi") -> "Hindi"
                    lang.contains("Urdu") -> "Urdu"
                    else -> lang.substringAfter(' ').ifBlank { lang }
                }
                runTask(AiPrompts.translate(fieldText(), code))
            })
        }
        actionGrid.addView(chip("← Back") { showActions() })
    }

    private fun showFestival() {
        clearDynamic()
        for (f in listOf("Diwali", "Eid Mubarak", "Holi", "Christmas", "New Year", "Pongal", "Navratri", "Raksha Bandhan")) {
            variantRow.addView(chip(f) {
                runTask(AiPrompts.festival(f, "", selectedTone, selectedTone))
            })
        }
        actionGrid.addView(chip("← Back") { showActions() })
    }

    private fun runRewrite() {
        val text = fieldText()
        if (text.isBlank()) { toast("Nothing to rewrite"); return }
        val styles = RewriteStyles.ALL.take(4)
        activeJob = scope.launch {
            busy.visibility = VISIBLE
            runCatching {
                Global.router.chat(
                    AiRouter.Tier.DEEP, AiPrompts.rewrite(text, styles.map { it.label }),
                    GenOptions(maxTokens = 500)
                ).getOrThrow()
            }.onSuccess { raw ->
                busy.visibility = GONE
                showVariants(AiPrompts.parseVariants(raw, styles.map { it.label }))
            }.onFailure { busy.visibility = GONE; showError(it) }
        }
    }

    private fun runReplyChips() {
        val context = fieldText()
        activeJob = scope.launch {
            busy.visibility = VISIBLE
            runCatching {
                Global.router.chat(
                    AiRouter.Tier.FAST, AiPrompts.replyChips(context, "the user's language"),
                    GenOptions(maxTokens = 120)
                ).getOrThrow()
            }.onSuccess { raw ->
                busy.visibility = GONE
                showVariants(AiPrompts.parseLines(raw, 3))
            }.onFailure { busy.visibility = GONE; showError(it) }
        }
    }

    private fun runTask(messages: List<com.infikey.ai.ChatMessage>) {
        if (Global.router.engineFor(AiRouter.Tier.FAST) == null &&
            Global.router.engineFor(AiRouter.Tier.DEEP) == null) {
            toast("No AI loaded — open ⚙ → AI model")
            return
        }
        activeJob = scope.launch {
            busy.visibility = VISIBLE
            outputScroll.visibility = VISIBLE
            output.text = ""
            runCatching {
                Global.router.streamChat(messages, selectedTone, fieldHint()) { partial ->
                    output.text = partial
                    outputScroll.post { outputScroll.fullScroll(android.view.View.FOCUS_DOWN) }
                }
            }.onSuccess {
                busy.visibility = GONE
                lastResult = it
            }.onFailure {
                busy.visibility = GONE
                showError(it)
            }
        }
    }

    private fun showVariants(variants: List<String>) {
        variantRow.removeAllViews()
        outputScroll.visibility = GONE
        for (v in variants.take(5)) {
            variantRow.addView(chip(v.take(28)) { callbacks?.commitText(v); close() })
        }
    }

    private fun showError(t: Throwable) {
        outputScroll.visibility = VISIBLE
        output.setTextColor(0xFFEF4444.toInt())
        output.text = "AI failed: ${t.message ?: t.javaClass.simpleName}\nCheck model/cloud settings."
        output.postDelayed({ output.setTextColor(theme.text) }, 4000)
    }

    private fun retry() {
        showActions()
    }

    // ---- context accessors ----

    private fun fieldHint(): String = callbacks?.fieldContext()?.first ?: ""
    private fun fieldText(): String = callbacks?.fieldContext()?.second ?: ""

    fun open() {
        visibility = VISIBLE
        refreshStatus()
        showActions()
    }

    fun close() {
        activeJob?.cancel()
        visibility = GONE
        callbacks?.onPanelClosed()
    }

    fun isOpen(): Boolean = visibility == VISIBLE

    fun destroy() {
        scope.cancel()
    }

    fun setTypeface(tf: Typeface?) = setTypefaceAll(tf)
}
