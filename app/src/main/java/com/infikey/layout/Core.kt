package com.infikey.layout

enum class KeyAction {
    CHAR, SHIFT, CAPS, BACKSPACE, ENTER, SPACE,
    SYMBOLS, ALPHABET, NUMBER,
    GLOBE, EMOJI, CLIPBOARD, AI, SETTINGS, VOICE,
    UNDO, REDO, NONE
}

data class Key(
    val label: String,
    val output: String = label,
    val action: KeyAction = KeyAction.CHAR,
    val width: Float = 1f,
    val popups: List<String> = emptyList()
) {
    val isSpecial: Boolean get() = action != KeyAction.CHAR
}

data class KeyboardRow(val keys: List<Key>)

data class Layout(
    val id: String,
    val name: String,
    val script: String,
    val rtl: Boolean = false,
    val hasCase: Boolean = true,
    val main: List<KeyboardRow>,
    val shifted: List<KeyboardRow>? = null,
    val symbols: List<KeyboardRow>? = null
)

fun row(vararg keys: Key) = KeyboardRow(keys.toList())

fun key(label: String, popups: List<String> = emptyList(), width: Float = 1f) =
    Key(label = label, output = label, popups = popups, width = width)

object Layouts {
    const val LATIN = "latin_qwerty"
    const val DEVANAGARI = "devanagari"
    const val BENGALI = "bengali"
    const val TAMIL = "tamil"
    const val TELUGU = "telugu"
    const val KANNADA = "kannada"
    const val MALAYALAM = "malayalam"
    const val GUJARATI = "gujarati"
    const val GURMUKHI = "gurmukhi"
    const val ODIA = "odia"
    const val URDU = "urdu"
    const val OL_CHIKI = "ol_chiki"
    const val DEV = "dev"
    const val MARKDOWN = "markdown"
    const val NUMPAD = "numpad"

    val ALL: List<Layout> by lazy {
        listOf(
            LatinLayout.LAYOUT, NorthIndicLayouts.DEVANAGARI, EastIndicLayouts.BENGALI,
            SouthIndicLayouts.TAMIL, SouthIndicLayouts.TELUGU, SouthIndicLayouts.KANNADA,
            SouthIndicLayouts.MALAYALAM, NorthIndicLayouts.GUJARATI, NorthIndicLayouts.GURMUKHI,
            NorthIndicLayouts.ODIA, UrduLayout.LAYOUT, EastIndicLayouts.OL_CHIKI,
            ExtraLayouts.DEV, ExtraLayouts.MARKDOWN, ExtraLayouts.NUMPAD
        )
    }

    fun byId(id: String): Layout = ALL.firstOrNull { it.id == id } ?: LatinLayout.LAYOUT
}
