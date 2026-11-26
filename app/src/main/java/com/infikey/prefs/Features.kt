package com.infikey.prefs

/** The full InfiKey feature registry — 85 features across 6 categories. */
object Features {

    enum class Category(val label: String) {
        TYPING("Typing"),
        LANGUAGES("Languages & Scripts"),
        AI("AI"),
        LOOK("Look & Feel"),
        CLIPBOARD("Clipboard & History"),
        SMART("Smart & Fun")
    }

    data class FeatureDef(
        val id: String,
        val name: String,
        val desc: String,
        val category: Category,
        val default: Boolean = true
    )

    val ALL: List<FeatureDef> = listOf(
        // ---- Typing (1–24) ----
        FeatureDef("glide", "Glide typing", "Slide finger across letters to type words", Category.TYPING),
        FeatureDef("suggestions", "Next-word suggestions", "3 predictions above the keyboard", Category.TYPING),
        FeatureDef("autocorrect", "Auto-correct", "Fix mistyped words automatically", Category.TYPING),
        FeatureDef("caps", "Auto-capitalisation", "Capitalize sentences and proper nouns", Category.TYPING),
        FeatureDef("number_row", "Number row", "Show digits above letters", Category.TYPING, default = false),
        FeatureDef("long_press_popups", "Long-press popups", "Accents, digits and symbols on long-press", Category.TYPING),
        FeatureDef("haptics_f", "Haptic feedback", "Vibrate on key press", Category.TYPING),
        FeatureDef("key_sound", "Key sounds", "Play a sound on key press", Category.TYPING, default = false),
        FeatureDef("one_handed", "One-handed mode", "Shift keyboard left or right", Category.TYPING, default = false),
        FeatureDef("key_height", "Adjustable key height", "Resize keys for any thumb", Category.TYPING),
        FeatureDef("undo_stack", "Undo / redo", "Shake-free undo of recent edits", Category.TYPING),
        FeatureDef("cursor_drag", "Cursor drag on spacebar", "Move the caret by sliding on space", Category.TYPING),
        FeatureDef("trackpad", "Spacebar trackpad mode", "Turn spacebar into a full cursor pad", Category.TYPING, default = false),
        FeatureDef("selection_mode", "Selection mode", "Select text with arrow keys", Category.TYPING, default = false),
        FeatureDef("numpad", "Dedicated number pad", "Full keypad for number fields", Category.TYPING),
        FeatureDef("dev_layout", "Dev layout", "Programming symbols under your thumbs", Category.TYPING, default = false),
        FeatureDef("markdown_layout", "Markdown layout", "** ## ` [] shortcuts row", Category.TYPING, default = false),
        FeatureDef("delete_word", "Delete word", "Backspace removes whole words on swipe", Category.TYPING),
        FeatureDef("cursor_cluster", "Safe grapheme delete", "Never splits emoji or Indic clusters", Category.TYPING),
        FeatureDef("double_space", "Double-space period", "Tap space twice for a full stop", Category.TYPING),
        FeatureDef("voice", "Voice typing", "System speech-to-text", Category.TYPING),
        FeatureDef("caps_lock", "Double-tap caps lock", "Lock caps with two shift taps", Category.TYPING),
        FeatureDef("swipe_delete", "Swipe-to-delete", "Slide left on backspace to wipe fast", Category.TYPING, default = false),
        FeatureDef("swipe_space_lang", "Swipe spacebar → switch language", "Slide left/right to change script", Category.TYPING),

        // ---- Languages & scripts (25–40) ----
        FeatureDef("latin", "Latin (English + more)", "QWERTY with accents", Category.LANGUAGES),
        FeatureDef("devanagari", "Devanagari", "Hindi, Marathi, Nepali, Sanskrit", Category.LANGUAGES),
        FeatureDef("bengali", "Bengali", "Bangla, Assamese, Manipuri", Category.LANGUAGES),
        FeatureDef("tamil", "Tamil", "Full vowel/consonant grid", Category.LANGUAGES),
        FeatureDef("telugu", "Telugu", "Full vowel/consonant grid", Category.LANGUAGES),
        FeatureDef("kannada", "Kannada", "Full vowel/consonant grid", Category.LANGUAGES),
        FeatureDef("malayalam", "Malayalam", "Full vowel/consonant grid", Category.LANGUAGES),
        FeatureDef("gujarati", "Gujarati", "Full vowel/consonant grid", Category.LANGUAGES),
        FeatureDef("gurmukhi", "Gurmukhi", "Punjabi", Category.LANGUAGES),
        FeatureDef("odia", "Odia", "Odia script grid", Category.LANGUAGES),
        FeatureDef("urdu", "Urdu (RTL)", "Right-to-left Perso-Arabic", Category.LANGUAGES),
        FeatureDef("ol_chiki", "Ol Chiki", "Santali script", Category.LANGUAGES),
        FeatureDef("translit", "Live transliteration", "Type 'namaste' → नमस्ते in 8 languages", Category.LANGUAGES, default = false),
        FeatureDef("per_app_lang", "Per-app language memory", "Remembers which language you use in each app", Category.LANGUAGES),
        FeatureDef("indic_digits", "Indic number formatting", "1,00,000-style grouping (en-IN)", Category.LANGUAGES),
        FeatureDef("indic_fonts", "Indic font pack", "Noto fonts for every supported script", Category.LANGUAGES),

        // ---- AI (41–60) ----
        FeatureDef("ai_type", "AI typing into any field", "AI writes directly where paste is blocked", Category.AI),
        FeatureDef("ai_chat", "AI chat panel", "Ask, instruct, iterate without leaving the field", Category.AI),
        FeatureDef("rewrite", "Style rewrite bar", "14 tones for the text you're about to send", Category.AI),
        FeatureDef("ai_fix", "Fix grammar & spelling", "One tap polish", Category.AI),
        FeatureDef("ai_continue", "Continue writing", "AI takes over from your draft", Category.AI),
        FeatureDef("reply_chips", "Smart reply chips", "3 instant replies to any incoming message", Category.AI),
        FeatureDef("chat_tldr", "Chat TL;DR", "Summarize long chats in 3 lines", Category.AI),
        FeatureDef("form_fill", "Form auto-fill", "AI fills fields from your facts", Category.AI, default = false),
        FeatureDef("field_genius", "Field Genius chips", "Context chips per field type (email, URL, OTP…)", Category.AI),
        FeatureDef("festival_gen", "Festival & greeting generator", "Diwali, Eid, Pongal, Christmas… personalised", Category.AI),
        FeatureDef("style_dna", "Style DNA", "3 sliders that lock your voice into every output", Category.AI),
        FeatureDef("read_aloud", "Read aloud before send", "Hear the draft before you commit it", Category.AI, default = false),
        FeatureDef("multi_model_router", "Multi-model routing", "Fast model for fixes, deep model for essays", Category.AI),
        FeatureDef("any_gguf", "Any-GGUF import", "Import any GGUF: storage, folder, or URL", Category.AI),
        FeatureDef("cloud_any", "Any-site cloud API", "Any OpenAI-compatible URL — any provider", Category.AI),
        FeatureDef("per_app_tone", "Per-app AI tone", "Formal at work, casual in chats", Category.AI, default = false),
        FeatureDef("ai_log", "AI transparency log", "See exactly what was sent to AI, when", Category.AI),
        FeatureDef("safe_apps", "Safe-app list", "AI never reads fields in chosen apps", Category.AI),
        FeatureDef("offline_mode", "Fully offline mode", "Airplane-proof on-device generation", Category.AI, default = false),
        FeatureDef("ai_translate", "AI translate", "Quick translate chip in the panel", Category.AI, default = false),

        // ---- Look & feel (61–72) ----
        FeatureDef("themes", "9 built-in aurora themes", "Obsidian Aurora, Midnight Mint, Rose Noir…", Category.LOOK),
        FeatureDef("photo_keyboard", "Photo keyboard", "Any image as keyboard background", Category.LOOK),
        FeatureDef("blur_dim", "Blur & dim sliders", "Tune photo backdrop live", Category.LOOK),
        FeatureDef("theme_codes", "Theme share codes", "Export/import themes as a short code", Category.LOOK),
        FeatureDef("theme_editor", "Custom theme editor", "Your colors, radius and glow", Category.LOOK),
        FeatureDef("fonts", "Font system", "8 bundled families + your own", Category.LOOK),
        FeatureDef("google_fonts", "Google Fonts catalog", "Download OFL fonts in-app", Category.LOOK),
        FeatureDef("font_import", "Import any .ttf/.otf", "Fonts from any website, stored locally", Category.LOOK),
        FeatureDef("unicode_styles", "Unicode text styles", "𝐁𝐨𝐥𝐝, 𝓢𝓬𝓻𝓲𝓹𝓽, ᴍᴏɴᴏ… works in any app", Category.LOOK),
        FeatureDef("elder_mode", "Elder mode", "Bigger keys, larger text, high contrast", Category.LOOK, default = false),
        FeatureDef("neumorphic", "Neumorphic key rendering", "Skeuomorphic depth + press glow", Category.LOOK),
        FeatureDef("idle_egg", "Idle glow easter egg", "Keys breathe light when you pause", Category.LOOK, default = true),

        // ---- Clipboard & history (73–78) ----
        FeatureDef("clipboard_history", "Clipboard history", "Last 20 snippets, searchable", Category.CLIPBOARD),
        FeatureDef("clipboard_pin", "Pin clipboard items", "Keep the essentials on top", Category.CLIPBOARD),
        FeatureDef("clipboard_plain", "Clipboard → clean text", "Strip weird formatting & zero-width chars", Category.CLIPBOARD),
        FeatureDef("clipboard_translate", "Translate clipboard", "One-tap translate of copied text", Category.CLIPBOARD, default = false),
        FeatureDef("clipboard_edit", "Edit before paste", "Tweak snippets inline", Category.CLIPBOARD),
        FeatureDef("clipboard_ai", "Ask AI about clipboard", "Summarize/extract from copied text", Category.CLIPBOARD),

        // ---- Smart & fun (79–85) ----
        FeatureDef("auto_emoji", "Auto-emoji", "Adds a fitting emoji after sentences", Category.SMART, default = false),
        FeatureDef("text_to_emoji", "Text → emoji", "Convert words into emoji strings", Category.SMART),
        FeatureDef("macros", "Text macros", ";email → your address instantly", Category.SMART),
        FeatureDef("shape_gestures", "Shape gestures", "Circle = undo, V = paste, Z = redo", Category.SMART),
        FeatureDef("greetings_pack", "Indian greetings pack", "Namaste, Adaab, Vanakkam quick chips", Category.SMART, default = false),
        FeatureDef("date_chips", "Date & time chips", "Insert today/tomorrow/timing formats", Category.SMART, default = false),
        FeatureDef("accessibility", "Accessibility", "High contrast, large text, TalkBack labels", Category.SMART)
    )

    val byCategory: Map<Category, List<FeatureDef>> = ALL.groupBy { it.category }
    fun byId(id: String) = ALL.firstOrNull { it.id == id }
}
