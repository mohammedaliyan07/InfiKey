package com.infikey.ime

/**
 * Feature 69 — Unicode text styles. Transforms plain text into Mathematical
 * Alphanumeric Symbols (bold / italic / script / fraktur / double-struck /
 * mono / circled / fullwidth), small caps, super/subscripts or combining
 * strike. Works in any app because it is real Unicode, not formatting.
 * Straight-offset ranges cover most letters; legacy exception glyphs
 * (double-struck C/H/N/P/Q/R/Z/h) use their dedicated codepoints.
 */
object UnicodeStyles {

    val NAMES = listOf(
        "Normal", "Bold", "Italic", "Bold Italic", "Script", "Bold Script",
        "Fraktur", "Double-struck", "Mono", "Circled", "Fullwidth",
        "Small Caps", "Superscript", "Subscript", "Strikethrough"
    )

    val COUNT = NAMES.size

    fun name(index: Int): String = NAMES[index % COUNT]

    fun transform(text: String, index: Int): String = when (val s = index % COUNT) {
        0 -> text
        11 -> text.map { smallCaps(it) }.joinToString("")
        12 -> text.map { SUPER[it] ?: "$it" }.joinToString("")
        13 -> text.map { SUB[it] ?: "$it" }.joinToString("")
        14 -> buildString { for (c in text) { append(c); append('\u0336') } }
        else -> {
            val (upper, lower, digits) = rangesFor(s)
            buildString {
                for (c in text) {
                    val cp = when {
                        s == 7 && DSTRUCK_EXC.containsKey(c) -> DSTRUCK_EXC[c]!!
                        s == 9 && c == '0' -> 0x24EA
                        s == 9 && c in '1'..'9' -> 0x2460 + (c - '1')
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
        7 -> Triple(0x1D538, 0x1D552, 0x1D7D8)          // double-struck
        8 -> Triple(0x1D670, 0x1D68A, 0x1D7F6)          // mono
        9 -> Triple(0x24B6, 0x24D0, null)               // circled (digits above)
        10 -> Triple(0xFF21, 0xFF41, 0xFF10)            // fullwidth
        else -> Triple(null, null, null)
    }

    // Double-struck has legacy exceptions outside the 1D5xx block.
    private val DSTRUCK_EXC = mapOf(
        'C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119,
        'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124, 'h' to 0x210E
    )

    private val SUPER = mapOf(
        'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ", 'f' to "ᶠ",
        'g' to "ᵍ", 'h' to "ʰ", 'i' to "ⁱ", 'j' to "ʲ", 'k' to "ᵏ", 'l' to "ˡ",
        'm' to "ᵐ", 'n' to "ⁿ", 'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ",
        't' to "ᵗ", 'u' to "ᵘ", 'v' to "ᵛ", 'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ", 'z' to "ᶻ",
        'A' to "ᴬ", 'B' to "ᴮ", 'D' to "ᴰ", 'E' to "ᴱ", 'G' to "ᴳ", 'H' to "ᴴ",
        'I' to "ᴵ", 'J' to "ᴶ", 'K' to "ᴷ", 'L' to "ᴸ", 'M' to "ᴹ", 'N' to "ᴺ",
        'O' to "ᴼ", 'P' to "ᴾ", 'R' to "ᴿ", 'T' to "ᵀ", 'U' to "ᵁ", 'V' to "ⱽ", 'W' to "ᵂ",
        '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴", '5' to "⁵",
        '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
        '+' to "⁺", '-' to "⁻", '=' to "⁼", '(' to "⁽", ')' to "⁾"
    )

    private val SUB = mapOf(
        'a' to "ₐ", 'e' to "ₑ", 'h' to "ₕ", 'i' to "ᵢ", 'j' to "ⱼ", 'k' to "ₖ",
        'l' to "ₗ", 'm' to "ₘ", 'n' to "ₙ", 'o' to "ₒ", 'p' to "ₚ", 'r' to "ᵣ",
        's' to "ₛ", 't' to "ₜ", 'u' to "ᵤ", 'v' to "ᵥ", 'x' to "ₓ",
        '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄", '5' to "₅",
        '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉",
        '+' to "₊", '-' to "₋", '=' to "₌"
    )

    private val SMALL_CAPS = mapOf(
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ', 'e' to 'ᴇ', 'f' to 'ꜰ',
        'g' to 'ɢ', 'h' to 'ʜ', 'i' to 'ɪ', 'j' to 'ᴊ', 'k' to 'ᴋ', 'l' to 'ʟ',
        'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ', 'p' to 'ᴘ', 'q' to 'ǫ', 'r' to 'ʀ',
        's' to 'ꜱ', 't' to 'ᴛ', 'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'y' to 'ʏ',
        'z' to 'ᴢ'
    )

    private fun smallCaps(c: Char): Char =
        if (c in 'A'..'Z') SMALL_CAPS[c.lowercaseChar()] ?: c else SMALL_CAPS[c] ?: c
}
