package com.infikey.ime

/**
 * Feature 69 — Unicode text styles. Transforms plain text into Mathematical
 * Alphanumeric Symbols (bold / italic / script / fraktur / mono / small caps)
 * or combining-strike text. Works in any app because it is real Unicode,
 * not formatting. Straight-offset ranges cover most letters; the handful of
 * legacy exception glyphs (e.g. script e, o) fall back to the plain letter.
 */
object UnicodeStyles {

    val NAMES = listOf(
        "Normal", "Bold", "Italic", "Bold Italic", "Script", "Bold Script",
        "Fraktur", "Mono", "Small Caps", "Strikethrough"
    )

    val COUNT = NAMES.size

    fun name(index: Int): String = NAMES[index % COUNT]

    fun transform(text: String, index: Int): String = when (index % COUNT) {
        0 -> text
        8 -> text.map { smallCaps(it) }.joinToString("")
        9 -> buildString { for (c in text) { append(c); append('\u0336') } }
        else -> {
            val (upper, lower, digits) = rangesFor(index % COUNT)
            buildString {
                for (c in text) {
                    val cp = when {
                        c in 'A'..'Z' && upper != null -> upper + (c - 'A')
                        c in 'a'..'z' && lower != null -> lower + (c - 'a')
                        c in '0'..'9' && digits != null -> digits + (c - '0')
                        else -> c.code
                    }
                    appendCodePoint(cp)
                }
            }
        }
    }

    /** (upperBase, lowerBase, digitBase) codepoint bases per style. */
    private fun rangesFor(style: Int): Triple<Int?, Int?, Int?> = when (style) {
        1 -> Triple(0x1D400, 0x1D41A, 0x1D7CE)          // bold
        2 -> Triple(0x1D434, 0x1D44E, null)             // italic
        3 -> Triple(0x1D468, 0x1D482, null)             // bold italic
        4 -> Triple(0x1D49C, 0x1D4B6, null)             // script
        5 -> Triple(0x1D4D0, 0x1D4EA, null)             // bold script
        6 -> Triple(0x1D504, 0x1D51E, null)             // fraktur
        7 -> Triple(0x1D670, 0x1D68A, 0x1D7F6)          // mono
        else -> Triple(null, null, null)
    }

    private val SMALL_CAPS = mapOf(
        'a' to '\u1D00', 'b' to '\u0299', 'c' to '\u1D04', 'd' to '\u1D05',
        'e' to '\u1D07', 'f' to '\uA730', 'g' to '\u0262', 'h' to '\u029C',
        'i' to '\u026A', 'j' to '\u1D0A', 'k' to '\u1D0B', 'l' to '\u029F',
        'm' to '\u1D0D', 'n' to '\u0274', 'o' to '\u1D0F', 'p' to '\u1D18',
        'q' to '\u1D16', 'r' to '\u0280', 's' to '\uA731', 't' to '\u1D1B',
        'u' to '\u1D1C', 'v' to '\u1D20', 'w' to '\u1D21', 'y' to '\u028F',
        'z' to '\u1D22'
    )

    private fun smallCaps(c: Char): Char =
        if (c in 'A'..'Z') SMALL_CAPS[c.lowercaseChar()] ?: c else SMALL_CAPS[c] ?: c
}
