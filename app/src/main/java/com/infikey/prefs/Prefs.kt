package com.infikey.prefs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Central settings store. */
class Prefs(context: Context) {

    val sp: SharedPreferences = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)

    // ---- Look & feel ----
    var themeId: String
        get() = sp.getString("theme_id", "obsidian_aurora") ?: "obsidian_aurora"
        set(v) = sp.edit().putString("theme_id", v).apply()

    var bgImagePath: String
        get() = sp.getString("bg_image_path", "") ?: ""
        set(v) = sp.edit().putString("bg_image_path", v).apply()

    var bgBlur: Float
        get() = sp.getFloat("bg_blur", 8f)
        set(v) = sp.edit().putFloat("bg_blur", v).apply()

    var bgDim: Float
        get() = sp.getFloat("bg_dim", 0.45f)
        set(v) = sp.edit().putFloat("bg_dim", v).apply()

    var keyHeightDp: Int
        get() = sp.getInt("key_height", 52)
        set(v) = sp.edit().putInt("key_height", v).apply()

    var borderRadiusDp: Int
        get() = sp.getInt("border_radius", 16)
        set(v) = sp.edit().putInt("border_radius", v).apply()

    var fontId: String
        get() = sp.getString("font_id", "system_sans") ?: "system_sans"
        set(v) = sp.edit().putString("font_id", v).apply()

    var followSystemDark: Boolean
        get() = sp.getBoolean("follow_system_dark", false)
        set(v) = sp.edit().putBoolean("follow_system_dark", v).apply()

    // ---- Behaviour ----
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    var keySound: Boolean
        get() = sp.getBoolean("key_sound", false)
        set(v) = sp.edit().putBoolean("key_sound", v).apply()

    var autoCaps: Boolean
        get() = sp.getBoolean("auto_caps", true)
        set(v) = sp.edit().putBoolean("auto_caps", v).apply()

    var numberRow: Boolean
        get() = sp.getBoolean("number_row", false)
        set(v) = sp.edit().putBoolean("number_row", v).apply()

    var glideEnabled: Boolean
        get() = sp.getBoolean("glide", true)
        set(v) = sp.edit().putBoolean("glide", v).apply()

    var suggestionsEnabled: Boolean
        get() = sp.getBoolean("suggestions", true)
        set(v) = sp.edit().putBoolean("suggestions", v).apply()

    var autoCorrect: Boolean
        get() = sp.getBoolean("autocorrect", true)
        set(v) = sp.edit().putBoolean("autocorrect", v).apply()

    var oneHanded: String   // off | left | right
        get() = sp.getString("one_handed", "off") ?: "off"
        set(v) = sp.edit().putString("one_handed", v).apply()

    var clipboardHistoryEnabled: Boolean
        get() = sp.getBoolean("clipboard_history", true)
        set(v) = sp.edit().putBoolean("clipboard_history", v).apply()

    var elderMode: Boolean
        get() = sp.getBoolean("elder_mode", false)
        set(v) = sp.edit().putBoolean("elder_mode", v).apply()

    var translitLang: String   // "" = off, else a script layout id such as devanagari
        get() = (sp.getString("translit_lang", "") ?: "").let {
            if (it == "hi") "devanagari" else it // older builds stored the language code
        }
        set(v) = sp.edit().putString("translit_lang", v).apply()

    // ---- Languages ----
    fun enabledLayouts(): MutableSet<String> =
        sp.getStringSet("enabled_layouts", setOf("latin_qwerty"))?.toMutableSet()
            ?: mutableSetOf("latin_qwerty")

    fun setEnabledLayouts(v: Set<String>) = sp.edit().putStringSet("enabled_layouts", v).apply()

    var activeLayoutId: String
        get() = sp.getString("active_layout", "latin_qwerty") ?: "latin_qwerty"
        set(v) = sp.edit().putString("active_layout", v).apply()

    var perAppAuto: Boolean
        get() = sp.getBoolean("per_app_auto", true)
        set(v) = sp.edit().putBoolean("per_app_auto", v).apply()

    // ---- AI ----
    var aiMode: String   // auto | cloud | offline
        get() = sp.getString("ai_mode", "auto") ?: "auto"
        set(v) = sp.edit().putString("ai_mode", v).apply()

    var cloudEndpointsJson: String
        get() = sp.getString("cloud_endpoints", "[]") ?: "[]"
        set(v) = sp.edit().putString("cloud_endpoints", v).apply()

    var activeCloudEndpointId: String
        get() = sp.getString("active_cloud_endpoint", "") ?: ""
        set(v) = sp.edit().putString("active_cloud_endpoint", v).apply()

    var aiLogEnabled: Boolean
        get() = sp.getBoolean("ai_log", true)
        set(v) = sp.edit().putBoolean("ai_log", v).apply()

    var readAloudBeforeSend: Boolean
        get() = sp.getBoolean("read_aloud_send", false)
        set(v) = sp.edit().putBoolean("read_aloud_send", v).apply()

    var styleDnaJson: String
        get() = sp.getString("style_dna", "{}") ?: "{}"
        set(v) = sp.edit().putString("style_dna", v).apply()

    var safeApps: MutableSet<String>
        get() = sp.getStringSet("safe_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        set(v) = sp.edit().putStringSet("safe_apps", v).apply()

    // ---- Fun & smart ----
    var autoEmoji: Boolean
        get() = sp.getBoolean("auto_emoji", false)
        set(v) = sp.edit().putBoolean("auto_emoji", v).apply()

    var idleEasterEgg: Boolean
        get() = sp.getBoolean("idle_egg", true)
        set(v) = sp.edit().putBoolean("idle_egg", v).apply()

    var shapeGestures: Boolean
        get() = sp.getBoolean("shape_gestures", true)
        set(v) = sp.edit().putBoolean("shape_gestures", v).apply()

    var macrosJson: String
        get() = sp.getString("macros", "{}") ?: "{}"
        set(v) = sp.edit().putString("macros", v).apply()

    var savedThemeCodes: MutableSet<String>
        get() = sp.getStringSet("theme_codes", emptySet())?.toMutableSet() ?: mutableSetOf()
        set(v) = sp.edit().putStringSet("theme_codes", v).apply()

    // ---- Feature toggles (all 85) ----
    /**
     * Central resolver: a registry id always maps to the REAL control behind
     * that feature — the typed accessor when one exists (so the dedicated
     * settings screens and the Features tab stay in sync), otherwise its
     * feat_<id> flag. Behavior code calls [feature], never duplicate logic.
     */
    fun feature(id: String, default: Boolean = true): Boolean = when (id) {
        "glide" -> glideEnabled
        "suggestions" -> suggestionsEnabled
        "autocorrect" -> autoCorrect
        "caps" -> autoCaps
        "number_row" -> numberRow
        "haptics_f" -> haptics
        "key_sound" -> keySound
        "one_handed" -> oneHanded != "off"
        "key_height" -> sp.getBoolean("feat_key_height", true)
        "clipboard_history" -> clipboardHistoryEnabled
        "elder_mode" -> elderMode
        "translit" -> translitLang.isNotEmpty()
        "per_app_lang" -> perAppAuto
        "auto_emoji" -> autoEmoji
        "idle_egg" -> idleEasterEgg
        "shape_gestures" -> shapeGestures
        "read_aloud" -> readAloudBeforeSend
        "ai_log" -> aiLogEnabled
        else -> sp.getBoolean("feat_$id", default)
    }

    /** Central writer — the Features tab writes here; typed accessors stay consistent. */
    fun setFeature(id: String, v: Boolean) {
        when (id) {
            "glide" -> glideEnabled = v
            "suggestions" -> suggestionsEnabled = v
            "autocorrect" -> autoCorrect = v
            "caps" -> autoCaps = v
            "number_row" -> numberRow = v
            "haptics_f" -> haptics = v
            "key_sound" -> keySound = v
            "one_handed" -> oneHanded = if (v) "right" else "off"
            "clipboard_history" -> clipboardHistoryEnabled = v
            "elder_mode" -> elderMode = v
            "translit" -> if (v) { if (translitLang.isEmpty()) translitLang = "devanagari" } else translitLang = ""
            "per_app_lang" -> perAppAuto = v
            "auto_emoji" -> autoEmoji = v
            "idle_egg" -> idleEasterEgg = v
            "shape_gestures" -> shapeGestures = v
            "read_aloud" -> readAloudBeforeSend = v
            "ai_log" -> aiLogEnabled = v
            else -> sp.edit().putBoolean("feat_$id", v).apply()
        }
    }

    /** Whether a keyboard layout may currently appear in the switcher (#25–#36, #16, #17). */
    fun layoutEnabled(layoutId: String): Boolean = when (layoutId) {
        "latin_qwerty" -> feature("latin")
        "dev" -> feature("dev_layout")
        "markdown" -> feature("markdown_layout")
        "numpad" -> feature("numpad")
        else -> feature(layoutId)
    }

    // ---- Misc ----
    fun putString(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun getString(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun getBool(k: String, d: Boolean = false): Boolean = sp.getBoolean(k, d)

    fun readStyleDna(): com.infikey.ai.StyleDna = runCatching {
        com.infikey.ai.StyleDna.fromJson(JSONObject(styleDnaJson))
    }.getOrDefault(com.infikey.ai.StyleDna.DEFAULT)

    fun writeStyleDna(d: com.infikey.ai.StyleDna) { styleDnaJson = d.toJson().toString() }

    fun readMacros(): MutableMap<String, String> = runCatching {
        val o = JSONObject(macrosJson)
        o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    fun writeMacros(m: Map<String, String>) {
        macrosJson = JSONObject(m).toString()
    }

    // ---- my facts: form auto-fill + {{}} templates (#17, #47) ----
    var factsJson: String
        get() = sp.getString("facts", "{}") ?: "{}"
        set(v) = sp.edit().putString("facts", v).apply()

    fun facts(): Map<String, String> = runCatching {
        val o = JSONObject(factsJson)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun writeFact(k: String, v: String) {
        val m = facts().toMutableMap()
        m[k.lowercase()] = v
        factsJson = JSONObject(m).toString()
    }

    // ---- look personalisation ----
    var pressAnim: String   // glow | ripple | bounce | retro
        get() = sp.getString("press_anim", "ripple") ?: "ripple"
        set(v) = sp.edit().putString("press_anim", v).apply()

    var soundPack: String   // standard | mechanical | typewriter | pop | silent
        get() = sp.getString("sound_pack", "standard") ?: "standard"
        set(v) = sp.edit().putString("sound_pack", v).apply()

    var biometricLock: Boolean
        get() = sp.getBoolean("biometric_lock", false)
        set(v) = sp.edit().putBoolean("biometric_lock", v).apply()

    // ---- emoji recents ----
    fun emojiRecents(): List<String> =
        sp.getString("emoji_recents", "")?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()

    fun addEmojiRecent(e: String) {
        val cur = emojiRecents().filter { it != e }.toMutableList()
        cur.add(0, e)
        sp.edit().putString("emoji_recents", cur.take(16).joinToString(" ")).apply()
    }

    // ---- AI transparency log (#63): what was sent, when, how big ----
    var aiLogJson: String
        get() = sp.getString("ai_log_entries", "[]") ?: "[]"
        set(v) = sp.edit().putString("ai_log_entries", v).apply()

    fun appendAiLog(kind: String, chars: Int) {
        val arr = runCatching { JSONArray(aiLogJson) }.getOrDefault(JSONArray())
        val out = JSONArray()
        out.put(JSONObject().put("t", System.currentTimeMillis()).put("kind", kind).put("chars", chars))
        for (i in 0 until minOf(arr.length(), 40)) out.put(arr.get(i))
        aiLogJson = out.toString()
    }

    fun learnedWordCount(): Int =
        sp.all.keys.filter { it.startsWith("learned_") }
            .sumOf { (sp.getStringSet(it, emptySet()) ?: emptySet()).size }

    /** One-tap wipe of everything InfiKey learned (#85). */
    fun wipeLearnedWords() {
        val editor = sp.edit()
        sp.all.keys.filter { it.startsWith("learned_") }.forEach { editor.remove(it) }
        editor.remove("per_app_memory").apply()
    }
}
