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
        installCrashLogger()
    }

    /**
     * Writes uncaught crash stack traces to files/crash_log.txt so a user
     * without adb can still share the reason a build misbehaved (app-crash
     * reports). The previous handler always runs afterwards.
     */
    private fun installCrashLogger() {
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ENGLISH)
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = java.io.File(getExternalFilesDir(null) ?: filesDir, "crash_log.txt")
                f.appendText(
                    "\n==== " + fmt.format(java.util.Date()) + " thread=" + t.name + " ====\n" +
                        android.util.Log.getStackTraceString(e)
                )
                // keep the file small: last ~4 crashes are enough
                if (f.length() > 256 * 1024) f.writeText(f.readText().takeLast(128 * 1024))
            }
            systemHandler?.uncaughtException(t, e)
        }
    }
}
