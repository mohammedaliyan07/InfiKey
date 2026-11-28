package com.infikey.ime

/** Small pure-Kotlin text rules (no Android types, so they can be tested on a JVM). */
object TextRules {

    data class Word(val lead: String, val core: String, val trail: String)

    private const val LEAD = "\"'([{\u201C\u2018"
    private const val TRAIL = ".,!?;:\"')]}\u2026\u0964\u0965\u201D\u2019\u061F\u060C\u06D4"

    /** Separates surrounding punctuation so it survives autocorrect. */
    fun splitWord(raw: String): Word {
        var start = 0
        while (start < raw.length && raw[start] in LEAD) start++
        var end = raw.length
        while (end > start && raw[end - 1] in TRAIL) end--
        return Word(raw.substring(0, start), raw.substring(start, end), raw.substring(end))
    }

    /** Only plain words are autocorrected: no digits, addresses, links or macros. */
    fun isCorrectable(core: String): Boolean =
        core.any { it.isLetter() } && core.all { it.isLetter() || it == '\'' || isMark(it) }

    private fun isMark(c: Char): Boolean {
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
            c == '\u200C' || c == '\u200D'
    }

    fun matchCase(original: String, fixed: String): String =
        if (original.firstOrNull()?.isUpperCase() == true) fixed.replaceFirstChar { it.uppercaseChar() }
        else fixed

    /** True when a second space should turn "word " into "word. ". */
    fun doubleSpaceApplies(before: String): Boolean {
        if (before.length < 2 || before.last() != ' ') return false
        val c = before[before.length - 2]
        return c.isLetterOrDigit() || isMark(c) || c == ')' || c == '"' || Character.isLowSurrogate(c)
    }

    /** True for loopback and private-network hosts (Ollama, LM Studio and the like). */
    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h == "::1" || h.endsWith(".local")) return true
        val p = h.split(".").map { it.toIntOrNull() ?: return false }
        if (p.size != 4) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
}
