package com.infikey

import android.app.Application
import com.infikey.ai.AiRouter
import com.infikey.ai.ModelManager
import com.infikey.font.FontManager
import com.infikey.ime.ClipboardHistory
import com.infikey.ime.SuggestionEngine
import com.infikey.prefs.PerAppMemory
import com.infikey.prefs.Prefs
import com.infikey.theme.ThemeEngine

/** Singletons used across the IME and the settings app. */
object Global {
    lateinit var prefs: Prefs
    lateinit var models: ModelManager
    lateinit var router: AiRouter
    lateinit var suggestions: SuggestionEngine
    lateinit var clipboard: ClipboardHistory
    lateinit var fonts: FontManager
    lateinit var themes: ThemeEngine
    lateinit var perApp: PerAppMemory

    fun init(context: Application) {
        if (::prefs.isInitialized) return
        prefs = Prefs(context)
        models = ModelManager(context)
        router = AiRouter(context)
        suggestions = SuggestionEngine(context)
        clipboard = ClipboardHistory(context)
        fonts = FontManager(context)
        themes = ThemeEngine(context)
        perApp = PerAppMemory(context)
    }
}

class InfiKeyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Global.init(this)
        runCatching { Global.models.extractBundledIfAny() }
    }
}
